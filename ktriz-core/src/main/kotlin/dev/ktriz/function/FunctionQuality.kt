package dev.ktriz.function

/**
 * How a [FunctionEdge] is judged in classical TRIZ function analysis: does the effect help
 * or hurt the receiving [Component], and is its magnitude right-sized for the job.
 *
 * A closed, compile-time-known set of four judgements from the TRIZ function-analysis
 * literature -- `enum`, following the same reasoning as [dev.ktriz.core.EngineeringParameter]
 * and [dev.ktriz.core.InventivePrinciple]: existence is a type-system guarantee, not a
 * runtime check.
 */
enum class FunctionQuality {
    /** The effect does what it is meant to do, at the right strength. */
    USEFUL,

    /** The effect actively works against the system's purpose. */
    HARMFUL,

    /** A useful effect, but too weak to do its job. */
    INSUFFICIENT,

    /** A useful effect, but stronger than the job requires (and therefore wasteful or harmful). */
    EXCESSIVE,
}
