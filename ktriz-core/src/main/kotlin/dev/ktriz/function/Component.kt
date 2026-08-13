package dev.ktriz.function

/**
 * One node in a [FunctionModel]: a named part of the system (or its environment) that
 * performs or receives functions.
 *
 * Deliberately just a name -- no id, type, or hierarchy. V1's function model is a flat
 * component graph. Identity is by [name] (data class equality), which is also why
 * [FunctionModelBuilder.component] dedups by name instead of minting a new instance per call.
 *
 * The primary constructor is public, matching the plain-data style of the rest of
 * `ktriz-core`. This means a caller *can* construct a `Component` without going through
 * [FunctionModelBuilder.component] and pass it to an edge helper -- see
 * [FunctionModelBuilder]'s KDoc, section "A `Component` built outside `component()`", for
 * why that is an accepted, tested V1 limitation rather than something this class guards
 * against.
 */
data class Component(
    val name: String,
)
