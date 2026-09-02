package dev.ktriz.mcp.tools

import dev.ktriz.mcp.mcpToolCall
import dev.ktriz.mcp.toJson
import dev.ktriz.sufield.StandardSolutionClass
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TOOL_NAME = "list_standard_solution_classes"

private val INPUT_SCHEMA = ToolSchema(required = emptyList())

/** Pure handler -- directly unit-testable, no server/transport involved. */
fun listStandardSolutionClassesHandler(
    @Suppress("UNUSED_PARAMETER") arguments: JsonObject?,
): CallToolResult =
    mcpToolCall(TOOL_NAME) {
        val classes = StandardSolutionClass.entries.sortedBy { it.id }
        val text =
            classes.joinToString("\n") { c ->
                "#${c.id}  ${c.name} (${c.solutionCount} solutions) — ${c.labelEn} / ${c.labelDe}: ${c.description}"
            }
        CallToolResult(
            content = listOf(TextContent(text = text)),
            structuredContent =
                buildJsonObject {
                    put("count", classes.size)
                    put("classes", buildJsonArray { classes.forEach { add(it.toJson()) } })
                },
        )
    }

fun registerListStandardSolutionClassesTool(server: Server) {
    server.addTool(
        name = TOOL_NAME,
        description =
            "Lists the 5-class structure of Altshuller's 76 TRIZ standard solutions for Su-Field analysis, " +
                "with `id`, exact Kotlin enum `symbol`, English/German label, a one-sentence description, " +
                "and `solutionCount` (how many of the 76 individual solutions belong to that class -- " +
                "13/23/6/17/17, summing to 76). kTRIZ bundles only this coarse classification, never the " +
                "76 individual solution texts (no trustworthy primary source found -- see " +
                "docs/standard-solutions-provenance.adoc). There is no automatic mapping from a " +
                "SuFieldQuality to a class -- that correspondence is informal, not a documented 1:1 rule in " +
                "the TRIZ literature, so this tool never guesses one; reason about it explicitly yourself. " +
                "Takes no parameters; unrecognized arguments are ignored, not rejected.",
        inputSchema = INPUT_SCHEMA,
    ) { request ->
        listStandardSolutionClassesHandler(request.arguments)
    }
}
