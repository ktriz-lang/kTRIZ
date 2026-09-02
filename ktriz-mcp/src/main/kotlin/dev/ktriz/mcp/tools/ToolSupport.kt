package dev.ktriz.mcp.tools

import dev.ktriz.mcp.ToolInputException

/** Well under 512 (a name/verb is never legitimately anywhere near that long); named separately
 *  from [dev.ktriz.mcp.MAX_STRING_FIELD_LENGTH] because a component name or verb has its own,
 *  tighter, natural upper bound. Shared across every tool in this package -- see this file's
 *  own KDoc-less rationale in the git history: `private` in Kotlin is file-local, not
 *  package-local, so a helper meant to be reused across `*Tool.kt` files has to live in a file
 *  of its own rather than being duplicated. */
const val MAX_NAME_LENGTH = 200

/** @throws ToolInputException if [value] exceeds [MAX_NAME_LENGTH]. */
internal fun requireBoundedName(
    field: String,
    value: String,
) {
    if (value.length > MAX_NAME_LENGTH) {
        throw ToolInputException("field '$field' exceeds the maximum allowed length of $MAX_NAME_LENGTH characters")
    }
}

/** @throws ToolInputException if [value] is blank -- shared by every tool's string-field input
 *  path so a name/verb rejected as meaningless via one field can't slip through another (OF-6
 *  initially only fixed this for `build_function_model`'s `edges`, leaving `components`
 *  accepting `[""]`/`["   "]` while the exact same string was rejected coming through
 *  `edges[].from`). */
internal fun requireNonBlank(
    field: String,
    value: String,
) {
    if (value.isBlank()) {
        throw ToolInputException("field '$field' must not be blank")
    }
}

/** Escapes [this] into a Kotlin string-template literal: backslash and quote first (so later
 *  escaping never doubles up the ones just inserted), then `$` (template-interpolation trigger --
 *  the SDK's own commit message promises a "directly compilable" snippet, which a component named
 *  e.g. `"Cost $total"` would otherwise silently break, either as a compile error or as a
 *  wrongly-interpolated string), then the control characters that would otherwise land as literal
 *  bytes inside the source and either break compilation (a raw newline mid-literal) or round-trip
 *  wrong (raw tab/CR). */
internal fun String.kotlinStringLiteral(): String =
    buildString {
        append('"')
        for (c in this@kotlinStringLiteral) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '$' -> append("\\$")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
        append('"')
    }
