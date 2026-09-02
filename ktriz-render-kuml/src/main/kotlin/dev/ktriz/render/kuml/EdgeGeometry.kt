package dev.ktriz.render.kuml

import dev.kuml.layout.EdgeRoute
import dev.kuml.layout.Point
import dev.kuml.layout.Rect
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal const val WAVE_AMPLITUDE_PX = 6f
internal const val WAVE_WAVELENGTH_PX = 24f
internal const val WAVE_STEP_PX = 4f

internal const val BOW_SAMPLES = 17
internal const val BOW_SPACING_PX = 9f
internal const val BOW_MAX_OFFSET_PX = 27f
internal const val SELF_LOOP_BASE_RADIUS_PX = 20f
internal const val SELF_LOOP_HEIGHT_FACTOR = 0.2f
internal const val SELF_LOOP_HEIGHT_OFFSET_PX = 12f
internal const val SELF_LOOP_STACK_STEP_PX = 8f
internal const val SELF_LOOP_STACK_MAX_PX = 44f
internal const val SELF_LOOP_SAMPLES = 24
internal const val SELF_LOOP_EXIT_FRACTION = 0.30f
internal const val SELF_LOOP_RETURN_FRACTION = 0.70f
internal const val CANVAS_MARGIN_PX = 4f
internal const val EDGE_LABEL_BASE_OFFSET_PX = 4f
internal const val EDGE_LABEL_STACK_STEP_PX = 13f

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
 * Only ever called when a component pair has two or more edges -- see
 * `FunctionModelSvgRenderer.kt`'s `computeEdgeBases`, which keeps a single edge on its raw
 * ELK route (2 points for an [EdgeRoute.Direct] route) instead of resampling it, so the
 * existing edge-label-midpoint test (which expects exactly 4 coordinates from a `Direct`
 * route's `d` attribute) keeps passing.
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

/** Radius for the k-th (0-based) self-loop on the same component -- later loops stack outward, capped. */
internal fun selfLoopRadiusPx(
    nodeHeightPx: Float,
    loopIndex: Int,
): Float {
    val base = maxOf(SELF_LOOP_BASE_RADIUS_PX, nodeHeightPx * SELF_LOOP_HEIGHT_FACTOR + SELF_LOOP_HEIGHT_OFFSET_PX)
    return base + min(loopIndex * SELF_LOOP_STACK_STEP_PX, SELF_LOOP_STACK_MAX_PX)
}

/** Perpendicular bow offset for edge `loopIndex` of `groupSize` sharing the same component pair (`groupSize<=1` -> 0). */
internal fun bowOffsetFor(
    loopIndex: Int,
    groupSize: Int,
): Float {
    if (groupSize <= 1) return 0f
    val raw = (loopIndex - (groupSize - 1) / 2f) * BOW_SPACING_PX
    return raw.coerceIn(-BOW_MAX_OFFSET_PX, BOW_MAX_OFFSET_PX)
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
