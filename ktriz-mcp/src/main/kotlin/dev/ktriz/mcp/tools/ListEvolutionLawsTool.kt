package dev.ktriz.mcp.tools

import dev.ktriz.evolution.EvolutionLaw
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

private const val TOOL_NAME = "list_evolution_laws"

private val INPUT_SCHEMA = ToolSchema(required = emptyList())

private const val SCOPE_NOTE =
    "Only the eight classical laws, grouped statics/kinematics/dynamics. No evolution lines, patterns " +
        "or TESE trends are modelled. Labels are English only."

/** Pure handler -- directly unit-testable, no server/transport involved. */
fun listEvolutionLawsHandler(
    @Suppress("UNUSED_PARAMETER") arguments: JsonObject?,
): CallToolResult =
    mcpToolCall(TOOL_NAME) {
        val laws = EvolutionLaw.entries.sortedBy { it.id }
        val text =
            laws.joinToString("\n") { l -> "#${l.id}  ${l.name} — ${l.labelEn} [${l.group.name}]" } +
                "\n$SCOPE_NOTE"
        CallToolResult(
            content = listOf(TextContent(text = text)),
            structuredContent =
                buildJsonObject {
                    put("count", laws.size)
                    put("evolutionLaws", buildJsonArray { laws.forEach { add(it.toJson()) } })
                    put("scope", "classical_laws_only")
                    put("note", SCOPE_NOTE)
                },
        )
    }

fun registerListEvolutionLawsTool(server: Server) {
    server.addTool(
        name = TOOL_NAME,
        description =
            "Lists the 8 classical laws of technical systems evolution (statics, kinematics, dynamics) with " +
                "exact Kotlin enum symbol, English label, group and a short description. Use `symbol` " +
                "verbatim as `EvolutionLaw.<symbol>`. $SCOPE_NOTE Takes no parameters; unrecognized " +
                "arguments are ignored, not rejected.",
        inputSchema = INPUT_SCHEMA,
    ) { request ->
        listEvolutionLawsHandler(request.arguments)
    }
}
