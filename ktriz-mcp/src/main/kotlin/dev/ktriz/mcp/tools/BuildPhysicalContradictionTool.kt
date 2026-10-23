package dev.ktriz.mcp.tools

import dev.ktriz.mcp.ToolInputException
import dev.ktriz.mcp.echoSafe
import dev.ktriz.mcp.mcpToolCall
import dev.ktriz.mcp.selfContradictionError
import dev.ktriz.mcp.toJson
import dev.ktriz.physical.MAX_PHYSICAL_REASON_LENGTH
import dev.ktriz.physical.MAX_PHYSICAL_TEXT_LENGTH
import dev.ktriz.physical.PhysicalContradiction
import dev.ktriz.physical.SeparationPrinciple
import dev.ktriz.physical.physicalContradiction
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

private const val TOOL_NAME = "build_physical_contradiction"

private val VALID_SEPARATIONS = SeparationPrinciple.entries.joinToString(", ") { it.name }

internal val PHYSICAL_CONTRADICTION_INPUT_SCHEMA: ToolSchema =
    ToolSchema(
        properties =
            buildJsonObject {
                putJsonObject("element") {
                    put("description", "Name of the element whose property is contradictory (required).")
                    put("type", "string")
                }
                putJsonObject("property") {
                    put(
                        "description",
                        "The property that must be in two opposite states, e.g. 'Temperature' (required).",
                    )
                    put("type", "string")
                }
                putJsonObject("state") {
                    put("description", "First required state, e.g. 'high' (required).")
                    put("type", "string")
                }
                putJsonObject("oppositeState") {
                    put("description", "Opposite required state, e.g. 'low' (required). Must differ from 'state'.")
                    put("type", "string")
                }
                putJsonObject("stateBecause") {
                    put("description", "Why 'state' is needed (optional).")
                    put("type", "string")
                }
                putJsonObject("oppositeStateBecause") {
                    put("description", "Why 'oppositeState' is needed (optional).")
                    put("type", "string")
                }
                putJsonObject("separation") {
                    put("description", "One of $VALID_SEPARATIONS (case-insensitive). Optional.")
                    put("type", "string")
                }
            },
        required = listOf("element", "property", "state", "oppositeState"),
    )

private fun asOptionalString(
    element: JsonElement?,
    field: String,
    maxLength: Int,
): String? {
    if (element == null || element is JsonNull) return null
    val primitive = element as? JsonPrimitive
    if (primitive == null || !primitive.isString) {
        throw ToolInputException("field '$field' must be a string")
    }
    requireNonBlank(field, primitive.content)
    if (primitive.content.length > maxLength) {
        throw ToolInputException("field '$field' exceeds the maximum allowed length of $maxLength characters")
    }
    return primitive.content
}

private fun asRequiredString(
    element: JsonElement?,
    field: String,
    maxLength: Int,
): String = asOptionalString(element, field, maxLength) ?: throw ToolInputException("field '$field' is required")

private fun resolveSeparation(raw: String): SeparationPrinciple =
    SeparationPrinciple.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
        ?: throw ToolInputException(
            "field 'separation' has invalid value '${echoSafe(raw)}' -- must be one of $VALID_SEPARATIONS",
        )

private fun physicalContradictionKotlinSnippet(model: PhysicalContradiction): String {
    val lines = mutableListOf("physicalContradiction {")
    lines += "    element(${model.element.name.kotlinStringLiteral()})"
    lines += "    property(${model.property.kotlinStringLiteral()})"
    lines += "    state(${model.state.kotlinStringLiteral()}" +
        (model.stateBecause?.let { ", because = ${it.kotlinStringLiteral()}" } ?: "") + ")"
    lines += "    oppositeState(${model.oppositeState.kotlinStringLiteral()}" +
        (model.oppositeStateBecause?.let { ", because = ${it.kotlinStringLiteral()}" } ?: "") + ")"
    model.separation?.let { lines += "    separation(SeparationPrinciple.${it.name})" }
    lines += "}"
    return lines.joinToString("\n")
}

/** Pure handler -- directly unit-testable, no server/transport involved. */
fun buildPhysicalContradictionHandler(arguments: JsonObject?): CallToolResult =
    mcpToolCall(TOOL_NAME) {
        val elementName = asRequiredString(arguments?.get("element"), "element", MAX_PHYSICAL_TEXT_LENGTH)
        val property = asRequiredString(arguments?.get("property"), "property", MAX_PHYSICAL_TEXT_LENGTH)
        val state = asRequiredString(arguments?.get("state"), "state", MAX_PHYSICAL_TEXT_LENGTH)
        val opposite = asRequiredString(arguments?.get("oppositeState"), "oppositeState", MAX_PHYSICAL_TEXT_LENGTH)
        val stateBecause = asOptionalString(arguments?.get("stateBecause"), "stateBecause", MAX_PHYSICAL_REASON_LENGTH)
        val oppositeBecause =
            asOptionalString(arguments?.get("oppositeStateBecause"), "oppositeStateBecause", MAX_PHYSICAL_REASON_LENGTH)
        val separation =
            asOptionalString(arguments?.get("separation"), "separation", MAX_PHYSICAL_TEXT_LENGTH)?.let {
                resolveSeparation(it)
            }

        val model =
            try {
                physicalContradiction {
                    element(elementName)
                    property(property)
                    state(state, because = stateBecause)
                    oppositeState(opposite, because = oppositeBecause)
                    separation?.let { separation(it) }
                }
            } catch (e: IllegalArgumentException) {
                return@mcpToolCall selfContradictionError(e.message)
            }

        val summary =
            "Physical contradiction: element='${echoSafe(model.element.name)}', " +
                "property='${echoSafe(model.property)}', '${echoSafe(model.state)}' vs. " +
                "'${echoSafe(model.oppositeState)}'" +
                (model.separation?.let { ", separation=${it.name}" } ?: "") + "."

        CallToolResult(
            content = listOf(TextContent(text = summary)),
            structuredContent =
                buildJsonObject {
                    putJsonObject("element") { put("name", model.element.name) }
                    put("property", model.property)
                    put("state", model.state)
                    put("oppositeState", model.oppositeState)
                    put("stateBecause", model.stateBecause?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("oppositeStateBecause", model.oppositeStateBecause?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("separation", model.separation?.toJson() ?: JsonNull)
                    put("kotlin", physicalContradictionKotlinSnippet(model))
                },
        )
    }

fun registerBuildPhysicalContradictionTool(server: Server) {
    server.addTool(
        name = TOOL_NAME,
        description =
            "Builds and validates a TRIZ physical contradiction (one property of one element that must be in " +
                "two opposite states, e.g. 'present' vs. 'absent') without saving or rendering it -- returns " +
                "the normalized model plus an equivalent, directly compilable Kotlin `physicalContradiction { }` " +
                "snippet. Only checks that 'state' and 'oppositeState' are different (trimmed, " +
                "case-insensitive), NOT that they are truly opposite -- that judgement stays with the analyst. " +
                "'separation', if given, must be one of $VALID_SEPARATIONS (case-insensitive); call " +
                "list_separation_principles first if unsure. Identical states return a structured " +
                "self_contradiction error. Unrecognized arguments are ignored, not rejected.",
        inputSchema = PHYSICAL_CONTRADICTION_INPUT_SCHEMA,
    ) { request ->
        buildPhysicalContradictionHandler(request.arguments)
    }
}
