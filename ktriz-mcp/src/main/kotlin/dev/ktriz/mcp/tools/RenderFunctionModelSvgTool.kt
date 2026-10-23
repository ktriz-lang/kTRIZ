package dev.ktriz.mcp.tools

import dev.ktriz.function.FunctionModel
import dev.ktriz.mcp.MAX_SVG_OUTPUT_CHARS
import dev.ktriz.mcp.RENDER_TIMEOUT_MS
import dev.ktriz.mcp.RenderOutcome
import dev.ktriz.mcp.ToolInputException
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TOOL_NAME = "render_function_model_svg"

/** Tighter than [MAX_COMPONENTS]: a render runs an ELK layout, not just validation. */
const val MAX_RENDER_COMPONENTS = 60
const val MAX_RENDER_EDGES = 150

/** Cap on the summed length of all component names and edge verbs (layout/label cost). */
const val MAX_RENDER_TOTAL_NAME_CHARS = 20_000

private fun FunctionModel.totalNameChars(): Int = components.sumOf { it.name.length } + edges.sumOf { it.verb.length }

/**
 * Pure handler -- directly unit-testable, no server/transport involved.
 *
 * Escaping is entirely the renderer's job; this layer deliberately never pre-escapes.
 */
fun renderFunctionModelSvgHandler(
    arguments: JsonObject?,
    maxSvgChars: Int = MAX_SVG_OUTPUT_CHARS,
): CallToolResult =
    mcpToolCall(TOOL_NAME) {
        val model = parseFunctionModelArguments(arguments, MAX_RENDER_COMPONENTS, MAX_RENDER_EDGES)

        // Edge endpoints are auto-declared, so 'components' alone does not bound the node count:
        // check the *resulting* model.
        if (model.components.size > MAX_RENDER_COMPONENTS) {
            throw ToolInputException(
                "model has ${model.components.size} components (including edge endpoints), exceeding the " +
                    "maximum of $MAX_RENDER_COMPONENTS for rendering",
            )
        }
        if (model.totalNameChars() > MAX_RENDER_TOTAL_NAME_CHARS) {
            throw ToolInputException(
                "total length of component names and verbs exceeds the maximum of " +
                    "$MAX_RENDER_TOTAL_NAME_CHARS characters for rendering",
            )
        }

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
                    put("componentCount", model.components.size)
                    put("edgeCount", model.edges.size)
                },
        )
    }

fun registerRenderFunctionModelSvgTool(server: Server) {
    server.addTool(
        name = TOOL_NAME,
        description =
            "Renders a TRIZ function model (components + useful/harmful/insufficient/excessive edges) as a " +
                "standalone SVG document using an automatic layout. Takes the same arguments as " +
                "build_function_model (components, edges; endpoints are auto-declared) but with tighter " +
                "limits: at most $MAX_RENDER_COMPONENTS components (counting auto-declared edge endpoints), " +
                "$MAX_RENDER_EDGES edges and $MAX_RENDER_TOTAL_NAME_CHARS characters of names and verbs in " +
                "total -- larger inputs are a structured malformed_input error. Returns the SVG as text " +
                "content and again in structuredContent.svg together with componentCount and edgeCount. " +
                "All names and verbs are XML-escaped by the renderer, so arbitrary text cannot inject " +
                "markup. Rendering runs under a ${RENDER_TIMEOUT_MS / 1000}s deadline with at most 2 " +
                "concurrent renders; failures are structured render_timeout / render_busy / " +
                "render_output_too_large errors. The first call after server start is slower (class " +
                "loading). Use build_function_model for validation only.",
        inputSchema = FUNCTION_MODEL_INPUT_SCHEMA,
    ) { request ->
        // Off the protocol dispatcher: guardedRender blocks its caller for up to the render deadline.
        withContext(Dispatchers.IO) { renderFunctionModelSvgHandler(request.arguments) }
    }
}
