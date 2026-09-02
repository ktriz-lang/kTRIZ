package dev.ktriz.tests

import dev.ktriz.core.ContradictionMatrix
import dev.ktriz.core.ContradictionMatrixSource
import dev.ktriz.core.EngineeringParameter.STRENGTH
import dev.ktriz.core.EngineeringParameter.WEIGHT_OF_MOVING_OBJECT
import dev.ktriz.core.InventivePrinciple
import dev.ktriz.core.contradiction
import dev.ktriz.core.resolve
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

/**
 * Covers [ContradictionMatrixSource] -- the pluggable matrix-source seam kTRIZ-ADR-0003 calls
 * for (`fromFile`, `resolve()`/[ContradictionMatrixSource.OVERRIDE_PROPERTY], `bundled()`).
 *
 * Every property-mutating case sets [ContradictionMatrixSource.OVERRIDE_PROPERTY] in a
 * `try`/`finally` and calls [ContradictionMatrixSource.resolve] directly -- never
 * [ContradictionMatrix], whose `by lazy` singleton is process-global and would otherwise
 * freeze on whichever value happened to be set the first time any test touched it.
 *
 * Fixtures are real temp files (`kotlin.io.path.createTempDirectory`-equivalent via
 * `Files.createTempDirectory`), not classpath resources -- the mechanism under test is
 * explicitly filesystem-based.
 */
class ContradictionMatrixSourceTest :
    StringSpec({

        val tempDir = Files.createTempDirectory("ktriz-matrix-source-test")

        afterSpec {
            // Otherwise every `./gradlew check` run leaves this behind permanently, including
            // the ~4 MiB "oversized.csv" fixture below.
            tempDir.toFile().deleteRecursively()
        }

        fun writeCsv(
            name: String,
            content: String,
        ): Path {
            val file = tempDir.resolve(name)
            Files.write(file, content.toByteArray(StandardCharsets.UTF_8))
            return file
        }

        // Takes the raw property value as a String, not a Path: constructing a Path from a
        // URI-shaped or otherwise platform-illegal string (e.g. "https://...", which contains
        // a ':' that Windows' Path.of rejects outright) must stay possible here so the tests
        // below can exercise ContradictionMatrixSource.resolve()'s own string-first validation
        // -- see its KDoc -- instead of failing in the test body before resolve() ever runs.
        fun withOverride(
            value: String,
            block: () -> Unit,
        ) {
            System.setProperty(ContradictionMatrixSource.OVERRIDE_PROPERTY, value)
            try {
                block()
            } finally {
                System.clearProperty(ContradictionMatrixSource.OVERRIDE_PROPERTY)
            }
        }

        // ---- Happy path: fromFile -----------------------------------------------------------

        "fromFile loads an alternative matrix with its own cells, independent of the bundled one" {
            val file =
                writeCsv(
                    "two-cell.csv",
                    """
                    improving,worsening,principles,sources
                    1,14,15 8,TEST
                    3,4,1,TEST
                    """.trimIndent(),
                )

            val data = ContradictionMatrixSource.fromFile(file)

            data.populatedCellCount shouldBe 2
            data.lookup(contradiction(improving = WEIGHT_OF_MOVING_OBJECT, worsening = STRENGTH)) shouldBe
                listOf(InventivePrinciple.ofId(15), InventivePrinciple.ofId(8))
        }

        "fromFile with an explicit provenance passes that string through unchanged" {
            val file = writeCsv("explicit-provenance.csv", "1,2,15,TEST")

            val data = ContradictionMatrixSource.fromFile(file, provenance = "my house matrix, v3")

            data.provenance shouldBe "my house matrix, v3"
        }

        "fromFile without a provenance synthesises one with the file name, size and a sha256 prefix, never the path" {
            val file = writeCsv("no-provenance.csv", "1,2,15,TEST")

            val data = ContradictionMatrixSource.fromFile(file)

            data.provenance shouldContain "no-provenance.csv"
            data.provenance shouldContain "sha256:"
            data.provenance shouldNotContain tempDir.toString()
        }

        "resolve() with the override property set returns the external file's data as EXTERNAL_FILE" {
            val file = writeCsv("override-set.csv", "1,2,15,TEST\n3,4,1 2,TEST")

            withOverride(file.toString()) {
                val resolved = ContradictionMatrixSource.resolve()
                resolved.origin shouldBe ContradictionMatrixSource.Origin.EXTERNAL_FILE
                resolved.data.populatedCellCount shouldBe 2
            }
        }

        "resolve() without the override property returns the bundled classical matrix as BUNDLED_CLASSICAL" {
            System.clearProperty(ContradictionMatrixSource.OVERRIDE_PROPERTY)

            val resolved = ContradictionMatrixSource.resolve()

            resolved.origin shouldBe ContradictionMatrixSource.Origin.BUNDLED_CLASSICAL
            resolved.data.populatedCellCount shouldBe 1248
        }

        "an external file with comment, blank and header lines parses exactly like the bundled matrix" {
            val file =
                writeCsv(
                    "comments-and-header.csv",
                    """
                    # a house-authored matrix
                    improving,worsening,principles,sources

                    1,2,15 8,TEST
                    """.trimIndent(),
                )

            val data = ContradictionMatrixSource.fromFile(file)

            data.populatedCellCount shouldBe 1
        }

        // ---- Error cases: same parser, same messages -----------------------------------------

        "a diagonal cell in an external file fails with the same parser message as the bundled parser" {
            val file = writeCsv("diagonal.csv", "5,5,15,TEST")

            val exception = shouldThrow<IllegalStateException> { ContradictionMatrixSource.fromFile(file) }

            exception.message shouldContain "line 1"
            exception.message shouldContain "cannot contradict itself"
        }

        "a parameter id of 48 in an external file is rejected -- the 1..39 licence guardrail also applies externally" {
            val file = writeCsv("param-48.csv", "48,2,15,TEST")

            val exception = shouldThrow<IllegalStateException> { ContradictionMatrixSource.fromFile(file) }

            exception.message shouldContain "out of range"
            exception.message shouldContain "48-parameter"
        }

        "a duplicate cell in an external file is rejected with the duplicate-cell parser message" {
            val file = writeCsv("duplicate-cell.csv", "1,2,15,TEST\n1,2,8,TEST")

            val exception = shouldThrow<IllegalStateException> { ContradictionMatrixSource.fromFile(file) }

            exception.message shouldContain "duplicate cell"
        }

        // ---- Gate errors: never reach the parser ----------------------------------------------

        "a nonexistent file names the override property in the failure message" {
            val missing = tempDir.resolve("does-not-exist.csv")

            val exception = shouldThrow<IllegalStateException> { ContradictionMatrixSource.fromFile(missing) }

            exception.message shouldContain "does-not-exist.csv"
        }

        "a relative path is rejected with a hint to use an absolute path" {
            val relative = Path.of("relative-matrix.csv")

            val exception = shouldThrow<IllegalStateException> { ContradictionMatrixSource.fromFile(relative) }

            exception.message shouldContain "absolute"
        }

        "a directory instead of a file is rejected as not a regular file" {
            val exception = shouldThrow<IllegalStateException> { ContradictionMatrixSource.fromFile(tempDir) }

            exception.message shouldContain "not a regular file"
        }

        "a file over MAX_MATRIX_BYTES is rejected on size before the parser ever runs" {
            val oversized = tempDir.resolve("oversized.csv")
            Files.write(oversized, ByteArray((ContradictionMatrixSource.MAX_MATRIX_BYTES + 1).toInt()))

            val exception = shouldThrow<IllegalStateException> { ContradictionMatrixSource.fromFile(oversized) }

            // If the parser had been reached, the message would come from
            // ContradictionMatrixData.parse ("line ...: ..."), not this size message.
            exception.message shouldContain "byte limit"
            exception.message shouldNotContain "line "
        }

        "an https:// override value is rejected as a URL, never resolved as a file, never downloaded" {
            withOverride("https://example.invalid/matrix.csv") {
                val exception = shouldThrow<IllegalStateException> { ContradictionMatrixSource.resolve() }
                exception.message shouldContain "not a URL"
            }
        }

        "a file:// override value is rejected with the same URL-scheme guard" {
            withOverride("file:///tmp/matrix.csv") {
                val exception = shouldThrow<IllegalStateException> { ContradictionMatrixSource.resolve() }
                exception.message shouldContain "not a URL"
            }
        }

        "an invalid override path fails clearly, not with a raw InvalidPathException" {
            // A NUL byte is illegal in a path on every platform Path.of runs on (POSIX and
            // Windows alike), and isn't URI-shaped, so this exercises resolve()'s
            // InvalidPathException translation specifically, not the URI-scheme guard above.
            // shouldThrow<IllegalStateException> also proves the raw InvalidPathException
            // never escapes resolve() unwrapped.
            withOverride("matrix .csv") {
                val exception = shouldThrow<IllegalStateException> { ContradictionMatrixSource.resolve() }
                exception.message shouldContain "not a valid local file path"
            }
        }

        "a file with invalid UTF-8 bytes fails with a clear encoding message, not a cryptic parser error" {
            val file = tempDir.resolve("invalid-utf8.csv")
            Files.write(file, byteArrayOf(0x31, 0x2C, 0x32, 0x2C, 0xFF.toByte(), 0x2C, 0x54))

            val exception = shouldThrow<IllegalStateException> { ContradictionMatrixSource.fromFile(file) }

            exception.message shouldContain "not valid UTF-8"
        }

        "a file with a leading UTF-8 BOM parses successfully -- the header line is still recognised" {
            val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
            val body = "improving,worsening,principles,sources\n1,2,15,TEST".toByteArray(StandardCharsets.UTF_8)
            val file = tempDir.resolve("bom.csv")
            Files.write(file, bom + body)

            val data = ContradictionMatrixSource.fromFile(file)

            data.populatedCellCount shouldBe 1
        }

        "a symlink to a valid matrix file resolves and parses successfully" {
            val target = writeCsv("symlink-target.csv", "1,2,15,TEST")
            val link = tempDir.resolve("symlink-source.csv")
            Files.deleteIfExists(link)
            val symlinkCreated =
                try {
                    Files.createSymbolicLink(link, target)
                    true
                } catch (e: UnsupportedOperationException) {
                    // Filesystem doesn't support symlinks at all -- nothing to test here.
                    false
                } catch (e: FileSystemException) {
                    // Windows without Developer Mode/SeCreateSymbolicLinkPrivilege refuses
                    // unprivileged symlink creation -- Path.toRealPath's canonicalisation step
                    // is exercised by every other test that reads through tempDir already, so
                    // skip rather than false-failing the whole suite for an environment
                    // permission this test isn't meant to verify.
                    false
                }

            if (symlinkCreated) {
                val data = ContradictionMatrixSource.fromFile(link)
                data.populatedCellCount shouldBe 1
            }
        }

        "an unreadable file is rejected as not readable" {
            val file = writeCsv("unreadable.csv", "1,2,15,TEST")
            val madeUnreadableViaPosix =
                try {
                    Files.setPosixFilePermissions(file, emptySet())
                    true
                } catch (e: UnsupportedOperationException) {
                    // Non-POSIX filesystem (e.g. Windows) -- fall back to the legacy File API.
                    file.toFile().setReadable(false)
                    false
                }
            try {
                // Running as root (POSIX permissions don't restrict root) or as an
                // elevated/ACL-bypassing account on a non-POSIX filesystem, Files.isReadable
                // would report true regardless of the permission change above, so this gate
                // step cannot be exercised in this environment -- skip rather than
                // false-failing the whole suite for an environment property this test isn't
                // meant to verify.
                if (!Files.isReadable(file)) {
                    val exception = shouldThrow<IllegalStateException> { ContradictionMatrixSource.fromFile(file) }
                    exception.message shouldContain "not readable"
                }
            } finally {
                // Restore permissions so tempDir cleanup (afterSpec) can delete the file.
                if (madeUnreadableViaPosix) {
                    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"))
                } else {
                    file.toFile().setReadable(true)
                }
            }
        }

        "a matrix file with only comments and a header line -- zero populated cells -- is rejected" {
            val file =
                writeCsv(
                    "empty-matrix.csv",
                    """
                    # nothing here yet
                    improving,worsening,principles,sources
                    """.trimIndent(),
                )

            val exception = shouldThrow<IllegalStateException> { ContradictionMatrixSource.fromFile(file) }

            exception.message shouldContain "zero populated cells"
        }

        // ---- Contradiction.resolve(matrix) ergonomic overload --------------------------------

        "Contradiction.resolve(matrix) looks up against the explicit matrix, not ContradictionMatrix's default" {
            val file = writeCsv("resolve-overload.csv", "1,14,15 8,TEST")
            val data = ContradictionMatrixSource.fromFile(file)

            val principles =
                contradiction(improving = WEIGHT_OF_MOVING_OBJECT, worsening = STRENGTH).resolve(data)

            principles shouldBe listOf(InventivePrinciple.ofId(15), InventivePrinciple.ofId(8))
        }
    })
