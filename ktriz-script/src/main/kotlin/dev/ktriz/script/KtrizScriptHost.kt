package dev.ktriz.script

import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

private val logger = KotlinLogging.logger {}

// Explicit allowlist of the URI schemes resolveSource actually needs to reject, checked
// case-insensitively against whatever precedes the *first* colon in the path -- deliberately
// not a generic "does this look like a URI" heuristic. A generic scheme-shaped regex
// (`^[a-zA-Z][a-zA-Z0-9+.-]+:`) also matches legal relative POSIX paths whose first segment
// merely contains a colon (e.g. a generated name like "run:1.ktriz.kts" -- colons are valid
// filename characters on Linux/macOS), rejecting a real local file as if it were a URI. An
// explicit allowlist has no such false positive: only these schemes are ever rejected, and a
// Windows drive letter (`C:\scripts\hello.ktriz.kts`) never collides with any of them, so no
// separate single-letter carve-out is needed either (see KtrizScriptHostTest for both cases).
private val REJECTED_URI_SCHEMES = setOf("http", "https", "ftp", "ftps", "jar", "file", "data")

// True when [path] starts with "<scheme>:" and <scheme> (case-insensitively) is one of
// REJECTED_URI_SCHEMES. Only the text before the first colon is treated as a candidate scheme;
// a colon anywhere later in the path (e.g. inside a Windows path's later segments) is
// irrelevant here.
private fun hasRejectedUriScheme(path: String): Boolean {
    val colonIndex = path.indexOf(':')
    if (colonIndex <= 0) return false
    val scheme = path.substring(0, colonIndex)
    return scheme.lowercase() in REJECTED_URI_SCHEMES
}

/**
 * Evaluates `*.ktriz.kts` scripts using [KtrizScriptCompilationConfiguration], mapping every
 * outcome -- successful or not -- into a [KtrizScriptOutcome]. Never lets a raw
 * `kotlin.script.experimental.*` type, an unhandled exception, or a stack trace escape to the
 * caller (`ktriz-cli`'s `ktriz run` subcommand): a `.ktriz.kts` script is arbitrary user input,
 * and this host is the "compiler as oracle" boundary that turns whatever it does -- compile
 * cleanly, fail to compile, run, or throw -- into one of [KtrizScriptOutcome]'s structured
 * cases.
 *
 * The host is reused across calls -- do not create multiple instances.
 *
 * **Deliberately no sandbox**: [KtrizScriptCompilationConfiguration] uses
 * `wholeClasspath = true` with no curated/allowlisted classpath, mirroring kSTEP's
 * `KStepScriptHost` and kUML's own *trusted in-process* script path (kUML's equivalent
 * *sandboxed* path exists only for its hosted-portal "compile someone else's script"
 * scenario, which kTRIZ has no equivalent of). A `.ktriz.kts` script run via `ktriz run` is
 * local, operator-invoked, arbitrary Kotlin -- the same trust level as running
 * `kotlinc -script` or a `build.gradle.kts` on one's own machine. It runs with the full
 * rights of the invoking user. If kTRIZ later grows a hosted surface that compiles
 * third-party scripts, port kUML's curated-classpath + worker-process approach then, not now.
 *
 * **The file-path security gate is a DoS/URI leitplanke, not a sandbox.** [eval] rejects a
 * blank path, a path that looks like a URI (no automatic remote resolution -- see
 * [resolveSource]), anything that isn't a plain regular file, and anything over
 * [MAX_SCRIPT_BYTES]. It does **not** confine the path to a base directory: `ktriz run` is
 * operator-invoked, and any local path the operator names is legitimate to read. A script
 * *within* those limits may still do anything a trusted local process can do -- that is the
 * whole point of "no sandbox" above, not a gap in this gate.
 */
object KtrizScriptHost {
    private val host = BasicJvmScriptingHost()
    private val compilationConfig = createJvmCompilationConfigurationFromTemplate<KtrizScript>()

    /**
     * Maximum size of a `.ktriz.kts` file (1 MiB). A DoS leitplanke, not a security boundary
     * -- a script *within* this limit may still do anything (see the class KDoc above).
     */
    const val MAX_SCRIPT_BYTES: Long = 1L * 1024 * 1024

    /**
     * Evaluates the `.ktriz.kts` file at [path]. Always runs the security gate
     * ([resolveSource]) first -- there is deliberately no `eval(File)` overload that could
     * bypass it (a divergence from kSTEP's `KStepScriptHost`, which offers both `eval(File)`
     * and `eval(String code)`; kTRIZ keeps exactly one file-based entry point to remove that
     * ambiguity).
     *
     * @param captureStdout when `true`, the script's stdout is captured into
     *   [KtrizScriptOutcome.Success.capturedStdout]/[KtrizScriptOutcome.RuntimeError.capturedStdout]
     *   instead of passing through to the process's real stdout. See the "stdout capture"
     *   section below.
     */
    fun eval(
        path: String,
        captureStdout: Boolean = false,
    ): KtrizScriptOutcome {
        // kotlin.Result.getOrElse is an inline function, so `return` inside this lambda is a
        // non-local return out of eval() itself, not out of getOrElse -- exactly what makes
        // this short-circuit idiom work.
        val file =
            resolveSource(path).getOrElse { rejection ->
                return (rejection as RejectedSourceException).outcome
            }
        return evalToOutcome(captureStdout) { host.eval(file.toScriptSource(), compilationConfig, null) }
    }

    /**
     * Evaluates inline `.ktriz.kts` source directly -- useful for tests and embedded callers
     * that already hold the script text. Bypasses the file-based security gate in [eval]
     * because there is no file: the caller already holds the source.
     */
    fun evalSource(
        code: String,
        fileName: String = "inline.ktriz.kts",
        captureStdout: Boolean = false,
    ): KtrizScriptOutcome =
        evalToOutcome(captureStdout) { host.eval(code.toScriptSource(fileName), compilationConfig, null) }

    // Security gate for eval(path). Order is load-bearing: a blank path is checked before it
    // is ever handed to java.io.File; a URI-shaped path is rejected before any filesystem call
    // that could resolve it; only then do the filesystem-existence/kind/readability/size
    // checks run, each against the *canonicalized* file (not the raw input string) so error
    // messages are unambiguous. Returns the resolved File, or the SourceRejected outcome to
    // return to the caller directly.
    private fun resolveSource(path: String): Result<File> {
        if (path.isBlank()) {
            return Result.failure(
                RejectedSourceException(
                    KtrizScriptOutcome.SourceRejected(
                        KtrizScriptOutcomeCodes.SOURCE_BLANK_PATH,
                        "script path must not be blank",
                    ),
                ),
            )
        }
        if (hasRejectedUriScheme(path)) {
            return Result.failure(
                RejectedSourceException(
                    KtrizScriptOutcome.SourceRejected(
                        KtrizScriptOutcomeCodes.SOURCE_REMOTE_URI,
                        "script path must be a local file path, not a URI: '$path'",
                    ),
                ),
            )
        }
        val canonical =
            try {
                File(path).canonicalFile
            } catch (e: java.io.IOException) {
                return Result.failure(
                    RejectedSourceException(
                        KtrizScriptOutcome.SourceRejected(
                            KtrizScriptOutcomeCodes.SOURCE_NOT_FOUND,
                            "could not resolve script path '$path': ${e.message}",
                        ),
                    ),
                )
            }
        if (!canonical.exists()) {
            return Result.failure(
                RejectedSourceException(
                    KtrizScriptOutcome.SourceRejected(
                        KtrizScriptOutcomeCodes.SOURCE_NOT_FOUND,
                        "script file not found: '${canonical.path}'",
                    ),
                ),
            )
        }
        if (!canonical.isFile) {
            return Result.failure(
                RejectedSourceException(
                    KtrizScriptOutcome.SourceRejected(
                        KtrizScriptOutcomeCodes.SOURCE_NOT_A_FILE,
                        "script path is not a regular file: '${canonical.path}'",
                    ),
                ),
            )
        }
        if (!canonical.canRead()) {
            return Result.failure(
                RejectedSourceException(
                    KtrizScriptOutcome.SourceRejected(
                        KtrizScriptOutcomeCodes.SOURCE_NOT_READABLE,
                        "script file is not readable: '${canonical.path}'",
                    ),
                ),
            )
        }
        if (canonical.length() > MAX_SCRIPT_BYTES) {
            return Result.failure(
                RejectedSourceException(
                    KtrizScriptOutcome.SourceRejected(
                        KtrizScriptOutcomeCodes.SOURCE_TOO_LARGE,
                        "script file '${canonical.path}' is ${canonical.length()} bytes, " +
                            "exceeding the $MAX_SCRIPT_BYTES byte limit",
                    ),
                ),
            )
        }
        return Result.success(canonical)
    }

    // resolveSource()'s Result<File> failure channel needs a Throwable to carry the rejection
    // outcome through kotlin.Result.getOrElse -- unwrapped again immediately in eval() above.
    // Never thrown, never logged: purely a typed carrier.
    private class RejectedSourceException(
        val outcome: KtrizScriptOutcome.SourceRejected,
    ) : Exception()

    // Wraps the actual host.eval(...) invocation itself in a broad catch: kotlin-scripting
    // normally reports both compile errors (ResultWithDiagnostics.Failure) and script-runtime
    // exceptions (ResultValue.Error, handled in mapReturnValue below) *within* its result type,
    // never as a thrown exception out of eval() itself -- but a script source file deleted
    // between resolveSource and this call, or an internal scripting-host failure, could still
    // throw directly. Catching here is what keeps that from ever reaching ktriz-cli as a raw
    // stack trace.
    private fun evalToOutcome(
        captureStdout: Boolean,
        block: () -> ResultWithDiagnostics<EvaluationResult>,
    ): KtrizScriptOutcome {
        if (!captureStdout) {
            return try {
                mapResult(block(), capturedStdout = null)
            } catch (e: Exception) {
                logger.warn(e) { "kTRIZ script evaluation threw before producing a scripting result" }
                KtrizScriptOutcome.RuntimeError(
                    message = e.message ?: "unknown error",
                    exceptionClass = e::class.qualifiedName ?: "unknown",
                    capturedStdout = null,
                )
            }
        }

        // Process-global: System.setOut is swapped for the duration of this call, synchronized
        // on `host` so two concurrent evals don't interleave into the same buffer -- but any
        // *other* thread that prints while this eval runs is captured too. Acceptable for a
        // one-shot CLI process and for a sequential test run; not acceptable for a
        // server-embedding scenario. That is exactly why this is opt-in, never the default.
        return synchronized(host) {
            val originalOut = System.out
            val buffer = ByteArrayOutputStream()
            // UTF-8 explicit: PrintStream(OutputStream) alone uses the platform default
            // charset, which silently mangles non-ASCII output (e.g. the hello-world script's
            // "↑"/"↓" arrows and German umlauts become "?").
            val captured = PrintStream(buffer, true, StandardCharsets.UTF_8)
            System.setOut(captured)
            try {
                // mapResult itself is capture-agnostic (always called with capturedStdout =
                // null); the buffer is only read *after* block() has run, and its text is
                // spliced into the outcome here.
                val outcome = mapResult(block(), capturedStdout = null)
                withCapturedStdout(outcome, buffer.toString(StandardCharsets.UTF_8))
            } catch (e: Exception) {
                logger.warn(e) { "kTRIZ script evaluation threw before producing a scripting result" }
                KtrizScriptOutcome.RuntimeError(
                    message = e.message ?: "unknown error",
                    exceptionClass = e::class.qualifiedName ?: "unknown",
                    capturedStdout = buffer.toString(StandardCharsets.UTF_8),
                )
            } finally {
                System.setOut(originalOut)
            }
        }
    }

    private fun withCapturedStdout(
        outcome: KtrizScriptOutcome,
        text: String,
    ): KtrizScriptOutcome =
        when (outcome) {
            is KtrizScriptOutcome.Success -> outcome.copy(capturedStdout = text)
            is KtrizScriptOutcome.RuntimeError -> outcome.copy(capturedStdout = text)
            is KtrizScriptOutcome.CompilationError -> outcome
            is KtrizScriptOutcome.NotEvaluated -> outcome
            is KtrizScriptOutcome.SourceRejected -> outcome
        }

    private fun mapResult(
        result: ResultWithDiagnostics<EvaluationResult>,
        capturedStdout: String?,
    ): KtrizScriptOutcome {
        val errorDiagnostics = result.reports.filter { it.severity >= ScriptDiagnostic.Severity.ERROR }
        // A Failure result is always a compile error, but a Success result can *also* carry
        // ERROR-severity reports -- checking both, not just the sealed-variant check alone, is
        // what makes this robust.
        if (result is ResultWithDiagnostics.Failure || errorDiagnostics.isNotEmpty()) {
            // DEBUG/INFO reports are the scripting host's own internal chatter (JDK/module
            // resolution, classpath diagnostics) -- never useful to a script author and noisy
            // enough to bury the actual error, so only WARNING and above are surfaced here.
            val relevantDiagnostics = result.reports.filter { it.severity >= ScriptDiagnostic.Severity.WARNING }
            return KtrizScriptOutcome.CompilationError(relevantDiagnostics.map { it.toView() })
        }
        val success = result as ResultWithDiagnostics.Success
        return mapReturnValue(success.value.returnValue, capturedStdout)
    }

    private fun mapReturnValue(
        returnValue: ResultValue,
        capturedStdout: String?,
    ): KtrizScriptOutcome =
        when (returnValue) {
            is ResultValue.Value ->
                KtrizScriptOutcome.Success(
                    returnValue = returnValue.value,
                    returnTypeName = returnValue.type,
                    capturedStdout = capturedStdout,
                )
            is ResultValue.Unit ->
                // Unit -- not a failure. A println-based script (the normal shape) always ends
                // this way; "success" means "compiled and ran without throwing", not "returned
                // a particular type" (a deliberate divergence from kSTEP's KStepScriptHost,
                // whose scripts must end in stepFile { } to signal success).
                KtrizScriptOutcome.Success(
                    returnValue = null,
                    returnTypeName = null,
                    capturedStdout = capturedStdout,
                )
            is ResultValue.Error -> mapRuntimeException(returnValue.error, capturedStdout)
            is ResultValue.NotEvaluated ->
                KtrizScriptOutcome.NotEvaluated("script did not evaluate to a result")
        }

    private fun mapRuntimeException(
        error: Throwable,
        capturedStdout: String?,
    ): KtrizScriptOutcome =
        KtrizScriptOutcome.RuntimeError(
            message = error.message ?: (error::class.qualifiedName ?: "unknown error"),
            exceptionClass = error::class.qualifiedName ?: "unknown",
            capturedStdout = capturedStdout,
        )

    private fun ScriptDiagnostic.toView(): ScriptDiagnosticView =
        ScriptDiagnosticView(
            severity = severity.name,
            message = message,
            line = location?.start?.line,
            column = location?.start?.col,
        )
}
