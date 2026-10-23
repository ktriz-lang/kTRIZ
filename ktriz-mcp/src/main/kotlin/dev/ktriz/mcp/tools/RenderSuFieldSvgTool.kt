package dev.ktriz.mcp.tools

import dev.ktriz.mcp.MAX_SVG_OUTPUT_CHARS
import dev.ktriz.mcp.RENDER_TIMEOUT_MS
import dev.ktriz.mcp.RenderOutcome
import dev.ktriz.mcp.guardedRender
import dev.ktriz.mcp.mcpToolCall
import dev.ktriz.mcp.renderBusyError
import dev.ktriz.mcp.renderOutputTooLargeError
import dev.ktriz.mcp.renderTimeoutError
import dev.ktriz.render.kuml.renderSvg
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TOOL_NAME = "render_su_field_svg"

/**
 * Pure handler -- directly unit-testable, no server/transport involved.
 *
 * Escaping is entirely the renderer's job (`xmlEscapeText`/`xmlEscapeAttr` in ktriz-render-kuml);
 * this layer deliberately never pre-escapes, which would double-escape.
 */
fun renderSuFieldSvgHandler(
    arguments: JsonObject?,
    maxSvgChars: Int = MAX_SVG_OUTPUT_CHARS,
): CallToolResult =
    mcpToolCall(TOOL_NAME) {
        val model =
            when (val parsed = parseSuFieldArguments(arguments)) {
                is SuFieldParse.Built -> parsed.model
                is SuFieldParse.Rejected -> return@mcpToolCall parsed.error
            }
        // The Su-Field geometry is fixed (no ELK), but AWT font measurement still runs; the guard
        // keeps the behaviour uniform across both render tools.
        val svg =
            when (val outcome = guardedRender { model.renderSvg() }) {
                is RenderOutcome.Ok -> outcome.svg
                RenderOutcome.TimedOut -> return@mcpToolCall renderTimeoutError(TOOL_NAME, RENDER_TIMEOUT_MS)
                RenderOutcome.Busy -> return@mcpToolCall renderBusyError(TOOL_NAME)
            }
        if (svg.length > maxSvgChars) {
            return@mcpToolCall renderOutputTooLargeError(TOOL_NAME, maxSvgChars)
        }
        CallToolResult(
            content = listOf(TextContent(text = svg)),
            structuredContent =
                buildJsonObject {
                    put("format", "svg")
                    put("svg", svg)
                    put("s1", model.s1.name)
                    put("s2", model.s2?.name?.let(::JsonPrimitive) ?: JsonNull)
                    put("fieldType", model.field?.name?.let(::JsonPrimitive) ?: JsonNull)
                    put("quality", model.quality.name)
                },
        )
    }

fun registerRenderSuFieldSvgTool(server: Server) {
    server.addTool(
        name = TOOL_NAME,
        description =
            "Renders a TRIZ substance-field (Su-Field) triangle as a standalone SVG document. Takes the " +
                "same arguments as build_su_field (s1, optional s2 and fieldType, quality) and validates " +
                "them the same way -- an inconsistent quality/s2/field combination returns a structured " +
                "self_contradiction error and no SVG. Returns the SVG as text content and again in " +
                "structuredContent.svg (format 'svg'). All names are XML-escaped by the renderer, so " +
                "arbitrary names cannot inject markup. Rendering runs under a ${RENDER_TIMEOUT_MS / 1000}s " +
                "deadline with at most 2 concurrent renders; failures are structured render_timeout / " +
                "render_busy / render_output_too_large errors. Use build_su_field for validation only.",
        inputSchema = SU_FIELD_INPUT_SCHEMA,
    ) { request ->
        // Off the protocol dispatcher: guardedRender blocks its caller for up to the render deadline.
        withContext(Dispatchers.IO) { renderSuFieldSvgHandler(request.arguments) }
    }
}
