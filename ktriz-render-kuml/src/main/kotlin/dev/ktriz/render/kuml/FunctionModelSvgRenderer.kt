package dev.ktriz.render.kuml

import dev.ktriz.function.FunctionEdge
import dev.ktriz.function.FunctionModel
import dev.kuml.layout.EdgeId
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
 * names could originate from untrusted input. A component name too wide for its (grid-snapped,
 * clamped) node box is truncated with an ellipsis and carries the full name in a `<title>`
 * tooltip -- see `NodeSizing.kt`'s `displayTextFor`.
 *
 * Every edge gets a triangular arrowhead at its target, oriented from the edge's own *base*
 * route geometry -- never from whichever stroke is actually drawn, so a wavy `HARMFUL` line's
 * arrowhead still points along the straight route axis (see `EdgeGeometry.kt`'s
 * `arrowheadPoints`). Two or more edges between the same unordered component pair bow apart via
 * a sine-shaped perpendicular offset so they stay individually visible (see `computeEdgeBases`
 * below); a single edge between a pair is left on its raw ELK route. Self-loops (`from == to`)
 * draw a dedicated fixed-radius arc anchored on the node's right edge instead of relying on
 * ELK's own self-loop routing, and the canvas is widened to fit it if needed.
 */
public fun FunctionModel.renderSvg(): String {
    if (components.isEmpty() && edges.isEmpty()) {
        return svgDocument(width = EMPTY_CANVAS_WIDTH_PX, height = EMPTY_CANVAS_HEIGHT_PX, body = "")
    }

    val result = ElkLayoutEngine().layout(graph = toLayoutGraph(), hints = LayoutHints.DEFAULT)
    val bases = computeEdgeBases(result)
    val body = renderBody(result, bases)
    val bounds = canvasBoundsFor(result, bases)
    val positionedBody =
        if (bounds.offsetX > 0f || bounds.offsetY > 0f) {
            """<g transform="translate(${bounds.offsetX},${bounds.offsetY})">$body</g>"""
        } else {
            body
        }
    return svgDocument(width = bounds.width, height = bounds.height, body = positionedBody)
}

/**
 * The canvas size actually needed to fit every rendered edge point without clipping, plus the
 * `<g transform="translate(...)">` offset required if any point falls outside ELK's own
 * `result.canvas` on any side. Self-loops and bowed multi-edges are drawn from [bases], not from
 * ELK's raw routes, so their geometry can legitimately extend beyond the canvas ELK computed for
 * the unmodified layout -- on any of the four sides, not just to the right (a self-loop bulges
 * right, but a bowed multi-edge's perpendicular offset can push points left, above, or below
 * just as easily, depending on the pair's layout orientation). [CANVAS_MARGIN_PX] is applied on
 * every side that actually needs widening, so a point never lands exactly on the SVG edge.
 */
private fun FunctionModel.canvasBoundsFor(
    result: LayoutResult,
    bases: Map<Int, List<Point>>,
): CanvasBounds {
    val points = bases.values.flatten()
    val minX = minOf(0f, (points.minOfOrNull { it.x } ?: 0f) - CANVAS_MARGIN_PX)
    val minY = minOf(0f, (points.minOfOrNull { it.y } ?: 0f) - CANVAS_MARGIN_PX)
    val maxX = maxOf(result.canvas.width, (points.maxOfOrNull { it.x } ?: 0f) + CANVAS_MARGIN_PX)
    val maxY = maxOf(result.canvas.height, (points.maxOfOrNull { it.y } ?: 0f) + CANVAS_MARGIN_PX)
    val offsetX = -minX
    val offsetY = -minY
    return CanvasBounds(width = maxX + offsetX, height = maxY + offsetY, offsetX = offsetX, offsetY = offsetY)
}

private data class CanvasBounds(
    val width: Float,
    val height: Float,
    val offsetX: Float,
    val offsetY: Float,
)

/**
 * For every edge, the finished *base* geometry its stroke style and arrowhead are derived from:
 * a self-loop gets its own circular arc ([selfLoopPolyline], ELK's route for that edge is
 * discarded); any other edge keeps its raw ELK route unless two or more edges connect the same
 * unordered component pair, in which case all of them are resampled to an even point spacing
 * and bowed apart ([resampleEvenlyByArcLength] + [bowOffset]) so they stay individually
 * visible instead of overlapping. A lone edge between a pair is returned unresampled, on
 * purpose -- see `EdgeGeometry.kt`'s `resampleEvenlyByArcLength` KDoc for why that distinction
 * matters for an existing test.
 *
 * Grouping is O(E): a single `groupBy` scan per edge kind, never an `indexOf()` lookup inside a
 * loop -- important so a pathologically large multi-edge group does not become quadratic.
 */
private fun FunctionModel.computeEdgeBases(result: LayoutResult): Map<Int, List<Point>> {
    val selfLoopK = HashMap<Int, Int>()
    edges
        .withIndex()
        .filter { it.value.from.name == it.value.to.name }
        .groupBy { it.value.from.name }
        .values
        .forEach { group -> group.forEachIndexed { k, iv -> selfLoopK[iv.index] = k } }

    val pairK = HashMap<Int, Int>()
    val pairN = HashMap<Int, Int>()
    edges
        .withIndex()
        .filter { it.value.from.name != it.value.to.name }
        .groupBy { listOf(it.value.from.name, it.value.to.name).sorted() }
        .values
        .forEach { group ->
            group.forEachIndexed { k, iv -> pairK[iv.index] = k }
            group.forEach { iv -> pairN[iv.index] = group.size }
        }

    val out = LinkedHashMap<Int, List<Point>>()
    edges.forEachIndexed { index, edge ->
        if (edge.from.name == edge.to.name) {
            val bounds = result.nodes[NodeId(edge.from.name)]?.bounds ?: return@forEachIndexed
            val radius = selfLoopRadiusPx(bounds.size.height, selfLoopK.getValue(index))
            out[index] = selfLoopPolyline(bounds, radius)
        } else {
            val route = result.edges[EdgeId("e$index")] ?: return@forEachIndexed
            val raw = route.polylinePoints()
            // Degenerate route (e.g. one ELK could not route) -- nothing sane to draw.
            if (raw.size < 2) return@forEachIndexed
            val n = pairN.getValue(index)
            out[index] =
                if (n <= 1) {
                    raw
                } else {
                    val k = pairK.getValue(index)
                    // Canonical sign normalization: every edge of the same pair bows relative to
                    // the alphabetically smaller component name as the "forward" direction,
                    // regardless of the edge's actual from/to direction -- otherwise an A->B and
                    // a B->A edge between the same pair could bow to the same side instead of
                    // fanning out evenly.
                    val forward = edge.from.name == minOf(edge.from.name, edge.to.name)
                    val offset = bowOffsetFor(k, n).let { if (forward) it else -it }
                    resampleEvenlyByArcLength(raw, BOW_SAMPLES).bowOffset(offset)
                }
        }
    }
    return out
}

private fun FunctionModel.renderBody(
    result: LayoutResult,
    bases: Map<Int, List<Point>>,
): String =
    buildString {
        edges.forEachIndexed { index, edge ->
            val base = bases[index] ?: return@forEachIndexed
            append(renderEdge(edge, base))
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
    val innerWidth = bounds.size.width - 2 * NODE_PAD_X_PX
    val display = displayTextFor(nodeId.value, innerWidth)
    val titleTag = if (display.truncated) "<title>${xmlEscapeText(nodeId.value)}</title>" else ""
    return """
        |<rect x="${bounds.origin.x}" y="${bounds.origin.y}" width="${bounds.size.width}" height="${bounds.size.height}" fill="$NODE_FILL_COLOR" stroke="$NODE_STROKE_COLOR" stroke-width="$STROKE_WIDTH_PX"/>
        |<text x="$cx" y="$cy" text-anchor="middle" dominant-baseline="middle" font-family="$NODE_FONT_FAMILY" font-size="$NODE_FONT_SIZE_PX">$titleTag${xmlEscapeText(
        display.text,
    )}</text>
        |
        """.trimMargin()
}

private fun renderEdge(
    edge: FunctionEdge,
    base: List<Point>,
): String {
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
    // The arrowhead's direction always comes from the base geometry (after bow-offset/self-loop
    // synthesis, before wavy perturbation) -- never from the stroke actually drawn. Otherwise a
    // wavy HARMFUL edge's arrowhead would follow the last wiggle's tangent instead of the
    // straight route axis.
    val arrow = arrowheadPoints(base).toSvgPolygonPoints()
    val arrowTag = """<polygon points="$arrow" fill="$EDGE_STROKE_COLOR"/>"""
    val mid = midpointOf(base)
    val label =
        """<text x="${mid.x}" y="${mid.y - 4}" text-anchor="middle" font-family="$NODE_FONT_FAMILY" font-size="$EDGE_LABEL_FONT_SIZE_PX">${xmlEscapeText(
            edge.verb,
        )}</text>"""
    return pathTags + arrowTag + label
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
