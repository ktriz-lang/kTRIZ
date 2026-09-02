package dev.ktriz.sufield

/**
 * The six classical Su-Field field types (Altshuller's substance-field analysis).
 *
 * [id] is the canonical 1..6 index; [labelEn]/[labelDe]/[abbreviation] are carried for
 * rendering, exactly following the [dev.ktriz.core.EngineeringParameter] pattern: existence of
 * a field type is a compiler-enforced guarantee (Typed Domain Grounding), not a runtime check
 * -- an LLM that invents a field type simply fails to compile.
 *
 * [abbreviation] is the classical subscript used in the printed Su-Field notation: the full
 * symbol is `"F" + abbreviation` (`FMec`, `FTh`, `FCh`, `FEl`, `FM`, `FGr`), per the legend
 * table (fig. 1.1.1.a) of *Systematic Innovation*, "4 Su-Field Analysis and Standard
 * Solutions" (`innovazionesistematica.it/wp-content/uploads/2020/10/EN_04.pdf`, citing *A
 * Thread in the Labyrinth*, Petrozavodsk: Karelia, 1988, ISBN 5-7545-0020-3). That source is
 * internally inconsistent on casing elsewhere (`Fmec` in one of its own worked-example
 * figures vs. `FMec` in its own legend table) -- the legend table is treated as normative here,
 * hence `FMec` rather than `Fmec`.
 *
 * `enum` and not a `sealed` hierarchy, for the same reason as
 * [dev.ktriz.core.EngineeringParameter]: a closed set, fully known at compile time, with no
 * instance-specific structure beyond id + labels.
 *
 * Deliberately **not** the later MATCEMIB eight-field extension (Belski/Livotov/Mayer, which
 * adds Acoustic, Biological, and Intermolecular fields on top of these classical six). This
 * enum stays a closed, classical six-entry set on purpose -- the same public-domain-only
 * stance kTRIZ already takes for the 39x39 contradiction matrix (see kTRIZ-ADR-0003) and is
 * documented for Su-Field data at kTRIZ-ADR-0001: only Altshuller's original classical field
 * taxonomy is bundled, never a newer, differently licensed or attributed extension. A future
 * wave that wants MATCEMIB support adds a *new*, separate enum rather than extending this
 * one, so existing `FieldType.entries`-based code (e.g. exhaustive `when` blocks) is never
 * silently broken by an extension it did not ask for.
 */
enum class FieldType(
    val id: Int,
    val labelEn: String,
    val labelDe: String,
    val abbreviation: String,
) {
    MECHANICAL(
        id = 1,
        labelEn = "Mechanical",
        labelDe = "Mechanisch",
        abbreviation = "Mec",
    ),
    THERMAL(
        id = 2,
        labelEn = "Thermal",
        labelDe = "Thermisch",
        abbreviation = "Th",
    ),
    CHEMICAL(
        id = 3,
        labelEn = "Chemical",
        labelDe = "Chemisch",
        abbreviation = "Ch",
    ),
    ELECTRIC(
        id = 4,
        labelEn = "Electric",
        labelDe = "Elektrisch",
        abbreviation = "El",
    ),
    MAGNETIC(
        id = 5,
        labelEn = "Magnetic",
        labelDe = "Magnetisch",
        abbreviation = "M",
    ),
    GRAVITATIONAL(
        id = 6,
        labelEn = "Gravitational",
        labelDe = "Gravitativ",
        abbreviation = "Gr",
    ),
    ;

    companion object {
        private val byId = entries.associateBy(FieldType::id)

        /**
         * Reverse lookup for future matrix/lookup tables that speak in raw indices. The
         * public DSL surface uses the symbols only, never the raw numbers.
         *
         * @throws IllegalStateException if [id] is outside 1..6.
         */
        fun ofId(id: Int): FieldType = byId[id] ?: error("No field type with id=$id (valid: 1..6)")
    }
}
