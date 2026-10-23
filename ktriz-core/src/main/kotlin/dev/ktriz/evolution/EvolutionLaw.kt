package dev.ktriz.evolution

import dev.ktriz.evolution.EvolutionLawGroup.DYNAMICS
import dev.ktriz.evolution.EvolutionLawGroup.KINEMATICS
import dev.ktriz.evolution.EvolutionLawGroup.STATICS

/**
 * The eight classical laws of technical systems evolution (Altshuller), grouped into statics,
 * kinematics and dynamics (3 + 3 + 2).
 *
 * Same design rationale as [dev.ktriz.physical.SeparationPrinciple]: a closed, compile-time-known
 * set, therefore an `enum`; [id] is the canonical 1..8 index; existence of a law is a
 * compiler-enforced guarantee (Typed Domain Grounding).
 *
 * Deliberately modelled as-is:
 * - **No `labelDe`**: no verified German source was checked (same stance as
 *   [dev.ktriz.sufield.StandardSolution]).
 * - **No ninth law**: MATRIZ additionally lists an "increase of dynamics" law, but that is a single
 *   source, so it is documented only in the provenance record and is not an enum value.
 * - **No evolution lines, patterns or TESE trends, and no application DSL.** Their number and
 *   naming differ between sources; see `docs/evolution-laws-provenance.adoc`.
 *
 * The enum symbols appear in MCP results and are therefore API.
 */
enum class EvolutionLaw(
    val id: Int,
    val labelEn: String,
    val group: EvolutionLawGroup,
    val description: String,
) {
    COMPLETENESS_OF_PARTS(
        1,
        "Law of completeness of parts",
        STATICS,
        "A viable system needs four working elements: an engine, a transmission, a working unit and " +
            "a control. If one is missing, the system cannot do its job on its own.",
    ),
    ENERGY_CONDUCTIVITY(
        2,
        "Law of energy conductivity",
        STATICS,
        "Energy has to be able to flow without obstruction through every part of the system, from " +
            "its source to the working unit. Blocked or lossy paths hold the system back.",
    ),
    HARMONIZATION_OF_RHYTHMS(
        3,
        "Law of harmonization of rhythms",
        STATICS,
        "The cycles, frequencies and timing of the parts have to be matched to one another. " +
            "Parts that work out of step reduce what the whole can achieve.",
    ),
    INCREASING_IDEALITY(
        4,
        "Law of increasing ideality",
        KINEMATICS,
        "Over time a system delivers more useful function while its cost, harm and resource " +
            "consumption shrink. The limit is the function without any system at all.",
    ),
    UNEVEN_DEVELOPMENT_OF_PARTS(
        5,
        "Law of uneven development of parts",
        KINEMATICS,
        "Different parts of a system mature at different speeds. The part that lags behind limits " +
            "the whole system and is where contradictions arise.",
    ),
    TRANSITION_TO_SUPERSYSTEM(
        6,
        "Law of transition to the supersystem",
        KINEMATICS,
        "Once a system has exhausted its own development, it is absorbed as one part into a larger " +
            "system, and further progress happens at that higher level.",
    ),
    TRANSITION_TO_MICRO_LEVEL(
        7,
        "Law of transition from macro to micro level",
        DYNAMICS,
        "Working units move from large bodies to ever finer structures, down to molecules, atoms " +
            "or fields, to gain performance.",
    ),
    INCREASING_SUBSTANCE_FIELD_INVOLVEMENT(
        8,
        "Law of increasing substance-field involvement",
        DYNAMICS,
        "Systems evolve toward more substance-field interactions that are also easier to control, " +
            "replacing mechanical effects with better controllable fields.",
    ),
    ;

    companion object {
        /** @throws IllegalStateException if [id] is outside 1..8. */
        fun ofId(id: Int): EvolutionLaw =
            entries.firstOrNull { it.id == id }
                ?: error("No EvolutionLaw with id $id; valid ids are 1..${entries.size}.")
    }
}
