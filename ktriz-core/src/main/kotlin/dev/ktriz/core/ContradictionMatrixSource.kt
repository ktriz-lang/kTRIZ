package dev.ktriz.core

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.security.MessageDigest

private val logger = KotlinLogging.logger {}

/**
 * The pluggable matrix-source seam kTRIZ-ADR-0003 calls for: resolves which
 * [ContradictionMatrixData] [ContradictionMatrix] should use, and provides a hardened
 * file-based frontend for anyone who wants to hold their own matrix explicitly (a house
 * compilation, a corrected transcription, or their own 39-parameter projection of a
 * licensed matrix) without touching [ContradictionMatrix]'s process-wide default.
 *
 * Ports the *bundled-resource-default, system-property-override* pattern kSTEP's
 * `OcctNativeLibrary` already uses for a native library path, onto a matrix data file, and
 * layers on the local-file-only hardening kTRIZ's own [dev.ktriz.script.KtrizScriptHost]
 * already applies to `.ktriz.kts` script paths (URI-scheme rejection, canonicalisation,
 * regular-file/readability checks, a byte-size cap).
 *
 * ## The three layers
 *
 * 1. **Programmatic** -- [fromFile] loads an explicit file into a standalone
 *    [ContradictionMatrixData], leaving [ContradictionMatrix]'s own default untouched. Use
 *    this from an embedder or a test that wants its own matrix instance.
 * 2. **Discovery** -- the system property [OVERRIDE_PROPERTY], read once by [resolve] (and,
 *    transitively, by [ContradictionMatrix] the first time it is touched). Set it with
 *    `-Dktriz.matrix.file=/abs/path.csv`; the installed CLI's `application`-plugin start
 *    script passes `KTRIZ_OPTS` straight through, so `KTRIZ_OPTS="-Dktriz.matrix.file=..."
 *    ktriz run ...` works with zero code changes.
 * 3. **Ergonomics** -- [Contradiction.resolve] takes an explicit [ContradictionMatrixData],
 *    for callers who already hold one via [fromFile] and want the same fluent call-site
 *    shape as the no-argument [Contradiction.resolve].
 *
 * ## What this deliberately does *not* do
 *
 * - **No remote/URL support, on any layer.** There is no code path here that opens a
 *   network connection. A URI-shaped path (`https://...`, `file://...`, ...) is rejected
 *   with an explanatory message, not silently downloaded -- kTRIZ never fetches matrix data
 *   over the network; see kTRIZ-ADR-0003.
 * - **No silent fallback to the bundled matrix.** If [OVERRIDE_PROPERTY] names a file that
 *   fails to load or fails to parse, [resolve] throws rather than quietly falling back to
 *   the classical matrix. A silent fallback would mean an operator believes they are using
 *   their own matrix while actually getting classical results -- the worst possible failure
 *   mode for a tool whose entire value proposition is determinism.
 * - **No relaxation of the 1..39 parameter-id range**, including for externally supplied
 *   files. [fromFile] delegates to the exact same [ContradictionMatrixData.parse] the
 *   bundled matrix uses -- there is no second, laxer validation path. A 48-parameter
 *   Matrix-2003/2010/2022-shaped file is rejected exactly as it always was, because
 *   [EngineeringParameter] has 39 members: a cell addressing parameter 42 would be
 *   permanently unreachable through the typed lookup API even if the parser accepted it,
 *   which would make "acceptance" a lie. See kTRIZ-ADR-0003.
 *
 * ## Threat model
 *
 * [OVERRIDE_PROPERTY] is a JVM system property: settable only by whoever starts the JVM (a
 * `-D` flag, or programmatically before this class is first touched). It is never derived
 * from network input, a config file parsed from untrusted data, or any per-request value --
 * the same threat model kSTEP's `OcctNativeLibrary` documents for its own override property.
 */
object ContradictionMatrixSource {
    /** Where the currently loaded [ContradictionMatrixData] came from. */
    enum class Origin {
        /** The classical matrix bundled with kTRIZ (`BUNDLED_RESOURCE_NAME`). */
        BUNDLED_CLASSICAL,

        /** A file named via [OVERRIDE_PROPERTY], loaded through [fromFile]'s gate. */
        EXTERNAL_FILE,
    }

    /** The outcome of [resolve]: the parsed data, plus which [Origin] it came from. */
    data class Resolved(
        val data: ContradictionMatrixData,
        val origin: Origin,
    )

    /**
     * System property that, when set to an absolute local file path, replaces
     * [ContradictionMatrix]'s data source process-wide. See the class KDoc for the full
     * mechanism and its deliberate limits.
     */
    const val OVERRIDE_PROPERTY: String = "ktriz.matrix.file"

    /**
     * Maximum size of a matrix file [fromFile] will read, in bytes. The bundled classical
     * matrix is ~39 KiB; 4 MiB leaves roughly a hundredfold of headroom for a legitimately
     * large hand-authored or generated matrix while still bounding worst-case memory use
     * against a misconfigured or hostile override path (a DoS concern, not a trust boundary --
     * see [fromFile]'s gate step 6).
     */
    const val MAX_MATRIX_BYTES: Long = 4L * 1024 * 1024

    /** Classpath resource name of the bundled classical matrix, next to [ContradictionMatrix]. */
    const val BUNDLED_RESOURCE_NAME: String = "contradiction-matrix-1971.csv"

    /** Provenance note attached to the bundled classical matrix. */
    const val BUNDLED_PROVENANCE: String =
        "Classical Altshuller 39x39 matrix, kTRIZ in-house reconciliation of four independent " +
            "transcriptions, 2026-09-02; see docs/matrix-provenance.adoc"

    // Explicit allowlist of URI schemes to reject, checked case-insensitively against
    // whatever precedes the *first* colon in the raw path string -- a deliberate, commented
    // copy of dev.ktriz.script.KtrizScriptHost's REJECTED_URI_SCHEMES/hasRejectedUriScheme,
    // not an extraction into a shared module: ktriz-script depends on ktriz-core (`api
    // ktriz-core`), so the reverse dependency direction would be cyclic. Keep both copies in
    // sync if the scheme list ever changes.
    private val REJECTED_URI_SCHEMES = setOf("http", "https", "ftp", "ftps", "jar", "file", "data")

    private fun hasRejectedUriScheme(path: String): Boolean {
        val colonIndex = path.indexOf(':')
        if (colonIndex <= 0) return false
        val scheme = path.substring(0, colonIndex)
        return scheme.lowercase() in REJECTED_URI_SCHEMES
    }

    /**
     * Property-aware resolution: if [OVERRIDE_PROPERTY] is set, loads that file through
     * [fromFile]'s full gate; otherwise returns [bundled]. Pure and stateless -- safe to call
     * any number of times from any number of tests without affecting
     * [ContradictionMatrix]'s own lazily-cached singleton, which calls this exactly once.
     *
     * Deliberately `public`, not `internal`: `ktriz-tests` is a separate Gradle
     * module/Kotlin compilation unit from `ktriz-core` (a project dependency does not grant
     * `internal` visibility across module boundaries, mirroring [ContradictionMatrix]'s own
     * `populatedCellCount` visibility note), and every property-override test must call this
     * function directly rather than touching [ContradictionMatrix]'s `by lazy` singleton --
     * which, once initialised anywhere in the same JVM, is frozen for the rest of the process.
     */
    fun resolve(): Resolved {
        val overridePath = System.getProperty(OVERRIDE_PROPERTY)
        if (overridePath.isNullOrBlank()) {
            return Resolved(data = bundled(), origin = Origin.BUNDLED_CLASSICAL)
        }
        // Checked on the raw string, before Path.of: on some platforms (notably Windows,
        // where ':' is illegal anywhere but a drive letter) Path.of itself throws
        // InvalidPathException for a URI-shaped value before the scheme check below would
        // ever run, which would leak a raw, unchecked exception instead of the explanatory
        // "not a URL" message this class's KDoc promises.
        check(!hasRejectedUriScheme(overridePath)) {
            "kTRIZ matrix file '$overridePath' must be a local file path, not a URL. kTRIZ " +
                "never downloads matrix data; see kTRIZ-ADR-0003."
        }
        val path =
            try {
                Path.of(overridePath)
            } catch (e: InvalidPathException) {
                error(
                    "kTRIZ matrix file '$overridePath' (from -D$OVERRIDE_PROPERTY) is not a " +
                        "valid local file path: ${e.message}",
                )
            }
        val data = fromFile(path)
        return Resolved(data = data, origin = Origin.EXTERNAL_FILE)
    }

    /** Always the bundled classical matrix, regardless of [OVERRIDE_PROPERTY]. */
    fun bundled(): ContradictionMatrixData {
        val stream =
            ContradictionMatrixSource::class.java.getResourceAsStream(BUNDLED_RESOURCE_NAME)
                ?: error(
                    "Bundled contradiction matrix resource '$BUNDLED_RESOURCE_NAME' is missing " +
                        "from the classpath next to dev.ktriz.core.ContradictionMatrixSource. " +
                        "This is a packaging bug -- the file should live at " +
                        "ktriz-core/src/main/resources/dev/ktriz/core/$BUNDLED_RESOURCE_NAME.",
                )
        return stream.reader(StandardCharsets.UTF_8).use { reader ->
            ContradictionMatrixData.parse(reader, provenance = BUNDLED_PROVENANCE)
        }
    }

    /**
     * Loads an operator-named external matrix file through the same hardening gate as
     * [OVERRIDE_PROPERTY] and the exact same parser ([ContradictionMatrixData.parse]) the
     * bundled matrix uses -- there is no second, laxer validation path.
     *
     * Gate, in order (every step load-bearing -- see the class KDoc's "What this deliberately
     * does not do" and "Threat model" sections for the rationale behind the design):
     *  1. [path] must not look like a URI (`hasRejectedUriScheme`) -- kTRIZ never downloads
     *     matrix data.
     *  2. [path] must be absolute -- a relative path is ambiguous about its working
     *     directory and is rejected rather than silently resolved against "whatever the
     *     process cwd happens to be".
     *  3. [Path.toRealPath] canonicalises symlinks *and* requires the file to exist.
     *  4. The canonicalised path must be a regular file -- rejects directories, FIFOs (which
     *     would block forever), and device files (which may be unbounded).
     *  5. The canonicalised path must be readable.
     *  6. Read at most [MAX_MATRIX_BYTES] bytes (a DoS cap, not a trust boundary).
     *  7. Decode strictly as UTF-8 (malformed bytes are a hard error, not silently replaced).
     *  8. Parse via [ContradictionMatrixData.parse] -- the single validation path.
     *  9. The parsed data must contain at least one populated cell -- an empty, comment-only,
     *     or header-only file parses *successfully* (zero is a valid cell count as far as the
     *     parser is concerned) but is almost always a truncated or empty file, not an
     *     intentionally empty matrix; see the class KDoc's "No silent fallback" rationale --
     *     the same "operator believes X, tool silently does Y" failure mode applies just as
     *     much to a load that succeeds with zero usable data as to one that falls back
     *     unnoticed.
     *  10. Log the full canonical path at INFO -- an override should never take effect
     *     unnoticed by whoever operates the process.
     *
     * There is deliberately **no fallback to the bundled matrix** if any of the above fails:
     * this function throws. A silent fallback would let an operator believe they are using
     * their own matrix while actually getting classical results back.
     *
     * @param path the matrix file to load. Must be an absolute path to an existing, regular,
     *   readable, UTF-8-encoded file no larger than [MAX_MATRIX_BYTES] and no URI, and must
     *   parse to at least one populated cell.
     * @param provenance provenance note to attach to the result. When `null` (the default),
     *   a note is synthesised from the file name, byte count, and a SHA-256 prefix --
     *   deliberately **never** the absolute path itself, because [ContradictionMatrix.provenance]
     *   (and this function's result via [Contradiction.resolve]) can flow into
     *   `ktriz-mcp`'s `resolve_contradiction` tool output, which is read by an LLM agent
     *   that should not learn the operator's local filesystem layout. The full path is only
     *   ever written to the local log (step 10 above).
     * @throws IllegalStateException if any gate step fails, or if [ContradictionMatrixData.parse]
     *   rejects the file's content.
     */
    fun fromFile(
        path: Path,
        provenance: String? = null,
    ): ContradictionMatrixData {
        check(!hasRejectedUriScheme(path.toString())) {
            "kTRIZ matrix file '$path' must be a local file path, not a URL. kTRIZ never " +
                "downloads matrix data; see kTRIZ-ADR-0003."
        }
        check(path.isAbsolute) {
            "kTRIZ matrix file '$path' must be an absolute path (got a relative path). " +
                "Use path.toAbsolutePath() or pass an absolute path from the start."
        }
        val real =
            try {
                path.toRealPath()
            } catch (e: IOException) {
                error(
                    "kTRIZ matrix file '$path' could not be resolved (does it exist?): " +
                        "${e.message}",
                )
            }
        check(Files.isRegularFile(real)) {
            "kTRIZ matrix file '$real' is not a regular file (directories, FIFOs, and device " +
                "files are rejected)."
        }
        check(Files.isReadable(real)) {
            "kTRIZ matrix file '$real' is not readable."
        }

        val bytes = readBounded(real)
        val digest = sha256Hex(bytes)
        val text = decodeStrictUtf8(bytes, real)

        val effectiveProvenance =
            provenance ?: run {
                val fileName = real.fileName?.toString() ?: real.toString()
                "External contradiction matrix supplied by the operator: '$fileName', " +
                    "${bytes.size} bytes, sha256:${digest.take(16)}. Not shipped with kTRIZ; " +
                    "kTRIZ makes no claim about its licence, accuracy, or completeness " +
                    "(see kTRIZ-ADR-0003)."
            }

        val data = ContradictionMatrixData.parse(StringReader(text), provenance = effectiveProvenance)
        check(data.populatedCellCount > 0) {
            "kTRIZ matrix file '$real' parsed successfully but contains zero populated cells " +
                "(only comments, a header line, and/or blank lines). This is almost always a " +
                "truncated or empty file (an interrupted copy, a 0-byte placeholder) rather " +
                "than an intentionally empty matrix -- kTRIZ refuses it rather than silently " +
                "answering every contradiction lookup with an empty principles list, which " +
                "would be indistinguishable from a legitimately unpopulated cell."
        }

        logger.info {
            "kTRIZ contradiction matrix loaded from external file '$real' (${bytes.size} bytes, " +
                "sha256:$digest, ${data.populatedCellCount} cells) via -D$OVERRIDE_PROPERTY"
        }

        return data
    }

    // Reads at most MAX_MATRIX_BYTES + 1 bytes so an over-limit file is detected without ever
    // trusting Files.size() (which would be a TOCTOU: the file can change between a size check
    // and the actual read, and reports 0 for some FIFO/procfs-like special files that would
    // otherwise read as unbounded). The open+read itself is wrapped for IOException so a race
    // between the gate's isReadable check and this call (the file is deleted, or permissions
    // change, in between) still surfaces as the same IllegalStateException fromFile's @throws
    // documents, not a raw checked-turned-unchecked IOException escaping past this function.
    private fun readBounded(path: Path): ByteArray {
        val limit = MAX_MATRIX_BYTES + 1
        val bytes =
            try {
                Files.newInputStream(path).use { input -> input.readNBytes(limit.toInt()) }
            } catch (e: IOException) {
                error("kTRIZ matrix file '$path' could not be read: ${e.message}")
            }
        check(bytes.size <= MAX_MATRIX_BYTES) {
            "kTRIZ matrix file '$path' is at least ${bytes.size} bytes, exceeding the " +
                "$MAX_MATRIX_BYTES byte limit."
        }
        return bytes
    }

    // Strict UTF-8 decoding: CodingErrorAction.REPORT turns malformed input into a clear
    // failure here, instead of String(bytes, UTF_8)'s default of silently replacing bad bytes
    // with U+FFFD, which would otherwise surface as a baffling downstream parser error. A
    // leading UTF-8 BOM (EF BB BF), which a file saved from Windows/Excel commonly carries, is
    // stripped first -- left in place, it would prepend U+FEFF to the header line, defeating
    // ContradictionMatrixData.parse's literal `startsWith("improving,worsening,principles")`
    // header check and making the header line parse as a malformed data line instead.
    private fun decodeStrictUtf8(
        bytes: ByteArray,
        path: Path,
    ): String {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val withoutBom =
            if (bytes.size >= bom.size && bytes.copyOfRange(0, bom.size).contentEquals(bom)) {
                bytes.copyOfRange(bom.size, bytes.size)
            } else {
                bytes
            }
        val decoder =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(withoutBom)).toString()
        } catch (e: CharacterCodingException) {
            error("kTRIZ matrix file '$path' is not valid UTF-8: ${e.message}")
        }
    }

    // A new MessageDigest instance per call: MessageDigest is not thread-safe, and this is a
    // pure audit fingerprint (surfaced in the default provenance note and the INFO log), never
    // an integrity check against a known-good expected hash -- fromFile makes no such claim.
    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
