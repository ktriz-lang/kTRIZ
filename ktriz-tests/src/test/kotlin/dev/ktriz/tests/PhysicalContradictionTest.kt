package dev.ktriz.tests

import dev.ktriz.function.Component
import dev.ktriz.physical.MAX_PHYSICAL_REASON_LENGTH
import dev.ktriz.physical.MAX_PHYSICAL_TEXT_LENGTH
import dev.ktriz.physical.PhysicalContradiction
import dev.ktriz.physical.SeparationPrinciple
import dev.ktriz.physical.physicalContradiction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

private fun pc(
    element: String = "E",
    property: String = "P",
    state: String = "high",
    opposite: String = "low",
    stateBecause: String? = null,
    oppositeBecause: String? = null,
) = PhysicalContradiction(Component(element), property, state, opposite, stateBecause, oppositeBecause)

class PhysicalContradictionTest :
    StringSpec({
        "DSL happy path keeps all fields" {
            val m =
                physicalContradiction {
                    element("Landing gear")
                    property("Presence")
                    state("present", because = "landing")
                    oppositeState("absent", because = "drag")
                    separation(SeparationPrinciple.IN_TIME)
                }
            m.element shouldBe Component("Landing gear")
            m.property shouldBe "Presence"
            m.state shouldBe "present"
            m.oppositeState shouldBe "absent"
            m.stateBecause shouldBe "landing"
            m.oppositeStateBecause shouldBe "drag"
            m.separation shouldBe SeparationPrinciple.IN_TIME
        }

        "optional fields default to null" {
            val m =
                physicalContradiction {
                    element("E")
                    property("P")
                    state("a")
                    oppositeState("b")
                }
            m.stateBecause.shouldBeNull()
            m.oppositeStateBecause.shouldBeNull()
            m.separation.shouldBeNull()
        }

        "direct construction equals the DSL result" {
            val dsl =
                physicalContradiction {
                    element("E")
                    property("P")
                    state("high", because = "x")
                    oppositeState("low")
                }
            dsl shouldBe pc(stateBecause = "x")
        }

        "identical, case-only and whitespace-only equal states are rejected" {
            listOf("high" to "high", "high" to "HIGH", "high" to "  high ").forEach { (a, b) ->
                val e = shouldThrow<IllegalArgumentException> { pc(state = a, opposite = b) }
                e.message!! shouldContain "different"
            }
        }

        "the equal-states message truncates long values" {
            val long = "x".repeat(MAX_PHYSICAL_TEXT_LENGTH)
            val e = shouldThrow<IllegalArgumentException> { pc(state = long, opposite = long) }
            e.message!! shouldNotContain long
            e.message!! shouldContain "..."
        }

        "blank values are rejected, including blank because texts" {
            shouldThrow<IllegalArgumentException> { pc(element = " ") }
            shouldThrow<IllegalArgumentException> { pc(property = "") }
            shouldThrow<IllegalArgumentException> { pc(state = " ") }
            shouldThrow<IllegalArgumentException> { pc(opposite = "") }
            shouldThrow<IllegalArgumentException> { pc(stateBecause = " ") }
            shouldThrow<IllegalArgumentException> { pc(oppositeBecause = "") }
        }

        "length limits are inclusive: 200/500 accepted, 201/501 rejected per field" {
            val t = "a".repeat(MAX_PHYSICAL_TEXT_LENGTH)
            val t1 = "a".repeat(MAX_PHYSICAL_TEXT_LENGTH + 1)
            val r = "a".repeat(MAX_PHYSICAL_REASON_LENGTH)
            val r1 = "a".repeat(MAX_PHYSICAL_REASON_LENGTH + 1)
            pc(element = t, property = t, state = t, opposite = "b", stateBecause = r, oppositeBecause = r)
            shouldThrow<IllegalArgumentException> { pc(element = t1) }
            shouldThrow<IllegalArgumentException> { pc(property = t1) }
            shouldThrow<IllegalArgumentException> { pc(state = t1) }
            shouldThrow<IllegalArgumentException> { pc(opposite = t1) }
            shouldThrow<IllegalArgumentException> { pc(stateBecause = r1) }
            shouldThrow<IllegalArgumentException> { pc(oppositeBecause = r1) }
        }

        "missing builder calls raise IllegalStateException" {
            shouldThrow<IllegalStateException> {
                physicalContradiction {
                    property("P")
                    state("a")
                    oppositeState("b")
                }
            }.message!! shouldContain "element"
            shouldThrow<IllegalStateException> {
                physicalContradiction {
                    element("E")
                    state("a")
                    oppositeState("b")
                }
            }.message!! shouldContain "property"
            shouldThrow<IllegalStateException> {
                physicalContradiction {
                    element("E")
                    property("P")
                    oppositeState("b")
                }
            }.message!! shouldContain "state"
            shouldThrow<IllegalStateException> {
                physicalContradiction {
                    element("E")
                    property("P")
                    state("a")
                }
            }.message!! shouldContain "oppositeState"
        }

        "last call wins" {
            val m =
                physicalContradiction {
                    element("A")
                    element("B")
                    property("P1")
                    property("P2")
                    state("s1", because = "x")
                    state("s2")
                    oppositeState("o")
                    separation(SeparationPrinciple.IN_SPACE)
                    separation(SeparationPrinciple.ON_SYSTEM_LEVEL)
                }
            m.element.name shouldBe "B"
            m.property shouldBe "P2"
            m.state shouldBe "s2"
            m.stateBecause.shouldBeNull()
            m.separation shouldBe SeparationPrinciple.ON_SYSTEM_LEVEL
        }

        "element(name) returns the same Component as Component(name)" {
            physicalContradiction {
                element("X") shouldBe Component("X")
                property("P")
                state("a")
                oppositeState("b")
            }
        }
    })
