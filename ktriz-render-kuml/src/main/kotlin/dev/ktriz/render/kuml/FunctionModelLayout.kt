package dev.ktriz.render.kuml

import dev.ktriz.function.FunctionModel
import dev.kuml.layout.EdgeId
import dev.kuml.layout.EndpointRef
import dev.kuml.layout.LayoutEdge
import dev.kuml.layout.LayoutGraph
import dev.kuml.layout.LayoutHints
import dev.kuml.layout.LayoutNode
import dev.kuml.layout.NodeId

/**
 * All component names this [FunctionModel] needs a layout node for, in first-seen order,
 * deduped: [FunctionModel.components]' own names first, then any additional names an edge
 * references that never went through [dev.ktriz.function.FunctionModelBuilder.component] --
 * the "ghost component" case documented on [dev.ktriz.function.FunctionModelBuilder]. Without
 * this union, an edge pointing at a name absent from [FunctionModel.components] would become
 * a [LayoutEdge] whose endpoint has no matching [LayoutNode], which kUML's ELK graph builder
 * silently drops the edge for (endpoint resolution returns null and the edge is skipped)
 * rather than rendering it.
 */
internal fun FunctionModel.allNodeNames(): List<String> {
    val names = LinkedHashSet<String>()
    components.forEach { names += it.name }
    edges.forEach {
        names += it.from.name
        names += it.to.name
    }
    return names.toList()
}

/**
 * Converts this [FunctionModel] into a generic [LayoutGraph] for
 * [dev.kuml.layout.elk.ElkLayoutEngine] -- kUML sees only nodes/edges/geometry here, never
 * TRIZ semantics (kTRIZ-ADR-0002, "Update 2026-08-13"). Node intrinsic size comes from
 * [measureNodeSize] (see `NodeSizing.kt`) -- real, headless-safe font-metrics measurement of
 * the component name, padded, snapped to a width grid and clamped between a minimum and
 * maximum width, rather than a fixed estimate. A node that carries a multi-edge bundle is
 * additionally grown so the bundle's endpoints fit with room between them (see
 * [groupedEdgeCounts] and [measureNodeSize]).
 */
internal fun FunctionModel.toLayoutGraph(hints: LayoutHints = LayoutHints.DEFAULT): LayoutGraph {
    val grouped = groupedEdgeCounts()
    val nodes =
        allNodeNames().map { name ->
            LayoutNode(
                id = NodeId(name),
                intrinsicSize = measureNodeSize(name, grouped[name] ?: 0, hints.direction),
            )
        }
    val layoutEdges =
        edges.mapIndexed { index, edge ->
            LayoutEdge(
                id = EdgeId("e$index"),
                source = EndpointRef(nodeId = NodeId(edge.from.name)),
                target = EndpointRef(nodeId = NodeId(edge.to.name)),
            )
        }
    return LayoutGraph(nodes = nodes, edges = layoutEdges)
}

/**
 * Edge indices grouped by unordered component pair, self-loops excluded, in first-seen order of
 * the pair. One O(E) `groupBy` pass -- the single source of truth for "which edges bundle",
 * shared by node sizing ([groupedEdgeCounts]) and edge routing so layout and rendering cannot
 * disagree about group membership. A pair with one edge yields a group of size 1.
 */
internal fun FunctionModel.pairGroups(): List<List<Int>> =
    edges
        .withIndex()
        .filter { it.value.from.name != it.value.to.name }
        .groupBy { minOf(it.value.from.name, it.value.to.name) to maxOf(it.value.from.name, it.value.to.name) }
        .values
        .map { group -> group.map { it.index } }

/**
 * Per component name: how many edge endpoints it carries in bundles of two or more edges
 * between the same pair (self-loops excluded). Components without a bundle are absent.
 */
internal fun FunctionModel.groupedEdgeCounts(): Map<String, Int> {
    val counts = HashMap<String, Int>()
    pairGroups().filter { it.size >= 2 }.forEach { group ->
        group.forEach { index ->
            val edge = edges[index]
            counts.merge(edge.from.name, 1, Int::plus)
            counts.merge(edge.to.name, 1, Int::plus)
        }
    }
    return counts
}
