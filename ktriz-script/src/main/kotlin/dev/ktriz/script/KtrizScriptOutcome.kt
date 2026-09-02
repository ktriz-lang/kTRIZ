package dev.ktriz.script

/**
 * Structured code family for the script layer. Never surfaced as a thrown exception or a raw
 * stack trace to a [KtrizScriptHost] caller -- always one of [KtrizScriptOutcome]'s structured
 * cases.
 */
object KtrizScriptOutcomeCodes {
    const val COMPILATION_ERROR = "KTRIZ-S-001"
    const val RUNTIME_ERROR = "KTRIZ-S-002"
    const val NOT_EVALUATED = "KTRIZ-S-003"

    // SourceRejected sub-family -- KtrizScriptHost.eval(path)'s security gate.
    const val SOURCE_BLANK_PATH = "KTRIZ-S-010"
    const val SOURCE_REMOTE_URI = "KTRIZ-S-011"
    const val SOURCE_NOT_FOUND = "KTRIZ-S-012"
    const val SOURCE_NOT_A_FILE = "KTRIZ-S-013"
    const val SOURCE_NOT_READABLE = "KTRIZ-S-014"
    const val SOURCE_TOO_LARGE = "KTRIZ-S-015"
}

/**
 * One compiler diagnostic, reduced to what a CLI/JSON consumer needs. No internal
 * `kotlin.script.experimental.api.ScriptDiagnostic` ever leaves [KtrizScriptHost].
 */
data class ScriptDiagnosticView(
    val severity: String,
    val message: String,
    val line: Int?,
    val column: Int?,
)

/**
 * The outcome of [KtrizScriptHost.eval]/[KtrizScriptHost.evalSource] -- never a thrown
 * exception or a raw stack trace, always one of these structured cases.
 */
sealed interface KtrizScriptOutcome {
    /**
     * The script compiled and ran to completion without throwing. [returnValue] is `null` when
     * the script's last expression was `Unit` -- the normal case for a `println`-based script
     * such as the Hello World example (see [KtrizScript]'s KDoc). This is deliberately *not*
     * an error: unlike kSTEP's `*.kstep.kts` scripts, a `.ktriz.kts` script has no required
     * return type -- "success" here means "compiled and ran without throwing".
     */
    data class Success(
        val returnValue: Any?,
        val returnTypeName: String?,
        /** Only non-null when the caller opted into `captureStdout = true`. */
        val capturedStdout: String?,
    ) : KtrizScriptOutcome

    /**
     * [KtrizScriptOutcomeCodes.COMPILATION_ERROR] -- a syntax/type/`unresolved reference`
     * error. Holds at least one entry of severity `"ERROR"`. This is the
     * generate-compile-repair signal.
     */
    data class CompilationError(
        val diagnostics: List<ScriptDiagnosticView>,
    ) : KtrizScriptOutcome

    /**
     * [KtrizScriptOutcomeCodes.RUNTIME_ERROR] -- the script threw at runtime. Carries only the
     * exception class and message, never a stack trace. The `require` violation thrown from
     * [dev.ktriz.core.Contradiction]'s `init` block lands here; its message is itself written
     * to double as a repair signal.
     */
    data class RuntimeError(
        val message: String,
        val exceptionClass: String,
        val capturedStdout: String?,
    ) : KtrizScriptOutcome

    /** [KtrizScriptOutcomeCodes.NOT_EVALUATED] -- the scripting host returned
     * `ResultValue.NotEvaluated`.
     */
    data class NotEvaluated(
        val message: String,
    ) : KtrizScriptOutcome

    /**
     * [KtrizScriptOutcomeCodes.SOURCE_BLANK_PATH]/[KtrizScriptOutcomeCodes.SOURCE_REMOTE_URI]/
     * etc. -- the source file was rejected *before* anything was compiled or run. See
     * [KtrizScriptHost]'s security gate.
     */
    data class SourceRejected(
        val code: String,
        val message: String,
    ) : KtrizScriptOutcome
}
