package dev.ktriz.mcp.tools

import dev.ktriz.mcp.mcpToolCall
import dev.ktriz.mcp.toJson
import dev.ktriz.sufield.FieldType
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TOOL_NAME = "list_field_types"

private val INPUT_SCHEMA = ToolSchema(required = emptyList())

/** Pure handler -- directly unit-testable, no server/transport involved. */
fun listFieldTypesHandler(
    @Suppress("UNUSED_PARAMETER") arguments: JsonObject?,
): CallToolResult =
    mcpToolCall(TOOL_NAME) {
        val fieldTypes = FieldType.entries.sortedBy { it.id }
        val text =
            fieldTypes.joinToString("\n") { f ->
                "#${f.id}  ${f.name} (F${f.abbreviation}) — ${f.labelEn} / ${f.labelDe}"
            }
        CallToolResult(
            content = listOf(TextContent(text = text)),
            structuredContent =
                buildJsonObject {
                    put("count", fieldTypes.size)
                    put("fieldTypes", buildJsonArray { fieldTypes.forEach { add(it.toJson()) } })
                },
        )
    }

fun registerListFieldTypesTool(server: Server) {
    server.addTool(
        name = TOOL_NAME,
        description =
            "Lists all 6 classical Su-Field field types (Altshuller) with their exact Kotlin enum symbol, " +
                "English label, German label, and classical notation abbreviation (e.g. 'Mec' for FMec). " +
                "Use `symbol` verbatim as `FieldType.<symbol>` -- a symbol not in this list will not compile " +
                "and build_su_field's 'fieldType' argument will reject it. Deliberately just the classical " +
                "six, not the later MATCEMIB eight-field extension. Call this before build_su_field with an " +
                "unfamiliar field type name, instead of guessing. Takes no parameters; unrecognized " +
                "arguments are ignored, not rejected.",
        inputSchema = INPUT_SCHEMA,
    ) { request ->
        listFieldTypesHandler(request.arguments)
    }
}
