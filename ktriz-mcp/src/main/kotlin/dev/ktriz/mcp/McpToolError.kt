package dev.ktriz.mcp

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Max. length of a single string argument. 512, not kSTEP's 4096: the longest legitimate input
 * in this module is a parameter/component/verb name (well under 60 characters in practice).
 */
const val MAX_STRING_FIELD_LENGTH = 512

/** Max. characters of a caller-supplied value ever echoed back into a response or a log line. */
const val MAX_ECHOED_VALUE_LENGTH = 64

/** Max. number of "did you mean" suggestions returned in an `unknown_parameter` error. */
const val MAX_SUGGESTIONS = 5

private val logger = ktrizMcpLogger {}

/**
 * Thrown for a decoded-but-still-invalid tool argument (wrong JSON shape, oversized field,
 * out-of-range value). Caught by [mcpToolCall] and routed into a [malformedInputError] --
 * the one place this module hand-writes a caller-facing message instead of forwarding a raw
 * exception message.
 */
class ToolInputException(
    message: String,
) : RuntimeException(message)

/**
 * Truncates a caller-supplied value to [MAX_ECHOED_VALUE_LENGTH] with a trailing "..." marker.
 * Every value this module echoes back into a response or a log line goes through this first --
 * without it, an attacker-controlled oversized string would be amplified back out verbatim.
 */
fun echoSafe(value: String): String {
    if (value.length <= MAX_ECHOED_VALUE_LENGTH) return value
    return value.take(MAX_ECHOED_VALUE_LENGTH) + "..."
}

/** @throws ToolInputException if [value] exceeds [MAX_STRING_FIELD_LENGTH]. */
fun requireBoundedString(
    field: String,
    value: String,
) {
    if (value.length > MAX_STRING_FIELD_LENGTH) {
        throw ToolInputException(
            "field '$field' exceeds the maximum allowed length of $MAX_STRING_FIELD_LENGTH characters",
        )
    }
}

/**
 * Runs [block], routing every exception tool-specific logic doesn't itself catch into one of this
 * file's structured [CallToolResult] error shapes instead of letting it reach the SDK's own
 * `Server.handleCallTool` catch -- which would interpolate a raw `e.message` into the response
 * sent back to the (untrusted) MCP caller. [CancellationException] is rethrown, never swallowed.
 *
 * Deliberately not `suspend`: every handler in this module is a plain, synchronous function over
 * enum lookups and map access -- there is nothing to suspend on, and keeping this synchronous is
 * what lets tests call handlers directly without `runBlocking`. The `addTool` registration lambda
 * (which *is* `suspend`, per the SDK's `Server.addTool` signature) simply calls into a handler
 * built on top of this function.
 */
fun mcpToolCall(
    toolName: String,
    block: () -> CallToolResult,
): CallToolResult {
    val result =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ToolInputException) {
            malformedInputError(toolName, e.message ?: "invalid arguments")
        } catch (e: Exception) {
            internalError(e::class.simpleName)
        }
    logToolOutcome(toolName, result)
    return result
}

// Reuses the errorKind this file's own *Error() builders already put into structuredContent,
// rather than a second parallel taxonomy. internal_error (an unexpected, unhandled exception) is
// ERROR -- an operator-actionable anomaly; every other structured error is WARN -- caller-driven
// and expected, but worth seeing; a clean result is DEBUG. Never logs a raw caller value or a raw
// exception message -- only toolName and the already-sanitized errorKind the response itself
// carries, so nothing reaches the log that the response wasn't already safe to send to the caller.
private fun logToolOutcome(
    toolName: String,
    result: CallToolResult,
) {
    if (result.isError != true) {
        logger.debug { "tool call succeeded: tool=$toolName" }
        return
    }
    val errorKind =
        result.structuredContent
            ?.get("errorKind")
            ?.jsonPrimitive
            ?.content
    if (errorKind == "internal_error") {
        logger.error { "tool call failed: tool=$toolName errorKind=$errorKind" }
    } else {
        logger.warn { "tool call failed: tool=$toolName errorKind=$errorKind" }
    }
}

fun malformedInputError(
    toolName: String,
    message: String,
): CallToolResult =
    CallToolResult(
        content = listOf(TextContent(text = "Malformed input for tool '$toolName': $message")),
        isError = true,
        structuredContent =
            buildJsonObject {
                put("errorKind", "malformed_input")
                put("message", message)
            },
    )

/**
 * A parameter contradicting itself -- the one `ktriz-core` runtime check ([dev.ktriz.core.Contradiction]'s
 * `init` block). [message] is [dev.ktriz.core.Contradiction]'s own hand-written require() text, safe to
 * forward verbatim (unlike every other exception message in this module): it is authored in
 * `ktriz-core`, not derived from a stack trace, and does not embed caller input beyond the
 * parameter's own public label.
 */
fun selfContradictionError(message: String?): CallToolResult =
    CallToolResult(
        content = listOf(TextContent(text = message ?: "A parameter cannot contradict itself.")),
        isError = true,
        structuredContent =
            buildJsonObject {
                put("errorKind", "self_contradiction")
                put("message", message ?: "A parameter cannot contradict itself.")
            },
    )

fun internalError(exceptionClassName: String?): CallToolResult =
    CallToolResult(
        content =
            listOf(
                TextContent(
                    text = "Internal error while executing tool" + (exceptionClassName?.let { " ($it)" } ?: ""),
                ),
            ),
        isError = true,
        structuredContent =
            buildJsonObject {
                put("errorKind", "internal_error")
                put("message", exceptionClassName ?: "unknown")
            },
    )

/**
 * Builds one structured error response out of one-or-more [ParameterResolution] problems (never
 * [ParameterResolution.Resolved]). Both `improving` and `worsening` are resolved before either
 * error is reported (OF-5, "gathered" variant): a caller who gets both wrong sees both problems
 * in a single round trip instead of fixing one, calling again, and hitting the second. The
 * top-level `errorKind` is the first problem's kind, for callers that only branch on that field;
 * the full detail lives in the `errors` array.
 */
fun parameterResolutionErrors(
    toolName: String,
    problems: List<ParameterResolution>,
): CallToolResult {
    require(problems.isNotEmpty()) { "parameterResolutionErrors requires at least one problem" }
    require(problems.none { it is ParameterResolution.Resolved }) {
        "parameterResolutionErrors must not be called with a Resolved entry"
    }
    val errorObjects = problems.map { it.toErrorJson() }
    val firstKind = errorObjects.first()["errorKind"]!!.jsonPrimitive.content
    val summary = problems.joinToString("; ") { it.toSummaryText() }
    return CallToolResult(
        content = listOf(TextContent(text = "Invalid input for tool '$toolName': $summary")),
        isError = true,
        structuredContent =
            buildJsonObject {
                put("errorKind", firstKind)
                put("errors", buildJsonArray { errorObjects.forEach { add(it) } })
            },
    )
}

private fun ParameterResolution.toSummaryText(): String =
    when (this) {
        is ParameterResolution.Missing -> "field '$field' is required"
        is ParameterResolution.Malformed -> "field '$field': $reason"
        is ParameterResolution.Unknown -> "field '$field': unknown parameter '${echoSafe(rawValue)}'"
        is ParameterResolution.Resolved -> error("Resolved is not a problem")
    }

private fun ParameterResolution.toErrorJson() =
    when (this) {
        is ParameterResolution.Missing ->
            buildJsonObject {
                put("errorKind", "malformed_input")
                put("field", field)
                put("message", "field '$field' is required")
            }
        is ParameterResolution.Malformed ->
            buildJsonObject {
                put("errorKind", "malformed_input")
                put("field", field)
                put("message", reason)
            }
        is ParameterResolution.Unknown ->
            buildJsonObject {
                put("errorKind", "unknown_parameter")
                put("field", field)
                put("value", echoSafe(rawValue))
                put("suggestions", buildJsonArray { suggestions.forEach { add(it.toJson()) } })
                put(
                    "hint",
                    "Call list_engineering_parameters for the full catalogue of the 39 valid parameters " +
                        "(valid ids: 1..39).",
                )
            }
        is ParameterResolution.Resolved -> error("Resolved is not a problem")
    }
