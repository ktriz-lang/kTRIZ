package dev.ktriz.core

/**
 * The 39 classical TRIZ engineering parameters (Altshuller).
 *
 * [id] is the canonical 1..39 index used by the contradiction matrix and by the entire
 * TRIZ literature. [labelEn]/[labelDe] are carried for rendering and for MCP few-shot
 * output; the enum symbol itself is the compiler-enforced ground truth (Typed Domain
 * Grounding): a parameter an LLM invents simply does not resolve, so *existence* is
 * checked for free by the type system with no runtime validation at all.
 *
 * `enum` and not a `sealed` hierarchy on purpose: the 39 parameters are a closed set,
 * fully known at compile time, with no instance-specific structure beyond id + labels.
 * See the vault note "kTRIZ - DSL-Surface (Hello World)", section 1.
 *
 * The parameter list itself is public domain (Altshuller's classical 39, released by the
 * author's goodwill) -- see kTRIZ-ADR-0003.
 */
enum class EngineeringParameter(
    val id: Int,
    val labelEn: String,
    val labelDe: String,
) {
    WEIGHT_OF_MOVING_OBJECT(
        id = 1,
        labelEn = "Weight of moving object",
        labelDe = "Gewicht eines beweglichen Objekts",
    ),
    WEIGHT_OF_STATIONARY_OBJECT(
        id = 2,
        labelEn = "Weight of stationary object",
        labelDe = "Gewicht eines unbeweglichen Objekts",
    ),
    LENGTH_OF_MOVING_OBJECT(
        id = 3,
        labelEn = "Length of moving object",
        labelDe = "Länge eines beweglichen Objekts",
    ),
    LENGTH_OF_STATIONARY_OBJECT(
        id = 4,
        labelEn = "Length of stationary object",
        labelDe = "Länge eines unbeweglichen Objekts",
    ),
    AREA_OF_MOVING_OBJECT(
        id = 5,
        labelEn = "Area of moving object",
        labelDe = "Fläche eines beweglichen Objekts",
    ),
    AREA_OF_STATIONARY_OBJECT(
        id = 6,
        labelEn = "Area of stationary object",
        labelDe = "Fläche eines unbeweglichen Objekts",
    ),
    VOLUME_OF_MOVING_OBJECT(
        id = 7,
        labelEn = "Volume of moving object",
        labelDe = "Volumen eines beweglichen Objekts",
    ),
    VOLUME_OF_STATIONARY_OBJECT(
        id = 8,
        labelEn = "Volume of stationary object",
        labelDe = "Volumen eines unbeweglichen Objekts",
    ),
    SPEED(
        id = 9,
        labelEn = "Speed",
        labelDe = "Geschwindigkeit",
    ),
    FORCE(
        id = 10,
        labelEn = "Force",
        labelDe = "Kraft",
    ),
    STRESS_OR_PRESSURE(
        id = 11,
        labelEn = "Stress or pressure",
        labelDe = "Spannung oder Druck",
    ),
    SHAPE(
        id = 12,
        labelEn = "Shape",
        labelDe = "Form",
    ),
    STABILITY_OF_THE_OBJECTS_COMPOSITION(
        id = 13,
        labelEn = "Stability of the object's composition",
        labelDe = "Stabilität der Zusammensetzung des Objekts",
    ),
    STRENGTH(
        id = 14,
        labelEn = "Strength",
        labelDe = "Festigkeit",
    ),
    DURATION_OF_ACTION_OF_MOVING_OBJECT(
        id = 15,
        labelEn = "Duration of action of moving object",
        labelDe = "Wirkungsdauer eines beweglichen Objekts",
    ),
    DURATION_OF_ACTION_OF_STATIONARY_OBJECT(
        id = 16,
        labelEn = "Duration of action of stationary object",
        labelDe = "Wirkungsdauer eines unbeweglichen Objekts",
    ),
    TEMPERATURE(
        id = 17,
        labelEn = "Temperature",
        labelDe = "Temperatur",
    ),
    ILLUMINATION_INTENSITY(
        id = 18,
        labelEn = "Illumination intensity",
        labelDe = "Beleuchtungsstärke",
    ),
    USE_OF_ENERGY_BY_MOVING_OBJECT(
        id = 19,
        labelEn = "Use of energy by moving object",
        labelDe = "Energieverbrauch eines beweglichen Objekts",
    ),
    USE_OF_ENERGY_BY_STATIONARY_OBJECT(
        id = 20,
        labelEn = "Use of energy by stationary object",
        labelDe = "Energieverbrauch eines unbeweglichen Objekts",
    ),
    POWER(
        id = 21,
        labelEn = "Power",
        labelDe = "Leistung",
    ),
    LOSS_OF_ENERGY(
        id = 22,
        labelEn = "Loss of energy",
        labelDe = "Energieverluste",
    ),
    LOSS_OF_SUBSTANCE(
        id = 23,
        labelEn = "Loss of substance",
        labelDe = "Stoffverluste",
    ),
    LOSS_OF_INFORMATION(
        id = 24,
        labelEn = "Loss of information",
        labelDe = "Informationsverluste",
    ),
    LOSS_OF_TIME(
        id = 25,
        labelEn = "Loss of time",
        labelDe = "Zeitverluste",
    ),
    QUANTITY_OF_SUBSTANCE(
        id = 26,
        labelEn = "Quantity of substance",
        labelDe = "Stoffmenge",
    ),
    RELIABILITY(
        id = 27,
        labelEn = "Reliability",
        labelDe = "Zuverlässigkeit",
    ),
    MEASUREMENT_ACCURACY(
        id = 28,
        labelEn = "Measurement accuracy",
        labelDe = "Messgenauigkeit",
    ),
    MANUFACTURING_PRECISION(
        id = 29,
        labelEn = "Manufacturing precision",
        labelDe = "Fertigungsgenauigkeit",
    ),
    OBJECT_AFFECTED_HARMFUL_FACTORS(
        id = 30,
        labelEn = "Object-affected harmful factors",
        labelDe = "Von außen auf das Objekt einwirkende schädliche Faktoren",
    ),
    OBJECT_GENERATED_HARMFUL_FACTORS(
        id = 31,
        labelEn = "Object-generated harmful factors",
        labelDe = "Vom Objekt erzeugte schädliche Faktoren",
    ),
    EASE_OF_MANUFACTURE(
        id = 32,
        labelEn = "Ease of manufacture",
        labelDe = "Fertigungsfreundlichkeit",
    ),
    EASE_OF_OPERATION(
        id = 33,
        labelEn = "Ease of operation",
        labelDe = "Bedienkomfort",
    ),
    EASE_OF_REPAIR(
        id = 34,
        labelEn = "Ease of repair",
        labelDe = "Reparaturfreundlichkeit",
    ),
    ADAPTABILITY_OR_VERSATILITY(
        id = 35,
        labelEn = "Adaptability or versatility",
        labelDe = "Anpassungsfähigkeit oder Universalität",
    ),
    DEVICE_COMPLEXITY(
        id = 36,
        labelEn = "Device complexity",
        labelDe = "Komplexität der Vorrichtung",
    ),
    DIFFICULTY_OF_DETECTING_AND_MEASURING(
        id = 37,
        labelEn = "Difficulty of detecting and measuring",
        labelDe = "Schwierigkeit der Erfassung und Messung",
    ),
    EXTENT_OF_AUTOMATION(
        id = 38,
        labelEn = "Extent of automation",
        labelDe = "Automatisierungsgrad",
    ),
    PRODUCTIVITY(
        id = 39,
        labelEn = "Productivity",
        labelDe = "Produktivität",
    ),
    ;

    companion object {
        private val byId = entries.associateBy(EngineeringParameter::id)

        /**
         * Reverse lookup for matrix tables and imports that speak in raw indices.
         * The public DSL surface uses the symbols only, never the raw numbers.
         *
         * @throws IllegalStateException if [id] is outside 1..39.
         */
        fun ofId(id: Int): EngineeringParameter =
            byId[id] ?: error("No engineering parameter with id=$id (valid: 1..39)")
    }
}
