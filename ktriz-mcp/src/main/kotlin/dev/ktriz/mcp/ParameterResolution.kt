package dev.ktriz.mcp

import dev.ktriz.core.EngineeringParameter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The outcome of resolving one `improving`/`worsening` tool argument against
 * [EngineeringParameter]. Deliberately not a single nullable result: [resolveParameter]'s callers
 * (currently only `resolve_contradiction`) need to distinguish *why* a value didn't resolve --
 * "you forgot it" (missing), "wrong JSON shape or out of bounds" (malformed), and "well-formed but
 * not a real parameter" (unknown, which alone carries "did you mean" suggestions) get different
 * response shapes.
 */
sealed interface ParameterResolution {
    data class Resolved(
        val parameter: EngineeringParameter,
    ) : ParameterResolution

    data class Missing(
        val field: String,
    ) : ParameterResolution

    data class Malformed(
        val field: String,
        val reason: String,
    ) : ParameterResolution

    data class Unknown(
        val field: String,
        val rawValue: String,
        val suggestions: List<EngineeringParameter>,
    ) : ParameterResolution
}

/**
 * Resolves one [field]'s raw JSON [value] against [EngineeringParameter]. Accepts, in this order
 * (first match wins, deterministic):
 *  - a numeric id, `1..39`, either as a JSON integer or a numeric string (`"14"`);
 *  - the exact Kotlin enum symbol, case-insensitive, with spaces/hyphens accepted in place of
 *    underscores and apostrophes ignored (`"stability of the object's composition"` resolves
 *    [EngineeringParameter.STABILITY_OF_THE_OBJECTS_COMPOSITION] -- see [normalizeParameterKey]);
 *  - `labelEn` or `labelDe`, same normalization.
 *
 * Not generalized over [dev.ktriz.core.InventivePrinciple] on purpose: nothing in this wave ever
 * takes a principle as *input* (only as output, from a matrix lookup), so there is nothing yet to
 * generalize against. Generalize this into a type-parameterized resolver only once a later wave
 * actually needs to resolve a principle from caller-supplied text.
 */
fun resolveParameter(
    field: String,
    value: JsonElement?,
): ParameterResolution {
    if (value == null || value is JsonNull) return ParameterResolution.Missing(field)
    if (value !is JsonPrimitive) {
        return ParameterResolution.Malformed(field, "expected a string or integer, got ${describeJsonShape(value)}")
    }
    if (value.isString) {
        val raw = value.content
        if (raw.length > MAX_STRING_FIELD_LENGTH) {
            return ParameterResolution.Malformed(
                field,
                "value exceeds the maximum allowed length of $MAX_STRING_FIELD_LENGTH characters",
            )
        }
        val asId = raw.trim().toIntOrNull()
        return if (asId != null) resolveById(field, asId, raw) else resolveBySymbolOrLabel(field, raw)
    }
    val asId = value.intOrNull
    if (asId != null) return resolveById(field, asId, value.content)
    val looksNumeric = value.content.toDoubleOrNull() != null
    return ParameterResolution.Malformed(
        field,
        if (looksNumeric) {
            "expected an integer id, got a non-integer number '${echoSafe(value.content)}'"
        } else {
            "expected a string or integer, got a boolean"
        },
    )
}

private fun describeJsonShape(value: JsonElement): String =
    when (value) {
        is JsonArray -> "an array"
        is JsonObject -> "an object"
        else -> value::class.simpleName ?: "an unsupported value"
    }

private fun resolveById(
    field: String,
    id: Int,
    raw: String,
): ParameterResolution {
    if (id < 1 || id > EngineeringParameter.entries.size) {
        return ParameterResolution.Unknown(field, raw, suggestParameters(normalizeParameterKey(raw)))
    }
    return ParameterResolution.Resolved(EngineeringParameter.ofId(id))
}

private fun resolveBySymbolOrLabel(
    field: String,
    raw: String,
): ParameterResolution {
    val normalized = normalizeParameterKey(raw)
    val bySymbol = EngineeringParameter.entries.firstOrNull { it.name.lowercase() == normalized }
    if (bySymbol != null) return ParameterResolution.Resolved(bySymbol)
    val byLabel =
        EngineeringParameter.entries.firstOrNull {
            normalizeParameterKey(it.labelEn) == normalized || normalizeParameterKey(it.labelDe) == normalized
        }
    if (byLabel != null) return ParameterResolution.Resolved(byLabel)
    return ParameterResolution.Unknown(field, raw, suggestParameters(normalized))
}

/**
 * Normalizes free-text parameter input for comparison: trim, lowercase, apostrophes stripped,
 * runs of whitespace/hyphens collapsed to a single underscore. [String.lowercase] (locale-
 * invariant), never `toLowerCase(Locale.getDefault())` -- the latter breaks under a Turkish
 * default locale (`I` maps to `ı`, not `i`), which would silently break symbol resolution for
 * every parameter whose name contains an `I`.
 */
internal fun normalizeParameterKey(raw: String): String =
    raw
        .trim()
        .lowercase()
        .replace("'", "")
        .replace("’", "") // typographic right single quotation mark
        .replace(Regex("[-\\s]+"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')

/**
 * Up to [MAX_SUGGESTIONS] "did you mean" candidates for an unresolved [normalizedInput], stably
 * ordered by [EngineeringParameter.id]. Substring matches (normalized, either direction against
 * the symbol or the English label) come first; only if none exist does this fall back to a
 * Levenshtein distance of at most 5, ranked by distance then id.
 *
 * Levenshtein is computed only when [normalizedInput] is at most 64 characters -- a DoS guard:
 * without it, a caller-controlled string near the (much larger) [MAX_STRING_FIELD_LENGTH] limit
 * would force 39 quadratic-cost comparisons per call for free.
 */
internal fun suggestParameters(normalizedInput: String): List<EngineeringParameter> {
    if (normalizedInput.isBlank()) return emptyList()
    val substringMatches =
        EngineeringParameter.entries.filter { p ->
            val symbolKey = p.name.lowercase()
            val labelKey = normalizeParameterKey(p.labelEn)
            symbolKey.contains(normalizedInput) ||
                normalizedInput.contains(symbolKey) ||
                labelKey.contains(normalizedInput) ||
                normalizedInput.contains(labelKey)
        }
    if (substringMatches.isNotEmpty()) {
        return substringMatches.sortedBy { it.id }.take(MAX_SUGGESTIONS)
    }
    if (normalizedInput.length > 64) return emptyList()
    return EngineeringParameter.entries
        .map { it to levenshtein(it.name.lowercase(), normalizedInput) }
        .filter { (_, distance) -> distance <= 5 }
        .sortedWith(compareBy({ it.second }, { it.first.id }))
        .take(MAX_SUGGESTIONS)
        .map { it.first }
}

private fun levenshtein(
    a: String,
    b: String,
): Int {
    val dp = Array(a.length + 1) { IntArray(b.length + 1) }
    for (i in 0..a.length) dp[i][0] = i
    for (j in 0..b.length) dp[0][j] = j
    for (i in 1..a.length) {
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
        }
    }
    return dp[a.length][b.length]
}
