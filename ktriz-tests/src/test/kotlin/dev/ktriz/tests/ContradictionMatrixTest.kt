package dev.ktriz.tests

import dev.ktriz.core.ContradictionMatrix
import dev.ktriz.core.EngineeringParameter.FORCE
import dev.ktriz.core.EngineeringParameter.LENGTH_OF_MOVING_OBJECT
import dev.ktriz.core.EngineeringParameter.SPEED
import dev.ktriz.core.EngineeringParameter.STRENGTH
import dev.ktriz.core.EngineeringParameter.TEMPERATURE
import dev.ktriz.core.EngineeringParameter.WEIGHT_OF_MOVING_OBJECT
import dev.ktriz.core.InventivePrinciple.ANTI_WEIGHT
import dev.ktriz.core.InventivePrinciple.CHEAP_SHORT_LIVING_OBJECTS
import dev.ktriz.core.InventivePrinciple.COMPOSITE_MATERIALS
import dev.ktriz.core.InventivePrinciple.DISCARDING_AND_RECOVERING
import dev.ktriz.core.InventivePrinciple.DYNAMICS
import dev.ktriz.core.InventivePrinciple.PARAMETER_CHANGES
import dev.ktriz.core.InventivePrinciple.PNEUMATICS_AND_HYDRAULICS
import dev.ktriz.core.InventivePrinciple.PRELIMINARY_ACTION
import dev.ktriz.core.InventivePrinciple.SEGMENTATION
import dev.ktriz.core.InventivePrinciple.SPHEROIDALITY_CURVATURE
import dev.ktriz.core.contradiction
import dev.ktriz.core.resolve
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class ContradictionMatrixTest :
    StringSpec({
        "cell 1 x 14 returns its four principles in TRIZ rank order" {
            ContradictionMatrix.lookup(
                contradiction(improving = WEIGHT_OF_MOVING_OBJECT, worsening = STRENGTH),
            ) shouldBe listOf(SEGMENTATION, ANTI_WEIGHT, COMPOSITE_MATERIALS, DYNAMICS)
        }

        "cell 1 x 3 returns its four principles in TRIZ rank order" {
            ContradictionMatrix.lookup(
                contradiction(improving = WEIGHT_OF_MOVING_OBJECT, worsening = LENGTH_OF_MOVING_OBJECT),
            ) shouldBe listOf(ANTI_WEIGHT, DYNAMICS, PNEUMATICS_AND_HYDRAULICS, DISCARDING_AND_RECOVERING)
        }

        "cell 10 x 14 returns its four principles in TRIZ rank order" {
            ContradictionMatrix.lookup(
                contradiction(improving = FORCE, worsening = STRENGTH),
            ) shouldBe
                listOf(PARAMETER_CHANGES, PRELIMINARY_ACTION, SPHEROIDALITY_CURVATURE, CHEAP_SHORT_LIVING_OBJECTS)
        }

        "an unpopulated cell yields an empty list, not an error" {
            ContradictionMatrix
                .lookup(
                    contradiction(improving = SPEED, worsening = TEMPERATURE),
                ).shouldBeEmpty()
        }

        "the matrix is deliberately incomplete (kTRIZ-ADR-0003)" {
            // This test is meant to fail the moment someone adds cells beyond the three
            // documented examples. A later, dedicated matrix wave must update it deliberately
            // and with explicit user sign-off -- see kTRIZ-ADR-0003.
            ContradictionMatrix.populatedCellCount shouldBe 3
        }

        "the reverse of a populated cell is not populated (rows = improving, columns = worsening)" {
            ContradictionMatrix
                .lookup(
                    contradiction(improving = STRENGTH, worsening = WEIGHT_OF_MOVING_OBJECT),
                ).shouldBeEmpty()
        }

        "resolve() delegates to lookup()" {
            val problem = contradiction(improving = WEIGHT_OF_MOVING_OBJECT, worsening = STRENGTH)

            problem.resolve() shouldBe ContradictionMatrix.lookup(problem)
        }

        "every stored raw principle id resolves to a real principle" {
            val cells =
                listOf(
                    contradiction(improving = WEIGHT_OF_MOVING_OBJECT, worsening = STRENGTH),
                    contradiction(improving = WEIGHT_OF_MOVING_OBJECT, worsening = LENGTH_OF_MOVING_OBJECT),
                    contradiction(improving = FORCE, worsening = STRENGTH),
                )

            cells.forEach { cell ->
                ContradictionMatrix.lookup(cell).size shouldBe 4
            }
        }

        "the documented hello-world example produces the documented German output" {
            val rendered =
                ContradictionMatrix
                    .lookup(
                        contradiction(improving = WEIGHT_OF_MOVING_OBJECT, worsening = STRENGTH),
                    ).map { principle -> "#${principle.id} ${principle.labelDe}" }

            rendered shouldBe
                listOf(
                    "#1 Segmentierung",
                    "#8 Gegengewicht",
                    "#40 Verbundwerkstoffe",
                    "#15 Dynamisierung",
                )
        }
    })
