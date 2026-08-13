package dev.ktriz.render.kuml

import dev.ktriz.function.FunctionModel
import dev.kuml.layout.EdgeId
import dev.kuml.layout.EndpointRef
import dev.kuml.layout.LayoutEdge
import dev.kuml.layout.LayoutGraph
import dev.kuml.layout.LayoutNode
import dev.kuml.layout.NodeId
import dev.kuml.layout.Size

internal const val NODE_WIDTH_PX = 140f
internal const val NODE_HEIGHT_PX = 60f

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
 * TRIZ semantics (kTRIZ-ADR-0002, "Update 2026-08-13"). Node intrinsic size is a fixed
 * [NODE_WIDTH_PX] x [NODE_HEIGHT_PX] estimate, not measured text metrics -- V1 has no
 * font-measurement dependency; revisit only if real component names in practice overflow
 * the box.
 */
internal fun FunctionModel.toLayoutGraph(): LayoutGraph {
    val nodes =
        allNodeNames().map { name ->
            LayoutNode(id = NodeId(name), intrinsicSize = Size(width = NODE_WIDTH_PX, height = NODE_HEIGHT_PX))
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
