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
    val geometry = computeEdgeBases(result)
    val body = renderBody(result, geometry)
    val bounds = canvasBoundsFor(result, geometry)
    val positionedBody =
        if (bounds.offsetX > 0f || bounds.offsetY > 0f) {
            """<g transform="translate(${bounds.offsetX},${bounds.offsetY})">$body</g>"""
        } else {
            body
        }
    return svgDocument(width = bounds.width, height = bounds.height, body = positionedBody)
}

/**
 * The canvas size actually needed to fit every rendered edge point *and* every edge verb label
 * without clipping, plus the `<g transform="translate(...)">` offset required if any of them
 * falls outside ELK's own `result.canvas` on any side. Self-loops and bowed multi-edges are
 * drawn from [bases], not from ELK's raw routes, so their geometry can legitimately extend
 * beyond the canvas ELK computed for the unmodified layout -- on any of the four sides, not just
 * to the right (a self-loop bulges right, but a bowed multi-edge's perpendicular offset can push
 * points left, above, or below just as easily, depending on the pair's layout orientation).
 * Edge-point geometry alone is not enough, though: a verb label is drawn centered
 * (`text-anchor="middle"`) at its route's midpoint with no relation to any rendered point's
 * coordinates, so a self-loop or edge whose midpoint sits near a canvas edge can still have its
 * *label* clip even when every path/polygon point is comfortably inside -- see this file's git
 * history for the "wears" self-loop label this was caught against by manual visual inspection
 * (automated point-coordinate tests never render text, so they could not have caught it).
 * [CANVAS_MARGIN_PX] is applied on every side that actually needs widening, so nothing lands
 * exactly on the SVG edge.
 */
private fun FunctionModel.canvasBoundsFor(
    result: LayoutResult,
    geometry: EdgeGeometry,
): CanvasBounds {
    val edgePoints = geometry.bases.values.flatten()
    val labelCorners =
        edges.withIndex().flatMap { (index, edge) ->
            val base = geometry.bases[index] ?: return@flatMap emptyList()
            val labelY = labelBaselineY(base, geometry.labelStackIndex[index] ?: 0)
            val mid = midpointOf(base)
            val halfWidth = measuredEdgeLabelWidthPx(edge.verb) / 2f
            // Label baseline sits at labelY (see renderEdge); pad upward by the font size for the
            // glyphs' ascent above the baseline, since SVG text y is a baseline, not a box top.
            listOf(
                Point(x = mid.x - halfWidth, y = labelY - EDGE_LABEL_FONT_SIZE_PX),
                Point(x = mid.x + halfWidth, y = labelY),
            )
        }
    val points = edgePoints + labelCorners
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
 * [bases]: for every edge, the finished *base* geometry its stroke style and arrowhead are
 * derived from. [labelStackIndex]: for every edge, its 0-based position within its label-sharing
 * group (all self-loops on the same component, or all edges between the same unordered component
 * pair) -- 0 for an edge that is the only member of its group. Both maps share the same keyset
 * (every edge with a non-degenerate route) and the same per-group ordering, computed once here so
 * [renderBody]'s per-edge label vertical stagger ([labelBaselineY]) lines up with the geometry
 * that produced the bow/loop [bases] entry it labels.
 */
private data class EdgeGeometry(
    val bases: Map<Int, List<Point>>,
    val labelStackIndex: Map<Int, Int>,
)

/**
 * Computes [EdgeGeometry.bases]: a self-loop gets its own circular arc ([selfLoopPolyline], ELK's
 * route for that edge is discarded); any other edge keeps its raw ELK route unless two or more
 * edges connect the same unordered component pair, in which case all of them are resampled to an
 * even point spacing and bowed apart ([resampleEvenlyByArcLength] + [bowOffset]) so they stay
 * individually visible instead of overlapping. A lone edge between a pair is returned unresampled,
 * on purpose -- see `EdgeGeometry.kt`'s `resampleEvenlyByArcLength` KDoc for why that distinction
 * matters for an existing test.
 *
 * Grouping is O(E): a single `groupBy` scan per edge kind, never an `indexOf()` lookup inside a
 * loop -- important so a pathologically large multi-edge group does not become quadratic.
 */
private fun FunctionModel.computeEdgeBases(result: LayoutResult): EdgeGeometry {
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
    // selfLoopK and pairK key-sets are disjoint (an edge is either a self-loop or not), and both
    // only ever assign to indices also present in `out` -- edges dropped above (degenerate route,
    // missing node bounds) simply have no entry in `out` and are skipped by every out[index]-keyed
    // consumer below, so a stale selfLoopK/pairK entry for a dropped edge is harmless dead data.
    return EdgeGeometry(bases = out, labelStackIndex = selfLoopK + pairK)
}

/**
 * The verb label's SVG text baseline y for an edge at [stackIndex] within its label-sharing
 * group: [EDGE_LABEL_BASE_OFFSET_PX] above the route's arc-length midpoint for a solo edge
 * (`stackIndex == 0`), every further member of the group staggered
 * [EDGE_LABEL_STACK_STEP_PX] further away, alternating *above and below* the midpoint (1 up,
 * 2 down, 3 further up, 4 further down, ...) rather than piling every label on one side --
 * for a group of size `n` this roughly halves the tallest single-direction excursion compared
 * to a one-directional staircase, which otherwise grows unbounded with group size and can climb
 * into a neighbouring node's box for a wide multi-edge group between two closely stacked nodes
 * (a known remaining limitation for very large groups with long verbs, documented in the README;
 * full label-collision avoidance -- e.g. shrinking the step or routing a leader line -- is out
 * of scope for this wave).
 *
 * Multiple edges between the same pair already bow apart so their *lines* stay individually
 * visible (see [computeEdgeBases]), but their verb labels are drawn at the same, undisplaced
 * midpoint height on the route between two nodes -- without this stagger they visually collide
 * regardless of how far apart the lines themselves bow, since text width usually exceeds the bow
 * spacing. A self-loop's single label needs no stagger from its own group unless multiple
 * self-loops share the same component, in which case the same staircase applies.
 */
private fun labelBaselineY(
    base: List<Point>,
    stackIndex: Int,
): Float {
    if (stackIndex == 0) return midpointOf(base).y - EDGE_LABEL_BASE_OFFSET_PX
    val level = (stackIndex + 1) / 2
    val sign = if (stackIndex % 2 == 1) -1f else 1f
    return midpointOf(base).y - EDGE_LABEL_BASE_OFFSET_PX + sign * level * EDGE_LABEL_STACK_STEP_PX
}

private fun FunctionModel.renderBody(
    result: LayoutResult,
    geometry: EdgeGeometry,
): String =
    buildString {
        edges.forEachIndexed { index, edge ->
            val base = geometry.bases[index] ?: return@forEachIndexed
            val stackIndex = geometry.labelStackIndex[index] ?: 0
            append(renderEdge(edge, base, stackIndex))
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
    labelStackIndex: Int,
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
    val labelY = labelBaselineY(base, labelStackIndex)
    val label =
        """<text x="${mid.x}" y="$labelY" text-anchor="middle" font-family="$NODE_FONT_FAMILY" font-size="$EDGE_LABEL_FONT_SIZE_PX">${xmlEscapeText(
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
