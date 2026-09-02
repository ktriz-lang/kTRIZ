package dev.ktriz.mcp.tools

import dev.ktriz.core.InventivePrinciple
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

private const val TOOL_NAME = "list_inventive_principles"

private val INPUT_SCHEMA = ToolSchema(required = emptyList())

/** Pure handler -- directly unit-testable, no server/transport involved. */
fun listInventivePrinciplesHandler(
    @Suppress("UNUSED_PARAMETER") arguments: JsonObject?,
): CallToolResult =
    mcpToolCall(TOOL_NAME) {
        val principles = InventivePrinciple.entries.sortedBy { it.id }
        val text =
            principles.joinToString("\n") { p ->
                "#${p.id}  ${p.name} — ${p.labelEn} / ${p.labelDe}"
            }
        CallToolResult(
            content = listOf(TextContent(text = text)),
            structuredContent =
                buildJsonObject {
                    put("count", principles.size)
                    put("principles", buildJsonArray { principles.forEach { add(it.toJson()) } })
                },
        )
    }

fun registerListInventivePrinciplesTool(server: Server) {
    server.addTool(
        name = TOOL_NAME,
        description =
            "Lists all 40 classical TRIZ inventive principles (Altshuller) with their exact Kotlin enum " +
                "symbol, English label, and German label. resolve_contradiction already returns each " +
                "recommended principle's `symbol`, so this tool is mainly for browsing the full catalogue " +
                "independent of any specific contradiction. Takes no parameters; unrecognized arguments are " +
                "ignored, not rejected.",
        inputSchema = INPUT_SCHEMA,
    ) { request ->
        listInventivePrinciplesHandler(request.arguments)
    }
}
