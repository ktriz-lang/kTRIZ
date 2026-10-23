package dev.ktriz.physical

/**
 * The four classical separation principles (Altshuller) for resolving a physical
 * contradiction: the two opposite states of a property are separated in space, in time,
 * upon a condition, or between the whole system and its parts.
 *
 * Same design rationale as [dev.ktriz.sufield.FieldType]: a closed, compile-time-known set,
 * therefore an `enum`; [id] is the canonical 1..4 index; existence of a separation principle
 * is a compiler-enforced guarantee (Typed Domain Grounding) -- an LLM that invents one fails to
 * compile.
 *
 * Deliberately **not** the 11 separation principles/strategies of Darrell Mann / CREAX /
 * Systematic Innovation, neither their content nor their names. This enum stays the closed,
 * classical four-entry set; a later extension would get its own, separate enum so existing
 * exhaustive `when` blocks are never silently broken. See kTRIZ-ADR-0003 and
 * `docs/physical-contradictions-provenance.adoc`.
 *
 * **No mapping to [dev.ktriz.core.InventivePrinciple] is bundled.** Only one freely available
 * source gave per-separation principle lists, so the two-independent-sources rule could not be
 * met; see the provenance document. Bundling none is deliberate, not an omission.
 */
enum class SeparationPrinciple(
    val id: Int,
    val labelEn: String,
    val labelDe: String,
) {
    IN_SPACE(1, "Separation in space", "Trennung im Raum"),
    IN_TIME(2, "Separation in time", "Trennung in der Zeit"),
    UPON_CONDITION(
        3,
        "Separation upon condition (in relation)",
        "Trennung nach Bedingung (in der Beziehung)",
    ),
    ON_SYSTEM_LEVEL(
        4,
        "Separation on system level (parts and whole)",
        "Trennung auf Systemebene (Teil und Ganzes)",
    ),
    ;

    companion object {
        /** @throws IllegalStateException if [id] is outside 1..4. */
        fun ofId(id: Int): SeparationPrinciple =
            entries.firstOrNull { it.id == id }
                ?: error("No SeparationPrinciple with id $id; valid ids are 1..${entries.size}.")
    }
}
