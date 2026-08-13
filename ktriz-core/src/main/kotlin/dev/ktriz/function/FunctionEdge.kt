package dev.ktriz.function

/**
 * One functional relationship in a [FunctionModel]: [from] performs [verb] on [to], judged
 * as [quality].
 *
 * `from == to` (a component acting on itself) is allowed on purpose -- see
 * [FunctionModelBuilder]'s KDoc, section "Self-loops are allowed", for why this is unlike
 * [dev.ktriz.core.Contradiction], which does runtime-reject its equivalent degenerate case.
 */
data class FunctionEdge(
    val from: Component,
    val to: Component,
    val quality: FunctionQuality,
    val verb: String,
)
