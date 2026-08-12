package dev.ktriz.core

/**
 * The 40 classical TRIZ inventive principles (Altshuller).
 *
 * Same design rationale as [EngineeringParameter]: a closed, compile-time-known set,
 * therefore an `enum`; [id] is the canonical 1..40 index the contradiction matrix cells
 * refer to; the symbol is the compiler-enforced ground truth.
 *
 * Public domain, see kTRIZ-ADR-0003.
 */
enum class InventivePrinciple(
    val id: Int,
    val labelEn: String,
    val labelDe: String,
) {
    SEGMENTATION(
        id = 1,
        labelEn = "Segmentation",
        labelDe = "Segmentierung",
    ),
    TAKING_OUT(
        id = 2,
        labelEn = "Taking out",
        labelDe = "Abtrennung",
    ),
    LOCAL_QUALITY(
        id = 3,
        labelEn = "Local quality",
        labelDe = "Örtliche Qualität",
    ),
    ASYMMETRY(
        id = 4,
        labelEn = "Asymmetry",
        labelDe = "Asymmetrie",
    ),
    MERGING(
        id = 5,
        labelEn = "Merging",
        labelDe = "Kopplung",
    ),
    UNIVERSALITY(
        id = 6,
        labelEn = "Universality",
        labelDe = "Universalität",
    ),
    NESTED_DOLL(
        id = 7,
        labelEn = "Nested doll",
        labelDe = "Verschachtelung",
    ),
    ANTI_WEIGHT(
        id = 8,
        labelEn = "Anti-weight",
        labelDe = "Gegengewicht",
    ),
    PRELIMINARY_ANTI_ACTION(
        id = 9,
        labelEn = "Preliminary anti-action",
        labelDe = "Vorherige Gegenwirkung",
    ),
    PRELIMINARY_ACTION(
        id = 10,
        labelEn = "Preliminary action",
        labelDe = "Vorherige Wirkung",
    ),
    BEFOREHAND_CUSHIONING(
        id = 11,
        labelEn = "Beforehand cushioning",
        labelDe = "Vorbeugende Schutzmaßnahme",
    ),
    EQUIPOTENTIALITY(
        id = 12,
        labelEn = "Equipotentiality",
        labelDe = "Äquipotenzialität",
    ),
    THE_OTHER_WAY_ROUND(
        id = 13,
        labelEn = "The other way round",
        labelDe = "Umkehrung",
    ),
    SPHEROIDALITY_CURVATURE(
        id = 14,
        labelEn = "Spheroidality – curvature",
        labelDe = "Kugelähnlichkeit / Krümmung",
    ),
    DYNAMICS(
        id = 15,
        labelEn = "Dynamics",
        labelDe = "Dynamisierung",
    ),
    PARTIAL_OR_EXCESSIVE_ACTIONS(
        id = 16,
        labelEn = "Partial or excessive actions",
        labelDe = "Teilweise oder überschüssige Wirkung",
    ),
    ANOTHER_DIMENSION(
        id = 17,
        labelEn = "Another dimension",
        labelDe = "Übergang zu höherer Dimension",
    ),
    MECHANICAL_VIBRATION(
        id = 18,
        labelEn = "Mechanical vibration",
        labelDe = "Mechanische Schwingungen",
    ),
    PERIODIC_ACTION(
        id = 19,
        labelEn = "Periodic action",
        labelDe = "Periodische Wirkung",
    ),
    CONTINUITY_OF_USEFUL_ACTION(
        id = 20,
        labelEn = "Continuity of useful action",
        labelDe = "Kontinuität der Nutzwirkung",
    ),
    SKIPPING(
        id = 21,
        labelEn = "Skipping",
        labelDe = "Durcheilen",
    ),
    BLESSING_IN_DISGUISE(
        id = 22,
        labelEn = "Blessing in disguise",
        labelDe = "Umwandlung von Schaden in Nutzen",
    ),
    FEEDBACK(
        id = 23,
        labelEn = "Feedback",
        labelDe = "Rückkopplung",
    ),
    INTERMEDIARY(
        id = 24,
        labelEn = "Intermediary",
        labelDe = "Vermittler",
    ),
    SELF_SERVICE(
        id = 25,
        labelEn = "Self-service",
        labelDe = "Selbstbedienung",
    ),
    COPYING(
        id = 26,
        labelEn = "Copying",
        labelDe = "Kopieren",
    ),
    CHEAP_SHORT_LIVING_OBJECTS(
        id = 27,
        labelEn = "Cheap short-living objects",
        labelDe = "Billige Kurzlebigkeit",
    ),
    MECHANICS_SUBSTITUTION(
        id = 28,
        labelEn = "Mechanics substitution",
        labelDe = "Ersetzen mechanischer Wirkprinzipien",
    ),
    PNEUMATICS_AND_HYDRAULICS(
        id = 29,
        labelEn = "Pneumatics and hydraulics",
        labelDe = "Pneumatik und Hydraulik",
    ),
    FLEXIBLE_SHELLS_AND_THIN_FILMS(
        id = 30,
        labelEn = "Flexible shells and thin films",
        labelDe = "Flexible Hüllen und dünne Folien",
    ),
    POROUS_MATERIALS(
        id = 31,
        labelEn = "Porous materials",
        labelDe = "Poröse Werkstoffe",
    ),
    COLOR_CHANGES(
        id = 32,
        labelEn = "Color changes",
        labelDe = "Farbveränderung",
    ),
    HOMOGENEITY(
        id = 33,
        labelEn = "Homogeneity",
        labelDe = "Gleichartigkeit",
    ),
    DISCARDING_AND_RECOVERING(
        id = 34,
        labelEn = "Discarding and recovering",
        labelDe = "Beseitigung und Regenerierung von Teilen",
    ),
    PARAMETER_CHANGES(
        id = 35,
        labelEn = "Parameter changes",
        labelDe = "Veränderung der Eigenschaften",
    ),
    PHASE_TRANSITIONS(
        id = 36,
        labelEn = "Phase transitions",
        labelDe = "Phasenübergänge",
    ),
    THERMAL_EXPANSION(
        id = 37,
        labelEn = "Thermal expansion",
        labelDe = "Wärmeausdehnung",
    ),
    STRONG_OXIDANTS(
        id = 38,
        labelEn = "Strong oxidants",
        labelDe = "Anwendung starker Oxidationsmittel",
    ),
    INERT_ATMOSPHERE(
        id = 39,
        labelEn = "Inert atmosphere",
        labelDe = "Träges Medium",
    ),
    COMPOSITE_MATERIALS(
        id = 40,
        labelEn = "Composite materials",
        labelDe = "Verbundwerkstoffe",
    ),
    ;

    companion object {
        private val byId = entries.associateBy(InventivePrinciple::id)

        /** @throws IllegalStateException if [id] is outside 1..40. */
        fun ofId(id: Int): InventivePrinciple = byId[id] ?: error("No inventive principle with id=$id (valid: 1..40)")
    }
}
