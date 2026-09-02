package dev.ktriz.sufield

/**
 * The 5-class structure of Altshuller's 76 Standard Solutions for Su-Field analysis.
 *
 * [id] is the canonical 1..5 class index; [labelEn]/[labelDe] are carried for rendering,
 * [description] is a short prose gloss of what the class addresses, and [solutionCount] is
 * how many of the 76 individual standard solutions the classical literature assigns to that
 * class (13/23/6/17/17, summing to 76) -- following the same id+label pattern as [FieldType]
 * and [dev.ktriz.core.EngineeringParameter] (Typed Domain Grounding: existence of a class is
 * a compiler-enforced guarantee, not a runtime check).
 *
 * `enum` and not a `sealed` hierarchy, for the same reason as [FieldType]/
 * [dev.ktriz.core.EngineeringParameter]: a closed set, fully known at compile time, with no
 * instance-specific structure beyond id + labels + description + count.
 *
 * ## Deliberately just the 5-class structure, not the 76 individual solutions
 *
 * kTRIZ bundles only this coarse classification, never the 76 individual solution texts
 * themselves (nor their finer sub-class groupings, e.g. "1.1 Synthesis" / "1.2 Destruction").
 * A pre-implementation source review found no primary-source-quality transcription of the
 * fine-grained 76-item list: the only complete open-source candidate reads like an
 * LLM-generated agent-skill reference document rather than a transcription of an
 * Altshuller-era primary source, a second candidate is missing 9 of the 76 entries outright,
 * and sub-class assignments diverge between what few sources exist. The 5-class structure
 * itself, by contrast, is stable and independently corroborated by all sources checked,
 * including wiki.matriz.org (MATRIZ). Full sourcing record, rejected candidates and reasons:
 * `docs/standard-solutions-provenance.adoc`. Scope decision: kTRIZ-ADR-0001.
 *
 * ## Relationship to [SuFieldQuality] -- deliberately not modeled in the type system
 *
 * There is a loose, informal correspondence between some of these classes and some
 * [SuFieldQuality] values -- roughly, [SYNTHESIS_AND_DESTRUCTION] concerns triangles judged
 * [SuFieldQuality.INCOMPLETE] or [SuFieldQuality.HARMFUL], and [DEVELOPMENT] concerns ones
 * judged [SuFieldQuality.INSUFFICIENT]. But this correspondence is **not** a documented 1:1
 * mapping in the TRIZ literature: [SuFieldQuality.EXCESSIVE] and [SuFieldQuality.COMPLETE]
 * have no clear counterpart among the 5 classes, and [MEASUREMENT_AND_DETECTION] is
 * orthogonal to quality altogether -- it is about whether a Su-Field serves *measurement*,
 * independent of how complete or effective it is. kTRIZ therefore does **not** provide a
 * `suggestStandardSolutionClass(quality: SuFieldQuality)` function or any other
 * type-system-enforced bridge between the two enums: doing so would present uncertain domain
 * knowledge as false precision, which is exactly the failure mode Typed Domain Grounding
 * exists to prevent. A caller who wants to reason about this relationship does so explicitly,
 * in their own code, aware that it is a heuristic, not a rule.
 */
enum class StandardSolutionClass(
    val id: Int,
    val labelEn: String,
    val labelDe: String,
    val description: String,
    val solutionCount: Int,
) {
    SYNTHESIS_AND_DESTRUCTION(
        id = 1,
        labelEn = "Building and destruction of Su-Fields",
        labelDe = "Aufbau und Zerstörung von Su-Feldern",
        description =
            "Addresses an incomplete or harmful Su-Field triangle -- how to build " +
                "one where none exists, or how to eliminate a harmful effect.",
        solutionCount = 13,
    ),
    DEVELOPMENT(
        id = 2,
        labelEn = "Development of Su-Fields",
        labelDe = "Entwicklung von Su-Feldern",
        description =
            "Addresses a structurally complete but insufficiently effective " +
                "Su-Field triangle -- how to strengthen, chain, or better control the effect.",
        solutionCount = 23,
    ),
    TRANSITION_TO_SUPERSYSTEM_OR_MICROLEVEL(
        id = 3,
        labelEn = "Transition to the supersystem and to the micro-level",
        labelDe = "Übergang zum Supersystem und zur Mikroebene",
        description =
            "Addresses solving a problem by moving it out of the current system " +
                "boundary -- up to the supersystem, or down to the micro-level.",
        solutionCount = 6,
    ),
    MEASUREMENT_AND_DETECTION(
        id = 4,
        labelEn = "Standard solutions for measuring and detecting",
        labelDe = "Standard-Lösungen für Messen und Erkennen",
        description =
            "Addresses building or improving a Su-Field whose purpose is " +
                "measurement or detection, independent of that Su-Field's own completeness " +
                "or strength.",
        solutionCount = 17,
    ),
    APPLICATION_STRATEGIES(
        id = 5,
        labelEn = "Application strategies for standard solutions",
        labelDe = "Anwendungsstrategien der Standard-Lösungen",
        description =
            "Addresses how and in what order to apply Standards 1-4 in practice, " +
                "including how to transform a physical problem into a Su-Field model in " +
                "the first place.",
        solutionCount = 17,
    ),
    ;

    companion object {
        private val byId = entries.associateBy(StandardSolutionClass::id)

        /** @throws IllegalStateException if [id] is outside 1..5. */
        fun ofId(id: Int): StandardSolutionClass =
            byId[id] ?: error("No standard solution class with id=$id (valid: 1..5)")
    }
}
