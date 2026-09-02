package dev.ktriz.tests

import dev.ktriz.core.ContradictionMatrix
import dev.ktriz.core.ContradictionMatrixSource
import dev.ktriz.core.EngineeringParameter.DURATION_OF_ACTION_OF_MOVING_OBJECT
import dev.ktriz.core.EngineeringParameter.FORCE
import dev.ktriz.core.EngineeringParameter.LENGTH_OF_MOVING_OBJECT
import dev.ktriz.core.EngineeringParameter.STRENGTH
import dev.ktriz.core.EngineeringParameter.WEIGHT_OF_MOVING_OBJECT
import dev.ktriz.core.EngineeringParameter.WEIGHT_OF_STATIONARY_OBJECT
import dev.ktriz.core.InventivePrinciple
import dev.ktriz.core.contradiction
import dev.ktriz.core.resolve
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

/**
 * Covers the full classical 39x39 matrix bundled via `contradiction-matrix-1971.csv`
 * (kTRIZ-ADR-0003). See `docs/matrix-provenance.adoc` for sourcing and reconciliation.
 *
 * Two of the three cells this test suite originally pinned turned out, on reconciliation
 * against four independent transcriptions, to be *transposed* -- what was recorded as
 * "1 x 14" was actually the classical cell "14 x 1", and likewise for "1 x 3" / "3 x 1".
 * This suite keeps every previously-asserted number, just under its correct address (see
 * "cell 14 x 1" and "cell 3 x 1" below), and adds the genuine "1 x 14" / "1 x 3" cells.
 */
class ContradictionMatrixTest :
    StringSpec({
        "cell 1 x 14 returns its four principles in TRIZ rank order" {
            ContradictionMatrix.lookup(
                contradiction(improving = WEIGHT_OF_MOVING_OBJECT, worsening = STRENGTH),
            ) shouldBe
                listOf(
                    InventivePrinciple.MECHANICS_SUBSTITUTION,
                    InventivePrinciple.CHEAP_SHORT_LIVING_OBJECTS,
                    InventivePrinciple.MECHANICAL_VIBRATION,
                    InventivePrinciple.COMPOSITE_MATERIALS,
                )
        }

        "cell 14 x 1 returns its four principles in TRIZ rank order" {
            ContradictionMatrix.lookup(
                contradiction(improving = STRENGTH, worsening = WEIGHT_OF_MOVING_OBJECT),
            ) shouldBe
                listOf(
                    InventivePrinciple.SEGMENTATION,
                    InventivePrinciple.ANTI_WEIGHT,
                    InventivePrinciple.COMPOSITE_MATERIALS,
                    InventivePrinciple.DYNAMICS,
                )
        }

        "cell 1 x 3 returns its four principles in TRIZ rank order" {
            ContradictionMatrix.lookup(
                contradiction(improving = WEIGHT_OF_MOVING_OBJECT, worsening = LENGTH_OF_MOVING_OBJECT),
            ) shouldBe
                listOf(
                    InventivePrinciple.DYNAMICS,
                    InventivePrinciple.ANTI_WEIGHT,
                    InventivePrinciple.PNEUMATICS_AND_HYDRAULICS,
                    InventivePrinciple.DISCARDING_AND_RECOVERING,
                )
        }

        "cell 3 x 1 returns its four principles in TRIZ rank order" {
            ContradictionMatrix.lookup(
                contradiction(improving = LENGTH_OF_MOVING_OBJECT, worsening = WEIGHT_OF_MOVING_OBJECT),
            ) shouldBe
                listOf(
                    InventivePrinciple.ANTI_WEIGHT,
                    InventivePrinciple.DYNAMICS,
                    InventivePrinciple.PNEUMATICS_AND_HYDRAULICS,
                    InventivePrinciple.DISCARDING_AND_RECOVERING,
                )
        }

        "cell 10 x 14 returns its four principles in TRIZ rank order" {
            ContradictionMatrix.lookup(
                contradiction(improving = FORCE, worsening = STRENGTH),
            ) shouldBe
                listOf(
                    InventivePrinciple.PARAMETER_CHANGES,
                    InventivePrinciple.PRELIMINARY_ACTION,
                    InventivePrinciple.SPHEROIDALITY_CURVATURE,
                    InventivePrinciple.CHEAP_SHORT_LIVING_OBJECTS,
                )
        }

        "a populated cell and its reverse are independent (rows = improving, columns = worsening)" {
            val forward =
                ContradictionMatrix.lookup(
                    contradiction(improving = WEIGHT_OF_MOVING_OBJECT, worsening = STRENGTH),
                )
            val backward =
                ContradictionMatrix.lookup(
                    contradiction(improving = STRENGTH, worsening = WEIGHT_OF_MOVING_OBJECT),
                )

            forward shouldNotBe backward
        }

        "a single-principle cell (3 x 15) is not lost" {
            ContradictionMatrix.lookup(
                contradiction(
                    improving = LENGTH_OF_MOVING_OBJECT,
                    worsening = DURATION_OF_ACTION_OF_MOVING_OBJECT,
                ),
            ) shouldBe listOf(InventivePrinciple.PERIODIC_ACTION)
        }

        "an unpopulated cell yields an empty list, not an error" {
            ContradictionMatrix
                .lookup(
                    contradiction(improving = WEIGHT_OF_MOVING_OBJECT, worsening = WEIGHT_OF_STATIONARY_OBJECT),
                ).shouldBeEmpty()
        }

        "the full classical matrix has exactly 1248 populated cells" {
            // Pins the kTRIZ-ADR-0003 data boundary. Change only deliberately, with a matching
            // update to docs/matrix-provenance.adoc, and with explicit user sign-off.
            ContradictionMatrix.populatedCellCount shouldBe 1248
        }

        "the bundled resource loads without error and lookups do not throw" {
            ContradictionMatrix.populatedCellCount shouldNotBe 0
            ContradictionMatrix.lookup(
                contradiction(improving = WEIGHT_OF_MOVING_OBJECT, worsening = STRENGTH),
            ) shouldNotBe emptyList<InventivePrinciple>()
        }

        "resolve() delegates to lookup()" {
            val problem = contradiction(improving = WEIGHT_OF_MOVING_OBJECT, worsening = STRENGTH)

            problem.resolve() shouldBe ContradictionMatrix.lookup(problem)
        }

        "the three pinned four-principle cells keep exactly four principles" {
            val cells =
                listOf(
                    contradiction(improving = WEIGHT_OF_MOVING_OBJECT, worsening = STRENGTH),
                    contradiction(improving = WEIGHT_OF_MOVING_OBJECT, worsening = LENGTH_OF_MOVING_OBJECT),
                    contradiction(improving = FORCE, worsening = STRENGTH),
                )

            cells.forEach { cell ->
                ContradictionMatrix.lookup(cell) shouldHaveSize 4
            }
        }

        "the German labels of the hello-world cell stay stable" {
            val rendered =
                ContradictionMatrix
                    .lookup(
                        contradiction(improving = WEIGHT_OF_MOVING_OBJECT, worsening = STRENGTH),
                    ).map { principle -> "#${principle.id} ${principle.labelDe}" }

            rendered shouldBe
                listOf(
                    "#28 Ersetzen mechanischer Wirkprinzipien",
                    "#27 Billige Kurzlebigkeit",
                    "#18 Mechanische Schwingungen",
                    "#40 Verbundwerkstoffe",
                )
        }

        "the README hello-world example produces the README's documented English output" {
            // Mirrors README.adoc's main() exactly -- including its print format -- so a
            // future relabel of InventivePrinciple.labelEn or reorder of this example
            // breaks this test instead of silently making the printed README wrong.
            val problem = contradiction(improving = WEIGHT_OF_MOVING_OBJECT, worsening = STRENGTH)
            val header = "Contradiction: ${problem.improving.labelEn} up vs. ${problem.worsening.labelEn} down"
            val lines = ContradictionMatrix.lookup(problem).map { p -> "  #${p.id}  ${p.labelEn}" }

            header shouldBe "Contradiction: Weight of moving object up vs. Strength down"
            lines shouldBe
                listOf(
                    "  #28  Mechanics substitution",
                    "  #27  Cheap short-living objects",
                    "  #18  Mechanical vibration",
                    "  #40  Composite materials",
                )
        }

        "a returned principle list cannot be mutated via an unchecked cast" {
            @Suppress("UNCHECKED_CAST")
            val mutable =
                ContradictionMatrix.lookup(
                    contradiction(improving = WEIGHT_OF_MOVING_OBJECT, worsening = STRENGTH),
                ) as MutableList<InventivePrinciple>

            shouldThrow<UnsupportedOperationException> {
                mutable.add(InventivePrinciple.SEGMENTATION)
            }
        }

        "the process default (no ktriz.matrix.file override) is BUNDLED_CLASSICAL with the classical provenance" {
            // Regression guard for the pluggable matrix-source seam (kTRIZ-ADR-0003): as long
            // as no test in this JVM sets ktriz.matrix.file before ContradictionMatrix is
            // first touched, its `by lazy` singleton must resolve to the bundled classical
            // matrix, byte-identical in behaviour to the pre-seam loadClassical(). Every
            // other case in this file (populatedCellCount == 1248, the four pinned cells, the
            // README output) is itself the real regression proof for that "no behaviour
            // change without an override" guarantee.
            ContradictionMatrix.origin shouldBe ContradictionMatrixSource.Origin.BUNDLED_CLASSICAL
            ContradictionMatrix.provenance shouldContain "reconciliation"
        }

        "the raw bundled matrix is structurally sound across every cell" {
            // Reads the same classpath resource ContradictionMatrix loads in production, but
            // independently -- this deliberately does not add any cell-enumeration API to
            // ContradictionMatrix/ContradictionMatrixData, which stay lookup()-only by design.
            val lines =
                ContradictionMatrix::class.java
                    .getResourceAsStream("contradiction-matrix-1971.csv")!!
                    .bufferedReader(Charsets.UTF_8)
                    .readLines()
                    .filter {
                        it.isNotBlank() &&
                            !it.startsWith(
                                "#",
                            ) &&
                            !it.startsWith("improving,worsening,principles")
                    }

            lines shouldHaveSize 1248

            val improvingRows = mutableSetOf<Int>()
            lines.forEach { line ->
                val columns = line.split(",")
                columns.size shouldBe 4

                val improving = columns[0].toInt()
                val worsening = columns[1].toInt()
                val principles = columns[2].trim().split(Regex("\\s+")).map(String::toInt)

                (improving in 1..39) shouldBe true
                (worsening in 1..39) shouldBe true
                improving shouldNotBe worsening
                principles.isEmpty() shouldBe false
                principles.forEach { id -> (id in 1..40) shouldBe true }

                improvingRows += improving
            }

            improvingRows shouldBe (1..39).toSet()
        }
    })
