package dev.ktriz.mcp.tools

import dev.ktriz.core.EngineeringParameter
import dev.ktriz.mcp.mcpToolCall
import dev.ktriz.mcp.toJson
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TOOL_NAME = "list_engineering_parameters"

private val INPUT_SCHEMA = ToolSchema(required = emptyList())

/** Pure handler -- directly unit-testable, no server/transport involved. */
fun listEngineeringParametersHandler(
    @Suppress("UNUSED_PARAMETER") arguments: JsonObject?,
): CallToolResult =
    mcpToolCall(TOOL_NAME) {
        val parameters = EngineeringParameter.entries.sortedBy { it.id }
        val text =
            parameters.joinToString("\n") { p ->
                "#${p.id}  ${p.name} — ${p.labelEn} / ${p.labelDe}"
            }
        CallToolResult(
            content = listOf(TextContent(text = text)),
            structuredContent =
                buildJsonObject {
                    put("count", parameters.size)
                    put("parameters", buildJsonArray { parameters.forEach { add(it.toJson()) } })
                },
        )
    }

fun registerListEngineeringParametersTool(server: Server) {
    server.addTool(
        name = TOOL_NAME,
        description =
            "Lists all 39 classical TRIZ engineering parameters (Altshuller) with their exact Kotlin enum " +
                "symbol, English label, and German label. Use the `symbol` field verbatim as " +
                "`EngineeringParameter.<symbol>` in generated Kotlin code -- a symbol not in this list will " +
                "not compile. Call this before calling resolve_contradiction with an unfamiliar parameter " +
                "name, instead of guessing one from memory. Takes no parameters; unrecognized arguments are " +
                "ignored, not rejected.",
        inputSchema = INPUT_SCHEMA,
    ) { request ->
        listEngineeringParametersHandler(request.arguments)
    }
}
