package dev.ktriz.sufield

/**
 * How a [SuField] is judged: does a complete substance-field triangle exist, and if so, how
 * well does the field's effect on S2 serve the model's purpose.
 *
 * A closed, compile-time-known set of five judgements from the classical Su-Field
 * literature -- `enum`, following the same reasoning as
 * [dev.ktriz.function.FunctionQuality] and [dev.ktriz.core.EngineeringParameter]: existence
 * is a type-system guarantee, not a runtime check.
 *
 * Plain `enum` entries without ids/labels, unlike [dev.ktriz.core.EngineeringParameter] or
 * [FieldType] -- there is no external matrix or lookup table indexed by these values (yet),
 * so no id/label carrying is needed for V1.
 */
enum class SuFieldQuality {
    /** A complete S1-S2-Field triangle exists and the field's effect on S2 is right-sized. */
    COMPLETE,

    /** The triangle is structurally incomplete -- S2 or the field (or both) is missing. */
    INCOMPLETE,

    /** A complete triangle exists, but the field's effect on S2 is too weak. */
    INSUFFICIENT,

    /** A complete triangle exists, but the field's effect on S2 is stronger than needed. */
    EXCESSIVE,

    /** A complete triangle exists, and the field's effect on S2 actively works against it. */
    HARMFUL,
}
