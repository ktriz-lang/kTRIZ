package dev.ktriz.render.kuml

import dev.kuml.layout.EdgeRoute
import dev.kuml.layout.Point
import dev.kuml.layout.Rect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal const val WAVE_AMPLITUDE_PX = 6f
internal const val WAVE_WAVELENGTH_PX = 24f
internal const val WAVE_STEP_PX = 4f

internal const val BOW_SAMPLES = 17
internal const val BOW_SPACING_PX = 9f
internal const val BOW_ENVELOPE_PX = 90f
internal const val BOW_MIN_SPACING_PX = 3f
internal const val SELF_LOOP_BASE_RADIUS_PX = 20f
internal const val SELF_LOOP_HEIGHT_FACTOR = 0.2f
internal const val SELF_LOOP_HEIGHT_OFFSET_PX = 12f
internal const val SELF_LOOP_STACK_STEP_PX = 8f
internal const val SELF_LOOP_ENVELOPE_PX = 120f
internal const val SELF_LOOP_MIN_STACK_STEP_PX = 3f
internal const val SELF_LOOP_SAMPLES = 24
internal const val SELF_LOOP_EXIT_FRACTION = 0.30f
internal const val SELF_LOOP_RETURN_FRACTION = 0.70f
internal const val CANVAS_MARGIN_PX = 4f
internal const val EDGE_LABEL_BASE_OFFSET_PX = 4f
internal const val EDGE_LABEL_STACK_STEP_PX = 13f

/** Target spacing between neighbouring edge endpoints on one box side for a multi-edge bundle. */
internal const val ANCHOR_SPACING_PX = 14f

/** No anchor is placed closer than this to a box corner, so arrowheads at the ends stay on the flat side. */
internal const val ANCHOR_CORNER_INSET_PX = 12f

/** Clearance kept between an anchor and the band of the right side a self-loop occupies. */
internal const val SELF_LOOP_ANCHOR_CLEARANCE_PX = 4f

/** Wave amplitude inside a bundle is at most this fraction of the neighbouring anchor spacing. */
internal const val GROUP_WAVE_AMPLITUDE_FACTOR = 0.4f

/**
 * Flattens any [EdgeRoute] into an ordered polyline from source to target, waypoints
 * included. [EdgeRoute.Bezier]'s control points are used as straight-line polyline vertices
 * rather than sampled along a true cubic curve -- this branch only exists to keep the `when`
 * exhaustive against [EdgeRoute]'s sealed hierarchy; [dev.kuml.layout.elk.ElkLayoutEngine]'s
 * own `capabilities.supportedEdgeStyles` (checked in kUML source) is `{Direct,
 * OrthogonalRounded}` only, so a real [EdgeRoute.Bezier] never reaches this function via the
 * engine this module actually uses.
 */
internal fun EdgeRoute.polylinePoints(): List<Point> =
    when (this) {
        is EdgeRoute.Direct -> listOf(source, target)
        is EdgeRoute.OrthogonalRounded -> listOf(source) + waypoints + target
        is EdgeRoute.TreeRounded -> listOf(source) + waypoints + target
        is EdgeRoute.Bezier -> listOf(source) + controlPoints + target
    }

private fun distance(
    a: Point,
    b: Point,
): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    return sqrt(dx * dx + dy * dy)
}

/** SVG `d` attribute for a polyline: `M x0 y0 L x1 y1 L x2 y2 ...`. Empty string if `size < 2`. */
internal fun List<Point>.toSvgPathData(): String {
    if (size < 2) return ""
    val first = first()
    val rest = drop(1)
    return buildString {
        append("M ${first.x} ${first.y}")
        rest.forEach { p -> append(" L ${p.x} ${p.y}") }
    }
}

/**
 * Offsets every point of `this` polyline by [distancePx] perpendicular to the local segment
 * direction (endpoints use their one adjacent segment; interior points average the direction
 * of their two adjacent segments -- in practice this walk just recomputes the perpendicular
 * per point from its immediate neighbours, which is enough for the mostly-straight/orthogonal
 * routes this module deals with). Used to draw the two parallel paths of an
 * [EdgeStrokeStyle.DOUBLED] edge -- called twice per doubled edge, with `+DOUBLE_STROKE_GAP_PX / 2`
 * and `-DOUBLE_STROKE_GAP_PX / 2`.
 */
internal fun List<Point>.offsetPerpendicular(distancePx: Float): List<Point> {
    if (size < 2) return this
    return mapIndexed { i, p ->
        val (a, b) =
            when (i) {
                0 -> this[0] to this[1]
                lastIndex -> this[lastIndex - 1] to this[lastIndex]
                else -> this[i - 1] to this[i + 1]
            }
        val len = distance(a, b).takeIf { it > 0f } ?: 1f
        val nx = -(b.y - a.y) / len
        val ny = (b.x - a.x) / len
        Point(x = p.x + nx * distancePx, y = p.y + ny * distancePx)
    }
}

/**
 * The true arc-length midpoint of `this` polyline -- the point exactly half of the total path
 * length along the walk from the first point to the last, not `points[points.size / 2]`. That
 * naive index only lands near the middle for polylines with many, evenly-spaced points; for the
 * common two-point case (an [EdgeRoute.Direct] route, `size == 2`), `size / 2 == 1` always
 * resolves to `points[1]` -- the *target endpoint*, not a midpoint at all. Used to place an edge
 * verb label at a reasonable position along the route regardless of how many waypoints it has.
 *
 * Returns the single point if [points] has zero or one entries, and the midpoint of the two
 * endpoints if the total length is zero (coincident points).
 */
internal fun midpointOf(points: List<Point>): Point {
    if (points.size <= 1) return points.first()
    val segments = points.zipWithNext()
    val totalLength = segments.sumOf { (a, b) -> distance(a, b).toDouble() }.toFloat()
    if (totalLength <= 0f) return points.first()

    val target = totalLength / 2f
    var walked = 0f
    for ((a, b) in segments) {
        val segLen = distance(a, b)
        if (walked + segLen >= target) {
            val t = if (segLen == 0f) 0f else (target - walked) / segLen
            return Point(x = a.x + (b.x - a.x) * t, y = a.y + (b.y - a.y) * t)
        }
        walked += segLen
    }
    return points.last()
}

/**
 * Produces a sine-perturbed version of [points] for [EdgeStrokeStyle.WAVY]: walks the
 * polyline by arc length in [stepPx] increments and, at each sample, offsets the point along
 * the local segment's perpendicular by `sin(distanceWalked / wavelengthPx * 2*PI) * amplitudePx`.
 * [points]' first and last entries are always preserved unperturbed, so a wavy stroke still
 * lands exactly on the node boundary a straight route would have hit. Returns [points]
 * unchanged if it has fewer than two points or zero total length (degenerate/self-loop route
 * with a single coincident point).
 */
internal fun wavyPathPoints(
    points: List<Point>,
    amplitudePx: Float = WAVE_AMPLITUDE_PX,
    wavelengthPx: Float = WAVE_WAVELENGTH_PX,
    stepPx: Float = WAVE_STEP_PX,
): List<Point> {
    if (points.size < 2) return points
    val segments = points.zipWithNext()
    val totalLength = segments.sumOf { (a, b) -> distance(a, b).toDouble() }.toFloat()
    if (totalLength <= 0f) return points

    val result = mutableListOf(points.first())
    var segIndex = 0
    var segStart = 0f
    var a = segments[0].first
    var b = segments[0].second
    var segLen = distance(a, b)

    var walked = stepPx
    while (walked < totalLength) {
        while (segStart + segLen < walked && segIndex < segments.lastIndex) {
            segStart += segLen
            segIndex++
            a = segments[segIndex].first
            b = segments[segIndex].second
            segLen = distance(a, b)
        }
        val t = if (segLen == 0f) 0f else (walked - segStart) / segLen
        val px = a.x + (b.x - a.x) * t
        val py = a.y + (b.y - a.y) * t
        val len = segLen.takeIf { it > 0f } ?: 1f
        val nx = -(b.y - a.y) / len
        val ny = (b.x - a.x) / len
        val offset = sin((walked / wavelengthPx) * 2f * PI.toFloat()) * amplitudePx
        result += Point(x = px + nx * offset, y = py + ny * offset)
        walked += stepPx
    }
    result += points.last()
    return result
}

/**
 * [sampleCount] points evenly spaced by arc length along [points], endpoints preserved exactly.
 * Used before [bowOffset] so a multi-edge component pair's parallel routes get a dense, evenly
 * distributed set of points to bulge, rather than the sparse waypoints ELK itself returned.
 *
 * Only ever called when a component pair has two or more edges -- directly by [deformRoute]
 * (via `EdgeBundles.kt`), which also moves both ends onto distributed anchors. A single edge
 * keeps its raw ELK route (2 points for an [EdgeRoute.Direct] route) instead of being
 * resampled, so the existing edge-label-midpoint test (which expects exactly 4 coordinates
 * from a `Direct` route's `d` attribute) keeps passing.
 */
internal fun resampleEvenlyByArcLength(
    points: List<Point>,
    sampleCount: Int,
): List<Point> {
    require(sampleCount >= 2)
    if (points.size < 2) return points
    val segments = points.zipWithNext()
    val totalLength = segments.sumOf { (a, b) -> distance(a, b).toDouble() }.toFloat()
    if (totalLength <= 0f) return List(sampleCount) { points.first() }
    val result = mutableListOf<Point>()
    var segIndex = 0
    var segStart = 0f
    for (i in 0 until sampleCount) {
        val target = totalLength * i / (sampleCount - 1)
        while (segIndex < segments.lastIndex &&
            segStart + distance(segments[segIndex].first, segments[segIndex].second) < target
        ) {
            segStart += distance(segments[segIndex].first, segments[segIndex].second)
            segIndex++
        }
        val (a, b) = segments[segIndex]
        val segLen = distance(a, b)
        val t = if (segLen == 0f) 0f else ((target - segStart) / segLen).coerceIn(0f, 1f)
        result += Point(x = a.x + (b.x - a.x) * t, y = a.y + (b.y - a.y) * t)
    }
    return result
}

/**
 * Shifts every point of `this` (already resampled by [resampleEvenlyByArcLength]) perpendicular
 * to the local segment direction by `maxOffsetPx * sin(PI * t)`, `t` = normalized position along
 * the point sequence -- zero at both ends, maximal at the middle, so the bowed route still meets
 * the node boundary exactly where the straight route did. Identity when `maxOffsetPx == 0f`.
 */
internal fun List<Point>.bowOffset(maxOffsetPx: Float): List<Point> {
    if (maxOffsetPx == 0f || size < 2) return this
    return mapIndexed { i, p ->
        val (a, b) =
            when (i) {
                0 -> this[0] to this[1]
                lastIndex -> this[lastIndex - 1] to this[lastIndex]
                else -> this[i - 1] to this[i + 1]
            }
        val len = distance(a, b).takeIf { it > 0f } ?: 1f
        val nx = -(b.y - a.y) / len
        val ny = (b.x - a.x) / len
        val t = i / lastIndex.toFloat()
        val mag = maxOffsetPx * sin(PI.toFloat() * t)
        Point(x = p.x + nx * mag, y = p.y + ny * mag)
    }
}

/**
 * A circular arc from an exit point to a return point on the right edge of [bounds], with a
 * fixed radius [radiusPx], as a [sampleCount]-point polyline. The first and last points sit
 * exactly on the node boundary. Replaces ELK's own self-loop routing entirely -- see
 * `FunctionModelSvgRenderer.kt`'s `computeEdgeBases`.
 */
internal fun selfLoopPolyline(
    bounds: Rect,
    radiusPx: Float,
    sampleCount: Int = SELF_LOOP_SAMPLES,
): List<Point> {
    val exitY = bounds.origin.y + bounds.size.height * SELF_LOOP_EXIT_FRACTION
    val returnY = bounds.origin.y + bounds.size.height * SELF_LOOP_RETURN_FRACTION
    val edgeX = bounds.origin.x + bounds.size.width
    val halfChord = (returnY - exitY) / 2f
    // Safety net: if the requested radius is too small for the exit/return chord, grow it just
    // enough to keep the sqrt() argument non-negative (real for every legal node height).
    val r = if (radiusPx > halfChord) radiusPx else halfChord + 1f
    val bulge = sqrt(r * r - halfChord * halfChord)
    val centerX = edgeX + bulge
    val centerY = (exitY + returnY) / 2f
    val angleExit = atan2((exitY - centerY), (edgeX - centerX))
    val angleReturn = atan2((returnY - centerY), (edgeX - centerX))
    return (0 until sampleCount).map { i ->
        val t = i / (sampleCount - 1).toFloat()
        val angle = angleExit + (angleReturn - angleExit) * t
        Point(x = centerX + r * cos(angle), y = centerY + r * sin(angle))
    }
}

/**
 * Radius for the k-th (0-based) self-loop of [loopCount] sharing the same component -- later
 * loops stack outward. The per-loop step narrows adaptively as [loopCount] grows so the stack
 * stays within [SELF_LOOP_ENVELOPE_PX] of the innermost loop's radius for moderate [loopCount]
 * values, down to a [SELF_LOOP_MIN_STACK_STEP_PX] floor -- unlike a hard clamp (the previous
 * `SELF_LOOP_STACK_MAX_PX`), which let every loop past a fixed count collapse onto the exact same
 * radius and become visually indistinguishable. Past roughly 40 loops (`loopCount - 1 >
 * SELF_LOOP_ENVELOPE_PX / SELF_LOOP_MIN_STACK_STEP_PX`) the floor takes over and the step stays
 * pinned at [SELF_LOOP_MIN_STACK_STEP_PX], so the envelope is no longer an upper bound -- total
 * spread then grows linearly with [loopCount] again. That is intentional: edges keep spreading
 * apart rather than collapsing onto each other, which is the bug this stack-step logic fixes; the
 * envelope is best read as "the target spread for realistic loop counts", not a hard cap. For
 * `loopCount <= 1` the step is irrelevant since there is only ever one loop (`loopIndex == 0`).
 */
internal fun selfLoopRadiusPx(
    nodeHeightPx: Float,
    loopIndex: Int,
    loopCount: Int,
): Float {
    val base = maxOf(SELF_LOOP_BASE_RADIUS_PX, nodeHeightPx * SELF_LOOP_HEIGHT_FACTOR + SELF_LOOP_HEIGHT_OFFSET_PX)
    val step =
        if (loopCount <= 1) {
            0f
        } else {
            (SELF_LOOP_ENVELOPE_PX / (loopCount - 1)).coerceIn(SELF_LOOP_MIN_STACK_STEP_PX, SELF_LOOP_STACK_STEP_PX)
        }
    return base + loopIndex * step
}

/**
 * Perpendicular bow offset for edge `loopIndex` of `groupSize` sharing the same component pair
 * (`groupSize<=1` -> 0). The spacing between adjacent edges narrows adaptively as [groupSize]
 * grows so the fan stays within +/-[BOW_ENVELOPE_PX] of the pair's route for moderate [groupSize]
 * values, down to a [BOW_MIN_SPACING_PX] floor -- unlike a hard clamp (the previous
 * `BOW_MAX_OFFSET_PX`), which let every edge past a fixed group size pile onto the same two
 * outermost offsets and become indistinguishable from each other. Past roughly 60 edges
 * (`groupSize - 1 > 2 * BOW_ENVELOPE_PX / BOW_MIN_SPACING_PX`) the floor takes over and spacing
 * stays pinned at [BOW_MIN_SPACING_PX], so the envelope is no longer an upper bound -- total fan
 * width then grows linearly with [groupSize] again. That is intentional: edges keep spreading
 * apart rather than collapsing onto each other, which is the bug this spacing logic fixes; the
 * envelope is best read as "the target spread for realistic group sizes", not a hard cap.
 */
internal fun bowOffsetFor(
    loopIndex: Int,
    groupSize: Int,
): Float {
    if (groupSize <= 1) return 0f
    val spacing = (2f * BOW_ENVELOPE_PX / (groupSize - 1)).coerceIn(BOW_MIN_SPACING_PX, BOW_SPACING_PX)
    return (loopIndex - (groupSize - 1) / 2f) * spacing
}

/**
 * The 3 corner points of a triangular arrowhead: the tip is [base]'s last point, oriented along
 * [base]'s own trailing direction -- never the direction of whatever stroke is actually drawn
 * (e.g. a [EdgeStrokeStyle.WAVY] path's last wiggle), so the arrowhead always points along the
 * route's true axis. See `FunctionModelSvgRenderer.kt`'s `renderEdge`.
 */
internal fun arrowheadPoints(
    base: List<Point>,
    lengthPx: Float = ARROW_LENGTH_PX,
    halfWidthPx: Float = ARROW_HALF_WIDTH_PX,
): List<Point> {
    val tip = base.last()
    // Walk backwards until the point is at least 1px from the tip -- guards against a
    // zero-length trailing segment (e.g. duplicate consecutive points).
    var back = base[base.lastIndex - 1]
    var i = base.lastIndex - 1
    while (distance(back, tip) < 1f && i > 0) {
        i--
        back = base[i]
    }
    val dx = tip.x - back.x
    val dy = tip.y - back.y
    val len = distance(back, tip).takeIf { it > 0f } ?: 1f
    val ux = dx / len
    val uy = dy / len
    val nx = -uy
    val ny = ux
    val baseCenter = Point(x = tip.x - ux * lengthPx, y = tip.y - uy * lengthPx)
    val left = Point(x = baseCenter.x + nx * halfWidthPx, y = baseCenter.y + ny * halfWidthPx)
    val right = Point(x = baseCenter.x - nx * halfWidthPx, y = baseCenter.y - ny * halfWidthPx)
    return listOf(tip, left, right)
}

/** SVG `points` attribute for a polygon: `x0,y0 x1,y1 x2,y2 ...`. */
internal fun List<Point>.toSvgPolygonPoints(): String = joinToString(" ") { "${it.x},${it.y}" }

/** One side of a node box. Declaration order is the tie-break order of [nearestSide]. */
internal enum class BoxSide { TOP, BOTTOM, LEFT, RIGHT }

/**
 * The side of [bounds] closest to [p] (distance to the side's line). Ties resolve in
 * [BoxSide] declaration order (TOP, BOTTOM, LEFT, RIGHT), so the result is deterministic
 * for points sitting exactly in a corner.
 */
internal fun nearestSide(
    p: Point,
    bounds: Rect,
): BoxSide {
    val x0 = bounds.origin.x
    val y0 = bounds.origin.y
    val x1 = x0 + bounds.size.width
    val y1 = y0 + bounds.size.height
    val candidates =
        listOf(
            BoxSide.TOP to abs(p.y - y0),
            BoxSide.BOTTOM to abs(p.y - y1),
            BoxSide.LEFT to abs(p.x - x0),
            BoxSide.RIGHT to abs(p.x - x1),
        )
    var best = candidates[0]
    for (c in candidates) if (c.second < best.second) best = c
    return best.first
}

/**
 * The coordinate ranges (along [side], i.e. x for TOP/BOTTOM and y for LEFT/RIGHT) where an
 * anchor may sit: the side's length minus [ANCHOR_CORNER_INSET_PX] at both corners. For the RIGHT
 * side of a node that carries self-loops (see [selfLoopPolyline]) the band the loop occupies
 * (exit..return fraction of the height, widened by [SELF_LOOP_ANCHOR_CLEARANCE_PX]) is cut out,
 * leaving up to two ranges. If cutting leaves nothing, or the box is too small for the corner
 * inset, the function falls back to the uncut range (or the side's centre point) -- it never
 * returns an empty list.
 */
internal fun usableSpans(
    bounds: Rect,
    side: BoxSide,
    hasSelfLoops: Boolean,
): List<ClosedFloatingPointRange<Float>> {
    val (start, length) =
        when (side) {
            BoxSide.TOP, BoxSide.BOTTOM -> bounds.origin.x to bounds.size.width
            BoxSide.LEFT, BoxSide.RIGHT -> bounds.origin.y to bounds.size.height
        }
    val lo = start + ANCHOR_CORNER_INSET_PX
    val hi = start + length - ANCHOR_CORNER_INSET_PX
    if (hi <= lo) {
        val mid = start + length / 2f
        return listOf(mid..mid)
    }
    val whole = lo..hi
    if (side != BoxSide.RIGHT || !hasSelfLoops) return listOf(whole)
    val bandLo = bounds.origin.y + bounds.size.height * SELF_LOOP_EXIT_FRACTION - SELF_LOOP_ANCHOR_CLEARANCE_PX
    val bandHi = bounds.origin.y + bounds.size.height * SELF_LOOP_RETURN_FRACTION + SELF_LOOP_ANCHOR_CLEARANCE_PX
    val pieces = mutableListOf<ClosedFloatingPointRange<Float>>()
    if (minOf(hi, bandLo) > lo) pieces += lo..minOf(hi, bandLo)
    if (hi > maxOf(lo, bandHi)) pieces += maxOf(lo, bandHi)..hi
    return pieces.ifEmpty { listOf(whole) }
}

/**
 * [count] anchor points on [side] of [bounds], ascending along the side, exactly on the box
 * edge. Anchor `i` sits at `(i + 0.5) * total / count` of the concatenated [usableSpans], so the
 * spacing is uniform inside one range. O(count + ranges); an empty list for `count == 0`.
 */
internal fun distributeAnchors(
    bounds: Rect,
    side: BoxSide,
    count: Int,
    hasSelfLoops: Boolean,
): List<Point> {
    require(count >= 0) { "count must be >= 0, was $count" }
    if (count == 0) return emptyList()
    val spans = usableSpans(bounds, side, hasSelfLoops)
    val total = spans.sumOf { (it.endInclusive - it.start).toDouble() }.toFloat()
    val x0 = bounds.origin.x
    val y0 = bounds.origin.y
    val x1 = x0 + bounds.size.width
    val y1 = y0 + bounds.size.height

    fun at(pos: Float): Point =
        when (side) {
            BoxSide.TOP -> Point(x = pos, y = y0)
            BoxSide.BOTTOM -> Point(x = pos, y = y1)
            BoxSide.LEFT -> Point(x = x0, y = pos)
            BoxSide.RIGHT -> Point(x = x1, y = pos)
        }
    if (total <= 0f) return List(count) { at(spans.first().start) }
    val out = ArrayList<Point>(count)
    var spanIndex = 0
    var spanStartOffset = 0f
    for (i in 0 until count) {
        val offset = (i + 0.5f) * total / count
        while (spanIndex < spans.lastIndex &&
            offset > spanStartOffset + (spans[spanIndex].endInclusive - spans[spanIndex].start)
        ) {
            spanStartOffset += spans[spanIndex].endInclusive - spans[spanIndex].start
            spanIndex++
        }
        val span = spans[spanIndex]
        val local = (offset - spanStartOffset).coerceIn(0f, span.endInclusive - span.start)
        out += at(span.start + local)
    }
    return out
}

/**
 * [raw] resampled to [BOW_SAMPLES] points and shifted so it starts exactly on [startAnchor] and
 * ends exactly on [endAnchor]: each point moves by the linear blend of the start and end
 * displacement (`t` = index fraction, i.e. arc-length fraction since the samples are even).
 */
internal fun deformRoute(
    raw: List<Point>,
    startAnchor: Point,
    endAnchor: Point,
): List<Point> {
    val sampled = resampleEvenlyByArcLength(raw, BOW_SAMPLES)
    val dsx = startAnchor.x - sampled.first().x
    val dsy = startAnchor.y - sampled.first().y
    val dex = endAnchor.x - sampled.last().x
    val dey = endAnchor.y - sampled.last().y
    val last = (sampled.size - 1).coerceAtLeast(1)
    return sampled.mapIndexed { i, p ->
        val t = i / last.toFloat()
        Point(x = p.x + dsx + (dex - dsx) * t, y = p.y + dsy + (dey - dsy) * t)
    }
}

/** Wave amplitude for a wavy edge inside a bundle: never larger than the single-edge amplitude, and at most 40% of the neighbouring anchor spacing. */
internal fun groupWaveAmplitudePx(anchorSpacingPx: Float): Float =
    minOf(WAVE_AMPLITUDE_PX, GROUP_WAVE_AMPLITUDE_FACTOR * anchorSpacingPx)
