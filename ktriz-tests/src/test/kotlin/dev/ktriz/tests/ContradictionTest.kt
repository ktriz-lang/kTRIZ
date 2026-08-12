package dev.ktriz.tests

import dev.ktriz.core.Contradiction
import dev.ktriz.core.EngineeringParameter
import dev.ktriz.core.EngineeringParameter.SPEED
import dev.ktriz.core.EngineeringParameter.STRENGTH
import dev.ktriz.core.EngineeringParameter.WEIGHT_OF_MOVING_OBJECT
import dev.ktriz.core.contradiction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

class ContradictionTest :
    StringSpec({
        "factory builds a contradiction from two different parameters" {
            val problem =
                contradiction(
                    improving = WEIGHT_OF_MOVING_OBJECT,
                    worsening = STRENGTH,
                )

            problem.improving shouldBe WEIGHT_OF_MOVING_OBJECT
            problem.worsening shouldBe STRENGTH
        }

        "factory rejects a self-contradiction with a speaking message" {
            val exception =
                shouldThrow<IllegalArgumentException> {
                    contradiction(improving = STRENGTH, worsening = STRENGTH)
                }

            exception.message shouldContain "cannot contradict itself"
            exception.message shouldContain "Strength"
        }

        "self-contradiction is rejected for every parameter, not just one" {
            EngineeringParameter.entries.forAll { parameter ->
                shouldThrow<IllegalArgumentException> {
                    contradiction(improving = parameter, worsening = parameter)
                }
            }
        }

        "the constructor itself enforces the invariant, not only the factory" {
            shouldThrow<IllegalArgumentException> {
                Contradiction(improving = SPEED, worsening = SPEED)
            }
        }

        "copy() cannot smuggle in a self-contradiction" {
            val problem = contradiction(improving = SPEED, worsening = STRENGTH)

            shouldThrow<IllegalArgumentException> {
                problem.copy(worsening = SPEED)
            }
        }

        "contradictions with the same poles are equal (data semantics)" {
            val a = contradiction(improving = SPEED, worsening = STRENGTH)
            val b = contradiction(improving = SPEED, worsening = STRENGTH)

            a shouldBe b
            a.hashCode() shouldBe b.hashCode()
        }

        "direction matters: swapping the poles yields a different contradiction" {
            val forward = contradiction(improving = SPEED, worsening = STRENGTH)
            val backward = contradiction(improving = STRENGTH, worsening = SPEED)

            forward shouldNotBe backward
        }
    })
