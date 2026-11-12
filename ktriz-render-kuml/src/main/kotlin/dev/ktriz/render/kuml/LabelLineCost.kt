package dev.ktriz.render.kuml

import dev.ktriz.function.FunctionModel
import dev.kuml.layout.Point
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * One foreign edge a verb label should avoid: its base polyline plus the half-width its stroke
 * actually occupies ([inflatePx]: wave amplitude, half the double-stroke gap, half the stroke
 * width). Wave and double-stroke offsets are not part of the base geometry, so the label box is
 * grown per obstacle instead of reconstructing exact paths -- a deliberate approximation.
 */
internal class LineObstacle(
    val edgeIndex: Int,
    val points: List<Point>,
    val inflatePx: Float,
) {
    val minX: Float = points.minOf { it.x }
    val maxX: Float = points.maxOf { it.x }
    val minY: Float = points.minOf { it.y }
    val maxY: Float = points.maxOf { it.y }
}

/**
 * The obstacles of every edge in [geometry], in ascending edge-index order (deterministic: no
 * map-iteration-order dependency). Edges without a (non-empty) base polyline are skipped.
 */
internal fun FunctionModel.lineObstacles(geometry: EdgeGeometry): List<LineObstacle> =
    edges.indices.mapNotNull { j ->
        val pts = geometry.bases[j]?.takeIf { it.size >= 2 } ?: return@mapNotNull null
        val style = edges[j].quality.strokeStyle()
        val amplitude = if (style == EdgeStrokeStyle.WAVY) geometry.waveAmplitude[j] ?: WAVE_AMPLITUDE_PX else 0f
        val doubled = if (style == EdgeStrokeStyle.DOUBLED) DOUBLE_STROKE_GAP_PX / 2f else 0f
        LineObstacle(j, pts, amplitude + doubled + STROKE_WIDTH_PX / 2f)
    }

/**
 * Total length of every foreign (index != [excludeIndex]) obstacle polyline that lies inside
 * [labelBox], each obstacle's box grown by its own [LineObstacle.inflatePx]. Pure and
 * deterministic; a cheap bounding-box reject keeps it near-linear for large models.
 */
internal fun List<LineObstacle>.lengthInside(
    labelBox: LabelBox,
    excludeIndex: Int,
): Float {
    var total = 0.0
    for (o in this) {
        if (o.edgeIndex == excludeIndex) continue
        val box = labelBox.inflate(o.inflatePx)
        if (o.maxX < box.minX || o.minX > box.maxX || o.maxY < box.minY || o.minY > box.maxY) continue
        for (s in 0 until o.points.size - 1) {
            total += clipSegmentLength(o.points[s], o.points[s + 1], box)
        }
    }
    return total.toFloat()
}

/**
 * Length of the part of segment [a]-[b] inside [box] (Liang-Barsky clipping). Returns `0f` for
 * non-finite input, a segment entirely outside, or a zero-length segment; a segment parallel to a
 * box side is handled by the sign test on its offset.
 */
internal fun clipSegmentLength(
    a: Point,
    b: Point,
    box: LabelBox,
): Float {
    val x0 = a.x.toDouble()
    val y0 = a.y.toDouble()
    val dx = b.x.toDouble() - x0
    val dy = b.y.toDouble() - y0
    if (!(x0.isFinite() && y0.isFinite() && dx.isFinite() && dy.isFinite())) return 0f
    var t0 = 0.0
    var t1 = 1.0
    val p = doubleArrayOf(-dx, dx, -dy, dy)
    val q = doubleArrayOf(x0 - box.minX, box.maxX - x0, y0 - box.minY, box.maxY - y0)
    for (k in 0 until 4) {
        if (p[k] == 0.0) {
            if (q[k] < 0.0) return 0f
        } else {
            val r = q[k] / p[k]
            if (p[k] < 0.0) t0 = max(t0, r) else t1 = min(t1, r)
            if (t0 > t1) return 0f
        }
    }
    val len = sqrt(dx * dx + dy * dy)
    return ((t1 - t0) * len).toFloat().takeIf { it.isFinite() } ?: 0f
}
