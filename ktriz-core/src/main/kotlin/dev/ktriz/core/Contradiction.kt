package dev.ktriz.core

/**
 * A *technical* contradiction: improving [improving] makes [worsening] worse.
 *
 * Two separate enforcement levels, on purpose:
 *  1. **Existence** lives entirely in the type system -- only a real [EngineeringParameter]
 *     member compiles, so an invented parameter is not a well-formed Kotlin program and
 *     never reaches runtime. No validation code needed.
 *  2. **Well-formedness** (`improving != worsening`) cannot be expressed in the type system,
 *     because both poles share one type, so `X vs. X` type-checks. It is therefore the one
 *     deliberate runtime check, in [init], with a message written to double as a repair
 *     signal for the generate-compile-repair loop.
 *
 * V1 covers technical contradictions only. Physical contradictions (one property having to
 * be in two opposite states at once, resolved via separation principles) were deferred in the
 * DSL-surface design note and live in [dev.ktriz.physical.PhysicalContradiction]; the two
 * classes sit side by side without renaming this one.
 */
data class Contradiction(
    val improving: EngineeringParameter,
    val worsening: EngineeringParameter,
) {
    init {
        require(improving != worsening) {
            "A contradiction needs two *different* parameters, but both were " +
                "'${improving.labelEn}'. A parameter cannot contradict itself."
        }
    }
}

/**
 * DSL entry point for a technical contradiction. Named arguments are mandatory by
 * convention across kTRIZ/kUML/kSTEP -- the direction (`improving` vs. `worsening`) is
 * semantically load-bearing and must never depend on argument position at a call site.
 *
 * @throws IllegalArgumentException if [improving] and [worsening] are the same parameter.
 */
fun contradiction(
    improving: EngineeringParameter,
    worsening: EngineeringParameter,
): Contradiction =
    Contradiction(
        improving = improving,
        worsening = worsening,
    )
