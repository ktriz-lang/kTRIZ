package dev.ktriz.render.kuml

import dev.ktriz.function.FunctionModel
import dev.kuml.layout.EdgeId
import dev.kuml.layout.LayoutResult
import dev.kuml.layout.NodeId
import dev.kuml.layout.Point
import dev.kuml.layout.Rect
import kotlin.math.hypot

/**
 * A finished route for one edge of a multi-edge bundle: [points] start and end exactly on
 * distributed anchors; [anchorSpacingPx] is the distance to the nearest neighbouring anchor at
 * either end ([Float.MAX_VALUE] if the edge has no neighbour), used to size wavy strokes.
 */
internal data class BundleRoute(
    val points: List<Point>,
    val anchorSpacingPx: Float,
)

private data class SlotEntry(
    val groupAxisPos: Float,
    val groupIndex: Int,
    val lateral: Float,
    val edgeIndex: Int,
    val isStart: Boolean,
)

private fun Rect.center(): Point = Point(x = origin.x + size.width / 2f, y = origin.y + size.height / 2f)

private fun Point.axisCoordinate(side: BoxSide): Float =
    when (side) {
        BoxSide.TOP, BoxSide.BOTTOM -> x
        BoxSide.LEFT, BoxSide.RIGHT -> y
    }

private fun majoritySide(sides: List<BoxSide>): BoxSide {
    val counts = IntArray(BoxSide.entries.size)
    sides.forEach { counts[it.ordinal]++ }
    var best = 0
    for (i in counts.indices) if (counts[i] > counts[best]) best = i
    return BoxSide.entries[best]
}

/**
 * Routes every edge of every bundle (a [pairGroups] group of two or more edges) between its
 * component pair so the lines stay individually readable, even for dense bundles:
 *
 * 1. Per node and bundle, the side the bundle docks to is the majority of
 *    [nearestSide] over its raw ELK endpoints (one side per bundle and node, so a bundle cannot
 *    split and twist).
 * 2. All bundle endpoints on one node side get distinct anchors from [distributeAnchors]
 *    (self-loop band excluded via [selfLoopNodes]). Bundles are ordered along the side by their
 *    raw position, edges inside a bundle by their canonical lateral offset
 *    ([bowOffsetFor] of the edge's rank, forward edges first) -- the same offset at both ends, in
 *    the same frame, so no two lines of one bundle cross at the ends.
 * 3. The raw route is deformed onto the two anchors ([deformRoute]) and bowed by the usual
 *    perpendicular offset.
 *
 * Complexity is O(E log E): one sort per node side, no per-edge scans of other edges.
 */
internal fun FunctionModel.bundleRoutes(
    result: LayoutResult,
    groups: List<List<Int>>,
    selfLoopNodes: Set<String>,
): Map<Int, BundleRoute> {
    val bundleGroups = groups.filter { it.size >= 2 }
    if (bundleGroups.isEmpty()) return emptyMap()

    data class Prepared(
        val edgeIndex: Int,
        val raw: List<Point>,
        val rank: Int,
        val groupSize: Int,
        val forward: Boolean,
    )

    // bucket: (node, side) -> entries; side per (group, node) is fixed before any entry is made.
    val buckets = LinkedHashMap<Pair<String, BoxSide>, MutableList<SlotEntry>>()
    val prepared = LinkedHashMap<Int, Prepared>()
    val fallback = HashSet<Int>()

    bundleGroups.forEachIndexed { groupIndex, group ->
        val lo = edges[group.first()].let { minOf(it.from.name, it.to.name) }
        val hi = edges[group.first()].let { maxOf(it.from.name, it.to.name) }
        val loBounds = result.nodes[NodeId(lo)]?.bounds
        val hiBounds = result.nodes[NodeId(hi)]?.bounds
        // Forward edges (from == alphabetically smaller name) first, declaration order within.
        val ranked = group.sortedBy { if (edges[it].from.name == lo) 0 else 1 }
        val valid = ArrayList<Prepared>()
        ranked.forEachIndexed { rank, index ->
            val raw = result.edges[EdgeId("e$index")]?.polylinePoints() ?: return@forEachIndexed
            if (raw.size < 2) return@forEachIndexed
            val p = Prepared(index, raw, rank, group.size, edges[index].from.name == lo)
            valid += p
            prepared[index] = p
        }
        if (loBounds == null || hiBounds == null) {
            valid.forEach { fallback += it.edgeIndex }
            return@forEachIndexed
        }
        val nrm = loBounds.center().let { c -> hiBounds.center().let { Point(x = -(it.y - c.y), y = it.x - c.x) } }

        for (node in listOf(lo, hi)) {
            val bounds = if (node == lo) loBounds else hiBounds
            val ends =
                valid.map { p ->
                    val isStart = edges[p.edgeIndex].from.name == node
                    p to (if (isStart) p.raw.first() else p.raw.last())
                }
            if (ends.isEmpty()) continue
            val side = majoritySide(ends.map { nearestSide(it.second, bounds) })
            val groupAxisPos = ends.sumOf { it.second.axisCoordinate(side).toDouble() }.toFloat() / ends.size
            val axisComponent = if (side == BoxSide.TOP || side == BoxSide.BOTTOM) nrm.x else nrm.y
            val axisSign = if (axisComponent >= 0f) 1f else -1f
            val list = buckets.getOrPut(node to side) { ArrayList() }
            ends.forEach { (p, _) ->
                list +=
                    SlotEntry(
                        groupAxisPos = groupAxisPos,
                        groupIndex = groupIndex,
                        lateral = bowOffsetFor(p.rank, p.groupSize) * axisSign,
                        edgeIndex = p.edgeIndex,
                        isStart = edges[p.edgeIndex].from.name == node,
                    )
            }
        }
    }

    val anchorOf = HashMap<Pair<Int, Boolean>, Point>()
    val spacingOf = HashMap<Pair<Int, Boolean>, Float>()
    buckets.forEach { (key, entries) ->
        val (node, side) = key
        val bounds = result.nodes[NodeId(node)]?.bounds ?: return@forEach
        val sorted =
            entries.sortedWith(
                compareBy<SlotEntry> { it.groupAxisPos }
                    .thenBy { it.groupIndex }
                    .thenBy { it.lateral }
                    .thenBy { it.edgeIndex },
            )
        val anchors = distributeAnchors(bounds, side, sorted.size, node in selfLoopNodes)
        sorted.forEachIndexed { i, entry ->
            val slot = entry.edgeIndex to entry.isStart
            anchorOf[slot] = anchors[i]
            val prev = if (i > 0) distanceBetween(anchors[i - 1], anchors[i]) else Float.MAX_VALUE
            val next = if (i < anchors.lastIndex) distanceBetween(anchors[i], anchors[i + 1]) else Float.MAX_VALUE
            spacingOf[slot] = minOf(prev, next)
        }
    }

    val out = LinkedHashMap<Int, BundleRoute>()
    prepared.values.forEach { p ->
        val sign = if (p.forward) 1f else -1f
        val offset = bowOffsetFor(p.rank, p.groupSize) * sign
        val startAnchor = anchorOf[p.edgeIndex to true]
        val endAnchor = anchorOf[p.edgeIndex to false]
        if (p.edgeIndex in fallback || startAnchor == null || endAnchor == null) {
            out[p.edgeIndex] =
                BundleRoute(resampleEvenlyByArcLength(p.raw, BOW_SAMPLES).bowOffset(offset), Float.MAX_VALUE)
        } else {
            val spacing = minOf(spacingOf.getValue(p.edgeIndex to true), spacingOf.getValue(p.edgeIndex to false))
            out[p.edgeIndex] = BundleRoute(deformRoute(p.raw, startAnchor, endAnchor).bowOffset(offset), spacing)
        }
    }
    return out
}

private fun distanceBetween(
    a: Point,
    b: Point,
): Float = hypot(b.x - a.x, b.y - a.y)
