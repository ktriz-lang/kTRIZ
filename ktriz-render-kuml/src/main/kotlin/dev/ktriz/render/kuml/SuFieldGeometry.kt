package dev.ktriz.render.kuml

import dev.ktriz.sufield.SuField
import dev.kuml.layout.Point
import kotlin.math.sqrt

internal const val VERTEX_RADIUS_PX = 21f
internal const val BASE_SPAN_PX = 220f // S1<->S2 centre distance
internal const val APEX_RISE_PX = 150f // flattened, not equilateral (~190)
internal const val VERTEX_SYMBOL_FONT_SIZE_PX = 13f
internal const val VERTEX_SUBSCRIPT_FONT_SIZE_PX = 9f
internal const val VERTEX_SUBSCRIPT_DY_PX = 3f
internal const val LABEL_GAP_PX = 8f // circle edge -> outside label
internal const val MAX_LABEL_WIDTH_PX = MAX_NODE_WIDTH_PX // 320f, reused
// Canvas padding reuses EdgeGeometry.kt's CANVAS_MARGIN_PX (4f) -- see SuFieldSvgRenderer.kt's
// SuFieldScene.bounds().

/** The three vertex slots of a Su-Field triangle, in the classical apex/left/right layout. */
internal enum class SuFieldVertex { S1, S2, FIELD }

/**
 * Deterministic local-frame placement (see `SuFieldSvgRenderer.kt`'s module KDoc for why no
 * layout engine is used here). Contains an entry **only for a vertex actually present** -- S1
 * always; S2 iff `s2 != null`; FIELD iff `field != null`. Slots never move: S1 stays at
 * `(-BASE_SPAN_PX / 2, 0)` whether or not S2 exists (an absent vertex must read as a gap, not
 * as a re-centred drawing). This is the single seam a future multi-substance standard-solution
 * model (e.g. an S3 added by standard solution 1.1.2) replaces.
 */
internal fun SuField.vertexPlacement(): Map<SuFieldVertex, Point> {
    val out = LinkedHashMap<SuFieldVertex, Point>()
    out[SuFieldVertex.S1] = Point(x = -BASE_SPAN_PX / 2f, y = 0f)
    if (s2 != null) out[SuFieldVertex.S2] = Point(x = BASE_SPAN_PX / 2f, y = 0f)
    if (field != null) out[SuFieldVertex.FIELD] = Point(x = 0f, y = -APEX_RISE_PX)
    return out
}

/**
 * `[from]`->`[to]` trimmed by [radiusPx] at both ends so a stroke meets the circle edge, not
 * its centre -- e.g. `trimmedSegment(s2Point, s1Point)` for the Su-Field base, or
 * `trimmedSegment(fieldPoint, s1Point)` for a leg. Guards a zero-length input (coincident
 * points -> returns the two points untrimmed) even though this module's fixed vertex geometry
 * can never actually produce one.
 */
internal fun trimmedSegment(
    from: Point,
    to: Point,
    radiusPx: Float = VERTEX_RADIUS_PX,
): List<Point> {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = sqrt(dx * dx + dy * dy)
    if (len <= 0f) return listOf(from, to)
    val ux = dx / len
    val uy = dy / len
    val trimmedFrom = Point(x = from.x + ux * radiusPx, y = from.y + uy * radiusPx)
    val trimmedTo = Point(x = to.x - ux * radiusPx, y = to.y - uy * radiusPx)
    return listOf(trimmedFrom, trimmedTo)
}
