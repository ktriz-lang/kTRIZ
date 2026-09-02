package dev.ktriz.function

import dev.ktriz.dsl.KtrizDsl

/**
 * Scoped builder behind the [functionModel] DSL entry point. Not meant to be constructed
 * directly -- use [functionModel].
 *
 * ## Components are deduped by name, not re-declared
 *
 * [component] is idempotent: calling it twice with the same [name] (via the backing
 * [LinkedHashMap.getOrPut]) returns the *same* [Component] instance, not a second,
 * `equals()`-but-not-identical node. [FunctionModel.components] therefore has exactly one
 * entry per distinct name, in first-seen order -- callers can freely re-mention
 * `component("Engine")` at every edge call site instead of hoisting a `val` for every
 * component, without silently duplicating nodes. The `LinkedHashMap` is load-bearing here:
 * a `HashMap` would make [FunctionModel.components]' order an implementation detail; a
 * `LinkedHashMap` makes "first declared, first in the list" a guarantee tests can pin.
 *
 * ## Self-loops are allowed, unlike [dev.ktriz.core.Contradiction]
 *
 * [dev.ktriz.core.Contradiction] rejects `improving == worsening` at runtime because a
 * parameter contradicting itself is definitionally meaningless. A [Component] acting on
 * itself is different: classical TRIZ function analysis explicitly models self-directed
 * effects (a bearing generating heat *in itself*, a part wearing itself out). kUML's own SVG
 * renderer already has dedicated self-loop routing (`SelfLoopRouter` in
 * `dev.kuml.io.svg`) for exactly this shape of edge in UML/SysML diagrams, which is further
 * evidence this is a legitimate, renderable case, not a mistake to reject. This builder
 * therefore adds no `from == to` check.
 *
 * ## A `Component` built outside `component()`
 *
 * Because [Component]'s constructor is public (see its KDoc), an edge helper accepts a
 * `Component` that was never passed through [component] on this builder. Such a component
 * is recorded on the edge but does **not** appear in [FunctionModel.components]. This is a
 * known, accepted V1 gap -- see the pinning test in `FunctionModelTest` -- rather than
 * something this wave closes; closing it would mean making [Component] unconstructible
 * outside its owning builder, which conflicts with keeping it a plain, publicly usable data
 * class. Revisit only if it causes real confusion in practice.
 */
@KtrizDsl
class FunctionModelBuilder {
    private val components = LinkedHashMap<String, Component>()
    private val edges = mutableListOf<FunctionEdge>()

    /** Declares (or re-references) a component by [name]. Idempotent -- see this class's KDoc. */
    fun component(name: String): Component = components.getOrPut(name) { Component(name) }

    /** Records a [FunctionQuality.USEFUL] edge: [from] performs [verb] on [to] as intended. */
    fun useful(
        from: Component,
        to: Component,
        verb: String,
    ) {
        edges += FunctionEdge(from, to, FunctionQuality.USEFUL, verb)
    }

    /** Records a [FunctionQuality.HARMFUL] edge: [from] performs [verb] on [to], working against the system's purpose. */
    fun harmful(
        from: Component,
        to: Component,
        verb: String,
    ) {
        edges += FunctionEdge(from, to, FunctionQuality.HARMFUL, verb)
    }

    /** Records a [FunctionQuality.INSUFFICIENT] edge: [from] performs [verb] on [to], but too weakly to do its job. */
    fun insufficient(
        from: Component,
        to: Component,
        verb: String,
    ) {
        edges += FunctionEdge(from, to, FunctionQuality.INSUFFICIENT, verb)
    }

    /** Records a [FunctionQuality.EXCESSIVE] edge: [from] performs [verb] on [to], but more strongly than needed. */
    fun excessive(
        from: Component,
        to: Component,
        verb: String,
    ) {
        edges += FunctionEdge(from, to, FunctionQuality.EXCESSIVE, verb)
    }

    /**
     * Deliberately `public`, not `internal`: [ContradictionMatrix.populatedCellCount][dev.ktriz.core.ContradictionMatrix]
     * already documents why an `internal` member here would be invisible from `ktriz-tests`
     * (separate Gradle module, `project()` dependency does not grant cross-module `internal`
     * visibility). Unlike that case, nothing in `ktriz-tests` actually needs to call [build]
     * directly -- it only ever goes through [functionModel] -- so `internal` is safe *here*.
     * Kept `internal` (not `private`) only so a future in-module caller (e.g. a scripting
     * host reusing this builder) is not forced back through the single-lambda entry point.
     */
    internal fun build(): FunctionModel = FunctionModel(components.values.toList(), edges.toList())
}

/**
 * A TRIZ function model: which [components] exist and which [edges] connect them.
 *
 * Plain data, produced only by [functionModel]. Unlike [dev.ktriz.core.Contradiction] there
 * is no invariant to protect here, so no `init` block: any components/edges a
 * [FunctionModelBuilder] can produce are a valid [FunctionModel].
 */
data class FunctionModel(
    val components: List<Component>,
    val edges: List<FunctionEdge>,
)

/**
 * DSL entry point. Builds a [FunctionModel] from a `component`/`useful`/`harmful`/
 * `insufficient`/`excessive` block.
 *
 * ```kotlin
 * val fm = functionModel {
 *     val engine = component("Engine")
 *     val coolant = component("Coolant")
 *     val block = component("Cylinder block")
 *     useful(from = coolant, to = engine, verb = "cools")
 *     harmful(from = engine, to = block, verb = "overheats")
 *     insufficient(from = coolant, to = engine, verb = "circulates")
 * }
 * ```
 */
fun functionModel(block: FunctionModelBuilder.() -> Unit): FunctionModel = FunctionModelBuilder().apply(block).build()
