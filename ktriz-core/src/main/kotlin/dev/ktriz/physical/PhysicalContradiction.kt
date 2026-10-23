package dev.ktriz.physical

import dev.ktriz.dsl.KtrizDsl
import dev.ktriz.function.Component

/** Maximum length of [PhysicalContradiction.property], [PhysicalContradiction.state] and
 *  [PhysicalContradiction.oppositeState] (equals the MCP module's `MAX_NAME_LENGTH`). */
const val MAX_PHYSICAL_TEXT_LENGTH = 200

/** Maximum length of the optional because-texts (below the MCP module's 512 string cap). */
const val MAX_PHYSICAL_REASON_LENGTH = 500

private const val ECHO_LIMIT = 64

private fun String.echo(): String = if (length <= ECHO_LIMIT) this else take(ECHO_LIMIT) + "..."

/**
 * A *physical* contradiction: one [property] of one [element] must be in [state] and, at the
 * same time, in the opposite state [oppositeState] (classically: "the knife must be hot and
 * cold"). The analyst may record which [SeparationPrinciple] is meant to resolve it.
 *
 * Two enforcement levels, as with [dev.ktriz.core.Contradiction]:
 *  1. **Existence** lives in the type system -- only a real [SeparationPrinciple] compiles.
 *  2. **Well-formedness** is checked at runtime in [init], with messages that double as repair
 *     signals for the generate-compile-repair loop.
 *
 * kTRIZ cannot check whether two free-text states are semantically *opposite*. It only checks
 * that they are *different* (compared trimmed and case-insensitively). A synonym check would be
 * invented precision.
 *
 * [separation] is a choice declared by the analyst, never computed. [element] reuses
 * [Component] one-way, like [dev.ktriz.sufield.SuField]. This class is not rendered in this wave.
 * The deferral of physical contradictions is recorded in the DSL-surface design note, not in
 * kTRIZ-ADR-0001.
 *
 * Error messages never echo more than 64 characters of a user value.
 *
 * @throws IllegalArgumentException if a text is blank or too long, or if [state] and
 * [oppositeState] are the same.
 */
data class PhysicalContradiction(
    val element: Component,
    val property: String,
    val state: String,
    val oppositeState: String,
    val stateBecause: String? = null,
    val oppositeStateBecause: String? = null,
    val separation: SeparationPrinciple? = null,
) {
    init {
        checkText("element", element.name, MAX_PHYSICAL_TEXT_LENGTH)
        checkText("property", property, MAX_PHYSICAL_TEXT_LENGTH)
        checkText("state", state, MAX_PHYSICAL_TEXT_LENGTH)
        checkText("oppositeState", oppositeState, MAX_PHYSICAL_TEXT_LENGTH)
        stateBecause?.let { checkText("stateBecause", it, MAX_PHYSICAL_REASON_LENGTH) }
        oppositeStateBecause?.let { checkText("oppositeStateBecause", it, MAX_PHYSICAL_REASON_LENGTH) }
        require(state.trim().lowercase() != oppositeState.trim().lowercase()) {
            "A physical contradiction needs two *different* states, but both were " +
                "'${state.echo()}'. State the opposite requirement (e.g. 'high' vs. 'low')."
        }
    }

    private fun checkText(
        field: String,
        value: String,
        max: Int,
    ) {
        require(value.isNotBlank()) { "PhysicalContradiction.$field must not be blank." }
        require(value.length <= max) {
            "PhysicalContradiction.$field exceeds the maximum length of $max characters " +
                "(got ${value.length}; starts with '${value.echo()}')."
        }
    }
}

/**
 * Scoped builder behind [physicalContradiction]. Setter style like
 * [dev.ktriz.sufield.SuFieldBuilder]: the last call wins.
 */
@KtrizDsl
class PhysicalContradictionBuilder {
    private var element: Component? = null
    private var property: String? = null
    private var state: String? = null
    private var stateBecause: String? = null
    private var oppositeState: String? = null
    private var oppositeStateBecause: String? = null
    private var separation: SeparationPrinciple? = null

    /** Sets the element by name and returns it. */
    fun element(name: String): Component = Component(name).also { this.element = it }

    /** Sets the element. */
    fun element(component: Component) {
        this.element = component
    }

    /** Sets the property whose state is contradictory. */
    fun property(name: String) {
        this.property = name
    }

    /** Sets the first required state, optionally with the reason it is needed. */
    fun state(
        value: String,
        because: String? = null,
    ) {
        this.state = value
        this.stateBecause = because
    }

    /** Sets the opposite required state, optionally with the reason it is needed. */
    fun oppositeState(
        value: String,
        because: String? = null,
    ) {
        this.oppositeState = value
        this.oppositeStateBecause = because
    }

    /** Records the separation principle the analyst intends to apply. */
    fun separation(principle: SeparationPrinciple) {
        this.separation = principle
    }

    /** @throws IllegalStateException if element, property, state or oppositeState is missing. */
    internal fun build(): PhysicalContradiction =
        PhysicalContradiction(
            element = checkNotNull(element) { "physicalContradiction { } requires element(...)." },
            property = checkNotNull(property) { "physicalContradiction { } requires property(...)." },
            state = checkNotNull(state) { "physicalContradiction { } requires state(...)." },
            oppositeState = checkNotNull(oppositeState) { "physicalContradiction { } requires oppositeState(...)." },
            stateBecause = stateBecause,
            oppositeStateBecause = oppositeStateBecause,
            separation = separation,
        )
}

/**
 * DSL entry point.
 *
 * ```kotlin
 * val pc = physicalContradiction {
 *     element("Landing gear")
 *     property("Presence")
 *     state("present", because = "needed for landing")
 *     oppositeState("absent", because = "causes drag in flight")
 *     separation(SeparationPrinciple.IN_TIME)
 * }
 * ```
 *
 * @throws IllegalStateException if a required call is missing.
 * @throws IllegalArgumentException see [PhysicalContradiction].
 */
fun physicalContradiction(block: PhysicalContradictionBuilder.() -> Unit): PhysicalContradiction =
    PhysicalContradictionBuilder().apply(block).build()
