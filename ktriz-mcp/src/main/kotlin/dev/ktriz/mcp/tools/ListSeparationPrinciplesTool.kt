package dev.ktriz.mcp.tools

import dev.ktriz.mcp.mcpToolCall
import dev.ktriz.mcp.toJson
import dev.ktriz.physical.SeparationPrinciple
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TOOL_NAME = "list_separation_principles"

private val INPUT_SCHEMA = ToolSchema(required = emptyList())

private const val MAPPING_NOTE =
    "No mapping from separation principles to the 40 inventive principles is bundled: only one freely " +
        "available source gave such lists, so the two-independent-sources rule was not met."

/** Pure handler -- directly unit-testable, no server/transport involved. */
fun listSeparationPrinciplesHandler(
    @Suppress("UNUSED_PARAMETER") arguments: JsonObject?,
): CallToolResult =
    mcpToolCall(TOOL_NAME) {
        val principles = SeparationPrinciple.entries.sortedBy { it.id }
        val text =
            principles.joinToString("\n") { p -> "#${p.id}  ${p.name} — ${p.labelEn} / ${p.labelDe}" }
        CallToolResult(
            content = listOf(TextContent(text = text)),
            structuredContent =
                buildJsonObject {
                    put("count", principles.size)
                    put("separationPrinciples", buildJsonArray { principles.forEach { add(it.toJson()) } })
                    put("inventivePrincipleMapping", "none")
                    put("note", MAPPING_NOTE)
                },
        )
    }

fun registerListSeparationPrinciplesTool(server: Server) {
    server.addTool(
        name = TOOL_NAME,
        description =
            "Lists the 4 classical separation principles (Altshuller: in space, in time, upon condition, on " +
                "system level) with exact Kotlin enum symbol, English and German label. Use `symbol` verbatim " +
                "as `SeparationPrinciple.<symbol>` or as build_physical_contradiction's 'separation' argument. " +
                "Deliberately the classical four, not the 11 strategies of Mann/CREAX. $MAPPING_NOTE Takes no " +
                "parameters; unrecognized arguments are ignored, not rejected.",
        inputSchema = INPUT_SCHEMA,
    ) { request ->
        listSeparationPrinciplesHandler(request.arguments)
    }
}
