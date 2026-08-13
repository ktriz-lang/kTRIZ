package dev.ktriz.render.kuml

import dev.ktriz.function.FunctionEdge
import dev.ktriz.function.FunctionModel
import dev.kuml.layout.EdgeId
import dev.kuml.layout.EdgeRoute
import dev.kuml.layout.LayoutHints
import dev.kuml.layout.LayoutResult
import dev.kuml.layout.NodeId
import dev.kuml.layout.Point
import dev.kuml.layout.Rect
import dev.kuml.layout.elk.ElkLayoutEngine

private const val STROKE_WIDTH_PX = 2f
private const val NODE_STROKE_COLOR = "#000000"
private const val NODE_FILL_COLOR = "#ffffff"
private const val EDGE_STROKE_COLOR = "#333333"
private const val EMPTY_CANVAS_WIDTH_PX = 200f
private const val EMPTY_CANVAS_HEIGHT_PX = 120f

/**
 * Renders this [FunctionModel] as a complete, standalone SVG document string.
 *
 * kUML is used strictly as a *layout* library: [ElkLayoutEngine] computes node positions and
 * edge routes, nothing more. The SVG markup itself is emitted by this module's own small
 * serializer, because kUML's SVG renderer (`kuml-io-svg`) is built around specific metamodel
 * packages (UML/C4/SysML2/BPMN/Blueprint/ERM) with per-relationship-subtype hardcoded line
 * styles, no generic node/edge diagram entry point, and no wavy/doubled-line concept a
 * [dev.ktriz.function.FunctionQuality] could map onto -- see kTRIZ-ADR-0002, "Update
 * 2026-08-13", for the full design rationale (this project's Obsidian vault, not shipped with
 * the repo).
 *
 * [dev.ktriz.function.FunctionQuality] maps to a stroke treatment via
 * [dev.ktriz.render.kuml.strokeStyle]: [dev.ktriz.function.FunctionQuality.USEFUL] solid,
 * [dev.ktriz.function.FunctionQuality.INSUFFICIENT] dashed,
 * [dev.ktriz.function.FunctionQuality.EXCESSIVE] a doubled line,
 * [dev.ktriz.function.FunctionQuality.HARMFUL] a sine-wavy path (see `EdgeGeometry.kt`).
 *
 * An empty [FunctionModel] (no components, no edges) renders a minimal but valid SVG document
 * without invoking the layout engine at all -- there is nothing to lay out, and this sidesteps
 * a zero-node [dev.kuml.layout.LayoutGraph] entirely rather than relying on it.
 *
 * [dev.ktriz.function.Component.name] and [FunctionEdge.verb] are XML-escaped (see
 * `SvgEscape.kt`) before being written into the document -- this module never trusts either
 * string to already be safe SVG content, since in a real application a [FunctionModel]'s
 * names could originate from untrusted input.
 */
public fun FunctionModel.renderSvg(): String {
    if (components.isEmpty() && edges.isEmpty()) {
        return svgDocument(width = EMPTY_CANVAS_WIDTH_PX, height = EMPTY_CANVAS_HEIGHT_PX, body = "")
    }

    val result = ElkLayoutEngine().layout(graph = toLayoutGraph(), hints = LayoutHints.DEFAULT)
    return svgDocument(width = result.canvas.width, height = result.canvas.height, body = renderBody(result))
}

private fun FunctionModel.renderBody(result: LayoutResult): String =
    buildString {
        edges.forEachIndexed { index, edge ->
            val route = result.edges[EdgeId("e$index")] ?: return@forEachIndexed
            append(renderEdge(edge, route))
        }
        result.nodes.forEach { (nodeId, nodeLayout) ->
            append(renderNode(nodeId, nodeLayout.bounds))
        }
    }

private fun renderNode(
    nodeId: NodeId,
    bounds: Rect,
): String {
    val cx = bounds.origin.x + bounds.size.width / 2
    val cy = bounds.origin.y + bounds.size.height / 2
    return """
        |<rect x="${bounds.origin.x}" y="${bounds.origin.y}" width="${bounds.size.width}" height="${bounds.size.height}" fill="$NODE_FILL_COLOR" stroke="$NODE_STROKE_COLOR" stroke-width="$STROKE_WIDTH_PX"/>
        |<text x="$cx" y="$cy" text-anchor="middle" dominant-baseline="middle">${xmlEscapeText(nodeId.value)}</text>
        |
        """.trimMargin()
}

private fun renderEdge(
    edge: FunctionEdge,
    route: EdgeRoute,
): String {
    val base = route.polylinePoints()
    if (base.size < 2) return "" // degenerate route (e.g. a self-loop ELK could not route); nothing sane to draw
    val style = edge.quality.strokeStyle()
    val paths: List<List<Point>> =
        when (style) {
            EdgeStrokeStyle.SOLID, EdgeStrokeStyle.DASHED -> listOf(base)
            EdgeStrokeStyle.DOUBLED ->
                listOf(
                    base.offsetPerpendicular(DOUBLE_STROKE_GAP_PX / 2f),
                    base.offsetPerpendicular(-DOUBLE_STROKE_GAP_PX / 2f),
                )
            EdgeStrokeStyle.WAVY -> listOf(wavyPathPoints(base))
        }
    val dashAttr = if (style == EdgeStrokeStyle.DASHED) """ stroke-dasharray="$DASH_ARRAY"""" else ""
    val pathTags =
        paths.joinToString("") { pts ->
            val d = xmlEscapeAttr(pts.toSvgPathData())
            """<path d="$d" fill="none" stroke="$EDGE_STROKE_COLOR" stroke-width="$STROKE_WIDTH_PX"$dashAttr/>"""
        }
    val mid = midpointOf(base)
    val label = """<text x="${mid.x}" y="${mid.y - 4}" text-anchor="middle">${xmlEscapeText(edge.verb)}</text>"""
    return pathTags + label
}

private fun svgDocument(
    width: Float,
    height: Float,
    body: String,
): String =
    """<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $width $height" width="$width" height="$height">
$body
</svg>
"""
