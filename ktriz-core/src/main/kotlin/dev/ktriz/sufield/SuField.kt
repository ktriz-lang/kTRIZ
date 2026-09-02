package dev.ktriz.sufield

import dev.ktriz.dsl.KtrizDsl
import dev.ktriz.function.Component

/**
 * A TRIZ substance-field (Su-Field) model: an object substance [s1], optionally acted on by
 * a second substance [s2] through a [field], judged overall by [quality].
 *
 * `dev.ktriz.sufield` depends on `dev.ktriz.function` for [Component] -- a deliberate,
 * one-way reuse (no cycle back the other way) rather than a duplicate `Component`-shaped
 * type. This inherits the same accepted V1 gap documented on
 * [dev.ktriz.function.FunctionModelBuilder]'s KDoc, section "A `Component` built outside
 * `component()`": because [Component]'s constructor is public, a [SuField] can be
 * constructed with an `s1`/`s2` that was never passed through [SuFieldBuilder.component] on
 * this (or any) builder.
 *
 * ## Nullable `s2`/`field`, and the [quality] consistency rule this constructor enforces
 *
 * Classical Su-Field analysis starts from an *incomplete* triangle -- often just S1, with no
 * second substance and no field yet identified -- and treats "what to add" as the analytical
 * question. [s2] and [field] are therefore nullable, and [init] enforces the one invariant
 * that keeps [quality] truthful about which shape is actually present:
 *
 *  * [SuFieldQuality.INCOMPLETE] requires the triangle to actually be incomplete -- at least
 *    one of [s2]/[field] must be `null`. Declaring `INCOMPLETE` while both are set would
 *    contradict the model's own claim about its shape.
 *  * Every other [quality] ([SuFieldQuality.COMPLETE], [SuFieldQuality.INSUFFICIENT],
 *    [SuFieldQuality.EXCESSIVE], [SuFieldQuality.HARMFUL]) requires both [s2] and [field] to
 *    be set -- judging the *strength* or *harmfulness* of an effect presupposes a
 *    structurally complete triangle to judge in the first place. This mirrors
 *    [dev.ktriz.function.FunctionEdge]'s [dev.ktriz.function.FunctionQuality]: `USEFUL`/
 *    `INSUFFICIENT`/`EXCESSIVE`/`HARMFUL` always presuppose a real `from`/`to` pair.
 *
 * @throws IllegalArgumentException if [quality] and the nullability of [s2]/[field]
 * contradict each other, as described above.
 */
data class SuField(
    val s1: Component,
    val s2: Component?,
    val field: FieldType?,
    val quality: SuFieldQuality,
) {
    init {
        if (quality == SuFieldQuality.INCOMPLETE) {
            require(s2 == null || field == null) {
                "SuFieldQuality.INCOMPLETE requires the triangle to actually be incomplete " +
                    "(s2 == null or field == null), but both s2='${s2?.name}' and " +
                    "field=$field were set. Use SuFieldQuality.COMPLETE (or one of " +
                    "INSUFFICIENT/EXCESSIVE/HARMFUL) if the triangle really is complete, or " +
                    "actually leave s2/field unset if it is not."
            }
        } else {
            require(s2 != null && field != null) {
                "quality=$quality requires a structurally complete triangle (both s2 and " +
                    "field set), but s2=${s2?.name} and field=$field. Judging strength or " +
                    "harmfulness presupposes a complete S1-S2-Field triangle to judge -- " +
                    "either set both s2(...) and field(...), or use " +
                    "SuFieldQuality.INCOMPLETE if the triangle really is incomplete."
            }
        }
    }
}

/**
 * Scoped builder behind the [suField] DSL entry point. Not meant to be constructed
 * directly -- use [suField].
 *
 * A setter-style builder, unlike [dev.ktriz.function.FunctionModelBuilder]'s verb-accumulator
 * shape: a [SuField] is a single four-slot record, not a graph of edges, so `s1`/`s2`/
 * `field`/`quality` each set one slot rather than appending to a list. The last call to any
 * of them wins if called more than once -- ordinary `var`-setter semantics, no guard.
 *
 * [component] mirrors [dev.ktriz.function.FunctionModelBuilder.component]'s dedup-by-name
 * behaviour (same [LinkedHashMap.getOrPut] mechanism) for stylistic consistency and so a
 * Su-Field block can freely re-mention `component("X")` without minting a duplicate node --
 * but the two builders' registries are **independent**: a name declared in one `suField { }`
 * block and the same name declared in a separate `functionModel { }` block yield
 * `equals()`-equal but not identical [Component] instances. A shared, cross-builder registry
 * is a deliberate non-goal of this wave -- see this module's design notes for the reasoning
 * (it would need either global mutable state or a registry threaded through both DSL entry
 * points, both of which are premature ahead of any actual use case that composes the two
 * DSLs in one script). Unlike [dev.ktriz.function.FunctionModel], [SuField] itself carries no
 * `components` list, so this builder's [component] map exists only for `s1`/`s2` dedup
 * convenience during the block and is discarded once [build] returns.
 */
@KtrizDsl
class SuFieldBuilder {
    private val components = LinkedHashMap<String, Component>()
    private var s1: Component? = null
    private var s2: Component? = null
    private var field: FieldType? = null
    private var quality: SuFieldQuality? = null

    /** Declares (or re-references) a component by [name]. Idempotent -- see this class's KDoc. */
    fun component(name: String): Component = components.getOrPut(name) { Component(name) }

    /** Sets the object substance S1. Last call wins if called more than once. */
    fun s1(component: Component) {
        this.s1 = component
    }

    /** Sets the second substance S2. Last call wins if called more than once. */
    fun s2(component: Component) {
        this.s2 = component
    }

    /** Sets the field acting between S1 and S2. Last call wins if called more than once. */
    fun field(type: FieldType) {
        this.field = type
    }

    /** Sets the model's overall [SuFieldQuality]. Last call wins if called more than once. */
    fun quality(quality: SuFieldQuality) {
        this.quality = quality
    }

    /**
     * Deliberately `internal`, not `public`: unlike
     * [dev.ktriz.function.FunctionModelBuilder.build], nothing outside `ktriz-core` needs to
     * call this directly -- `ktriz-tests` only ever goes through [suField].
     *
     * @throws IllegalStateException if [s1] or [quality] was never set.
     */
    internal fun build(): SuField {
        val s1 = checkNotNull(s1) { "suField { } requires s1(...) -- a Su-Field always needs an object substance S1." }
        val quality =
            checkNotNull(quality) {
                "suField { } requires quality(...) -- declare the model's SuFieldQuality explicitly."
            }
        return SuField(s1 = s1, s2 = s2, field = field, quality = quality)
    }
}

/**
 * DSL entry point. Builds a [SuField] from an `s1`/`s2`/`field`/`quality` block.
 *
 * ```kotlin
 * val sf = suField {
 *     val workpiece = component("Workpiece")
 *     val tool = component("Grinding wheel")
 *     s1(workpiece)
 *     s2(tool)
 *     field(FieldType.MECHANICAL)
 *     quality(SuFieldQuality.INSUFFICIENT)
 * }
 * ```
 *
 * @throws IllegalStateException if the block never calls `s1(...)` or `quality(...)`.
 * @throws IllegalArgumentException if the declared [SuFieldQuality] contradicts the
 * nullability of `s2`/`field` -- see [SuField]'s KDoc.
 */
fun suField(block: SuFieldBuilder.() -> Unit): SuField = SuFieldBuilder().apply(block).build()
