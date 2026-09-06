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
 *
 * Edge-verb labels are placed by a greedy 2D collision-avoidance pass ([placeEdgeLabels]):
 * for every edge (in declaration order, so placement is deterministic), a small set of candidate
 * baseline positions alternating above/below the route's midpoint is tried, and the one with the
 * least overlap area against every label and node box already placed is kept. If no candidate
 * avoids overlap entirely, the verb is truncated with an ellipsis (the same mechanism
 * [displayTextFor] uses for over-wide node names) and re-searched; whichever of the full or
 * truncated text ends up with the smaller total overlap area wins -- this always terminates in a
 * bounded number of candidates and never throws, even for a dense bundle of edges between the same
 * two nodes. This replaces a purely alternating vertical stagger that had no knowledge of actual
 * label width and could still collide for long verbs or large edge groups.
 */
public fun FunctionModel.renderSvg(): String {
    if (components.isEmpty() && edges.isEmpty()) {
        return svgDocument(width = EMPTY_CANVAS_WIDTH_PX, height = EMPTY_CANVAS_HEIGHT_PX, body = "")
    }

    val result = ElkLayoutEngine().layout(graph = toLayoutGraph(), hints = LayoutHints.DEFAULT)
    val geometry = computeEdgeBases(result)
    val labels = placeEdgeLabels(result, geometry.bases)
    val body = renderBody(result, geometry, labels)
    val bounds = canvasBoundsFor(result, geometry, labels)
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
 * drawn from [EdgeGeometry.bases], not from ELK's raw routes, so their geometry can legitimately
 * extend beyond the canvas ELK computed for the unmodified layout -- on any of the four sides, not
 * just to the right (a self-loop bulges right, but a bowed multi-edge's perpendicular offset can
 * push points left, above, or below just as easily, depending on the pair's layout orientation).
 * Edge-point geometry alone is not enough, though: a verb label is drawn centered
 * (`text-anchor="middle"`) at its route's midpoint with no relation to any rendered point's
 * coordinates, so a self-loop or edge whose midpoint sits near a canvas edge can still have its
 * *label* clip even when every path/polygon point is comfortably inside -- see this file's git
 * history for the "wears" self-loop label this was caught against by manual visual inspection
 * (automated point-coordinate tests never render text, so they could not have caught it). Label
 * boxes here are computed with the exact same [edgeLabelBoxAt] helper [placeEdgeLabels] used to
 * place them -- a single source of truth, rather than the flat [EDGE_LABEL_FONT_SIZE_PX]
 * approximation this function used before real per-label bounding boxes existed.
 * [CANVAS_MARGIN_PX] is applied on every side that actually needs widening, so nothing lands
 * exactly on the SVG edge.
 */
private fun FunctionModel.canvasBoundsFor(
    result: LayoutResult,
    geometry: EdgeGeometry,
    labels: Map<Int, EdgeLabelPlacement>,
): CanvasBounds {
    val edgePoints = geometry.bases.values.flatten()
    val labelCorners =
        labels.values.flatMap { placement ->
            val box = edgeLabelBoxAt(placement.text, placement.centerX, placement.baselineY)
            listOf(Point(x = box.minX, y = box.minY), Point(x = box.maxX, y = box.maxY))
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

/**
 * [bases]: for every edge, the finished *base* geometry its stroke style, arrowhead, and label
 * placement are derived from -- keyed by the edge's index in [FunctionModel.edges], every edge
 * with a non-degenerate route present.
 */
private data class EdgeGeometry(
    val bases: Map<Int, List<Point>>,
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
    val selfLoopN = HashMap<Int, Int>()
    edges
        .withIndex()
        .filter { it.value.from.name == it.value.to.name }
        .groupBy { it.value.from.name }
        .values
        .forEach { group ->
            group.forEachIndexed { k, iv -> selfLoopK[iv.index] = k }
            group.forEach { iv -> selfLoopN[iv.index] = group.size }
        }

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
            val radius = selfLoopRadiusPx(bounds.size.height, selfLoopK.getValue(index), selfLoopN.getValue(index))
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
    // selfLoopK/selfLoopN and pairK/pairN key-sets are disjoint (an edge is either a self-loop or
    // not), and both only ever assign to indices also present in `out` -- edges dropped above
    // (degenerate route, missing node bounds) simply have no entry in `out` and are skipped by
    // every out[index]-keyed consumer below, so a stale entry for a dropped edge is harmless dead
    // data.
    return EdgeGeometry(bases = out)
}

/** A resolved, collision-checked verb-label placement for one edge -- see [placeEdgeLabels]. */
private data class EdgeLabelPlacement(
    val text: String,
    val truncated: Boolean,
    val centerX: Float,
    val baselineY: Float,
)

/** Bounds how many candidate positions [placeEdgeLabels] tries per edge before settling for the least-bad one it has seen. */
private const val LABEL_CANDIDATE_MAX = 60

/** Extra clearance kept between a label's box and any other label/node box it is checked against, beyond literal overlap. */
private const val LABEL_COLLISION_PAD_PX = 2f

/** Width budget (as a fraction of the full verb's measured width) a label is shrunk to when the fallback truncation search runs. */
private const val LABEL_TRUNCATE_WIDTH_FACTOR = 0.6f

/**
 * Places every edge's verb label with a greedy 2D collision-avoidance search, in
 * [FunctionModel.edges] declaration order so placement is fully deterministic. For each edge, up
 * to [LABEL_CANDIDATE_MAX] candidate baseline positions are tried -- alternating above and below
 * the route's arc-length midpoint, each level one *label-height* step further out (the step is
 * the text's own measured box height plus [LABEL_COLLISION_PAD_PX] on each side, not a fixed
 * constant: [EDGE_LABEL_STACK_STEP_PX] alone (13px) is *smaller* than a 12px font's typical
 * ascent+descent, so two candidates one level apart would still overlap vertically regardless of
 * how many levels were tried -- using the real box height instead guarantees every level is
 * genuinely clear of its neighbours), floored at [EDGE_LABEL_STACK_STEP_PX] so short single-line
 * labels never step by less than the original spacing. Candidate 0 reproduces the original
 * single-edge placement (`midpointOf(base).y - EDGE_LABEL_BASE_OFFSET_PX`) exactly regardless of
 * this step, since level 0 has no offset -- and the candidate whose real, measured label box
 * ([edgeLabelBoxAt], inflated by [LABEL_COLLISION_PAD_PX]) has the least total overlap area
 * against every label and node box placed so far is kept. If no candidate is overlap-free, the
 * verb is truncated to roughly [LABEL_TRUNCATE_WIDTH_FACTOR] of its measured width (via
 * [displayTextFor], the same mechanism over-wide node names use) and the search is repeated;
 * whichever of the full-text or truncated-text result has the smaller overlap area wins, and a
 * truncated placement carries a `<title>` tooltip with the full verb (mirrors [renderNode]'s
 * truncated-name tooltip). This is O(E * [LABEL_CANDIDATE_MAX] * (E + V)) in the worst case --
 * bounded per edge, so a pathologically large [FunctionModel] cannot make label placement itself
 * a runaway cost.
 *
 * Multiple edges between the same pair already bow apart so their *lines* stay individually
 * visible (see [computeEdgeBases]), but without collision-aware label placement their verb labels
 * could still visually collide, since text width usually exceeds the bow spacing. A self-loop's
 * single label needs no collision search against its own group unless multiple self-loops share
 * the same component, in which case the same search applies.
 */
private fun FunctionModel.placeEdgeLabels(
    result: LayoutResult,
    bases: Map<Int, List<Point>>,
): Map<Int, EdgeLabelPlacement> {
    val nodeBoxes = result.nodes.values.map { it.bounds.toLabelBox() }
    val placed = mutableListOf<LabelBox>()
    val out = LinkedHashMap<Int, EdgeLabelPlacement>()

    fun candidateY(
        midY: Float,
        c: Int,
        stepPx: Float,
    ): Float {
        val level = (c + 1) / 2
        val sign = if (c % 2 == 1) -1f else 1f
        return midY - EDGE_LABEL_BASE_OFFSET_PX + sign * level * stepPx
    }

    fun bestOf(
        midX: Float,
        midY: Float,
        text: String,
    ): Pair<Float, Float> {
        val ownHeight = edgeLabelBoxAt(text, midX, 0f).let { it.maxY - it.minY }
        val stepPx = maxOf(EDGE_LABEL_STACK_STEP_PX, ownHeight + 2f * LABEL_COLLISION_PAD_PX)
        var bestY = candidateY(midY, 0, stepPx)
        var bestArea = Float.MAX_VALUE
        for (c in 0 until LABEL_CANDIDATE_MAX) {
            val y = candidateY(midY, c, stepPx)
            val box = edgeLabelBoxAt(text, midX, y).inflate(LABEL_COLLISION_PAD_PX)
            val area =
                placed.sumOf { box.overlapAreaWith(it).toDouble() }.toFloat() +
                    nodeBoxes.sumOf { box.overlapAreaWith(it).toDouble() }.toFloat()
            if (area == 0f) return y to 0f
            if (area < bestArea) {
                bestArea = area
                bestY = y
            }
        }
        return bestY to bestArea
    }

    edges.forEachIndexed { index, edge ->
        val base = bases[index] ?: return@forEachIndexed
        val mid = midpointOf(base)
        val (fullY, fullArea) = bestOf(mid.x, mid.y, edge.verb)
        val (finalText, finalY, truncated) =
            if (fullArea == 0f) {
                Triple(edge.verb, fullY, false)
            } else {
                val shrunkWidth = measuredEdgeLabelWidthPx(edge.verb) * LABEL_TRUNCATE_WIDTH_FACTOR
                val shrunk = displayTextFor(edge.verb, shrunkWidth, ::measuredEdgeLabelWidthPx)
                val (shrunkY, shrunkArea) = bestOf(mid.x, mid.y, shrunk.text)
                if (shrunkArea < fullArea) {
                    Triple(shrunk.text, shrunkY, shrunk.truncated)
                } else {
                    Triple(edge.verb, fullY, false)
                }
            }
        placed += edgeLabelBoxAt(finalText, mid.x, finalY).inflate(LABEL_COLLISION_PAD_PX)
        out[index] = EdgeLabelPlacement(text = finalText, truncated = truncated, centerX = mid.x, baselineY = finalY)
    }
    return out
}

private fun FunctionModel.renderBody(
    result: LayoutResult,
    geometry: EdgeGeometry,
    labels: Map<Int, EdgeLabelPlacement>,
): String =
    buildString {
        edges.forEachIndexed { index, edge ->
            val base = geometry.bases[index] ?: return@forEachIndexed
            val placement = labels[index] ?: return@forEachIndexed
            append(renderEdge(edge, base, placement))
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
    val titleTag = if (display.truncated) "<title>${xmlEscapeText(titleTextFor(nodeId.value))}</title>" else ""
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
    placement: EdgeLabelPlacement,
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
    // A truncated verb (label placement couldn't avoid overlap even after shrinking) carries the
    // full verb as a <title> tooltip, mirroring renderNode's truncated-name tooltip.
    val titleTag = if (placement.truncated) "<title>${xmlEscapeText(titleTextFor(edge.verb))}</title>" else ""
    val labelText = xmlEscapeText(placement.text)
    val label =
        """<text x="${placement.centerX}" y="${placement.baselineY}" text-anchor="middle" """ +
            """font-family="$NODE_FONT_FAMILY" font-size="$EDGE_LABEL_FONT_SIZE_PX">$titleTag$labelText</text>"""
    return pathTags + arrowTag + label
}
