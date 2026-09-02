package dev.ktriz.mcp.tools

import dev.ktriz.function.Component
import dev.ktriz.mcp.ToolInputException
import dev.ktriz.mcp.echoSafe
import dev.ktriz.mcp.mcpToolCall
import dev.ktriz.mcp.selfContradictionError
import dev.ktriz.mcp.toJson
import dev.ktriz.sufield.FieldType
import dev.ktriz.sufield.SuField
import dev.ktriz.sufield.SuFieldQuality
import dev.ktriz.sufield.suField
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

private const val TOOL_NAME = "build_su_field"

private val VALID_FIELD_TYPES = FieldType.entries.joinToString(", ") { it.name }
private val VALID_SU_FIELD_QUALITIES = SuFieldQuality.entries.joinToString(", ") { it.name }

private val INPUT_SCHEMA =
    ToolSchema(
        properties =
            buildJsonObject {
                putJsonObject("s1") {
                    put("description", "Name of the object substance S1 (required).")
                    put("type", "string")
                }
                putJsonObject("s2") {
                    put("description", "Name of the second substance S2 acting on S1 (optional).")
                    put("type", "string")
                }
                putJsonObject("fieldType") {
                    put("description", "One of $VALID_FIELD_TYPES (case-insensitive). Optional.")
                    put("type", "string")
                }
                putJsonObject("quality") {
                    put(
                        "description",
                        "Overall judgement of the triangle: one of $VALID_SU_FIELD_QUALITIES " +
                            "(case-insensitive). Required.",
                    )
                    put("type", "string")
                }
            },
        required = listOf("s1", "quality"),
    )

private fun asOptionalNonBlankString(
    element: JsonElement?,
    field: String,
): String? {
    if (element == null || element is JsonNull) return null
    val primitive = element as? JsonPrimitive
    if (primitive == null || !primitive.isString) {
        throw ToolInputException("field '$field' must be a string")
    }
    requireNonBlank(field, primitive.content)
    requireBoundedName(field, primitive.content)
    return primitive.content
}

private fun asRequiredNonBlankString(
    element: JsonElement?,
    field: String,
): String = asOptionalNonBlankString(element, field) ?: throw ToolInputException("field '$field' is required")

private fun resolveFieldType(raw: String): FieldType =
    FieldType.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        ?: throw ToolInputException(
            "field 'fieldType' has invalid value '${echoSafe(raw)}' -- must be one of $VALID_FIELD_TYPES",
        )

private fun resolveSuFieldQuality(raw: String): SuFieldQuality =
    SuFieldQuality.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        ?: throw ToolInputException(
            "field 'quality' has invalid value '${echoSafe(raw)}' -- must be one of $VALID_SU_FIELD_QUALITIES",
        )

private fun Component.toJson(): JsonObject = buildJsonObject { put("name", name) }

private fun suFieldKotlinSnippet(model: SuField): String {
    val lines = mutableListOf("suField {")
    lines += "    s1(component(${model.s1.name.kotlinStringLiteral()}))"
    model.s2?.let { lines += "    s2(component(${it.name.kotlinStringLiteral()}))" }
    model.field?.let { lines += "    field(FieldType.${it.name})" }
    lines += "    quality(SuFieldQuality.${model.quality.name})"
    lines += "}"
    return lines.joinToString("\n")
}

/** Pure handler -- directly unit-testable, no server/transport involved. */
fun buildSuFieldHandler(arguments: JsonObject?): CallToolResult =
    mcpToolCall(TOOL_NAME) {
        val s1Name = asRequiredNonBlankString(arguments?.get("s1"), "s1")
        val s2Name = asOptionalNonBlankString(arguments?.get("s2"), "s2")
        val fieldTypeRaw = asOptionalNonBlankString(arguments?.get("fieldType"), "fieldType")
        val qualityRaw = asRequiredNonBlankString(arguments?.get("quality"), "quality")

        val fieldType = fieldTypeRaw?.let { resolveFieldType(it) }
        val quality = resolveSuFieldQuality(qualityRaw)

        // SuField's init block enforces quality/s2/field consistency (see its KDoc) -- that
        // check is the one authoritative location for this rule, so it's surfaced here as a
        // structured self_contradiction error rather than duplicated as a second, redundant
        // check in this module.
        val model =
            try {
                suField {
                    s1(component(s1Name))
                    s2Name?.let { s2(component(it)) }
                    fieldType?.let { field(it) }
                    quality(quality)
                }
            } catch (e: IllegalArgumentException) {
                return@mcpToolCall selfContradictionError(e.message)
            }

        val summaryText =
            buildString {
                append("Su-Field model: s1='${model.s1.name}'")
                model.s2?.let { append(", s2='${it.name}'") }
                model.field?.let { append(", field=${it.name}") }
                append(", quality=${model.quality.name}.")
            }

        CallToolResult(
            content = listOf(TextContent(text = summaryText)),
            structuredContent =
                buildJsonObject {
                    put("s1", model.s1.toJson())
                    put("s2", model.s2?.toJson() ?: JsonNull)
                    put("fieldType", model.field?.toJson() ?: JsonNull)
                    put("quality", model.quality.name)
                    put("kotlin", suFieldKotlinSnippet(model))
                },
        )
    }

fun registerBuildSuFieldTool(server: Server) {
    server.addTool(
        name = TOOL_NAME,
        description =
            "Builds and validates a TRIZ substance-field (Su-Field) model (S1 + optional S2/field, judged " +
                "by an overall quality) without saving or rendering it -- returns the normalized model plus " +
                "an equivalent, directly compilable Kotlin `suField { }` snippet. 's2' and 'fieldType' may " +
                "both be omitted for an incomplete triangle (classical Su-Field analysis often starts from " +
                "just S1). 'quality' must be one of $VALID_SU_FIELD_QUALITIES (case-insensitive): " +
                "INCOMPLETE requires at least one of s2/fieldType to be absent, every other value requires " +
                "both to be present -- a mismatch returns a structured self_contradiction error naming which " +
                "slot is missing or unexpectedly present, not a crash. 'fieldType', if given, must be one of " +
                "$VALID_FIELD_TYPES (case-insensitive) -- call list_field_types first if unsure. There is " +
                "deliberately no matching render tool here (consistent with build_function_model, which " +
                "likewise has no MCP-exposed rendering pendant) -- SVG rendering of a Su-Field triangle is " +
                "only reachable via ktriz-script/ktriz-cli.",
        inputSchema = INPUT_SCHEMA,
    ) { request ->
        buildSuFieldHandler(request.arguments)
    }
}
