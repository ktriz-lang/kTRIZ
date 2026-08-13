package dev.ktriz.render.kuml

import dev.kuml.layout.EdgeRoute
import dev.kuml.layout.Point
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

internal const val WAVE_AMPLITUDE_PX = 6f
internal const val WAVE_WAVELENGTH_PX = 24f
internal const val WAVE_STEP_PX = 4f

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
