package dev.ktriz.tests

import dev.ktriz.core.EngineeringParameter
import dev.ktriz.core.EngineeringParameter.EASE_OF_MANUFACTURE
import dev.ktriz.core.EngineeringParameter.LENGTH_OF_MOVING_OBJECT
import dev.ktriz.core.EngineeringParameter.STRENGTH
import dev.ktriz.core.EngineeringParameter.WEIGHT_OF_MOVING_OBJECT
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank

class EngineeringParameterTest :
    StringSpec({
        "enum declares exactly 39 parameters" {
            EngineeringParameter.entries.size shouldBe 39
        }

        "ids are exactly 1..39, no gaps, no duplicates" {
            val ids = EngineeringParameter.entries.map { it.id }
            ids.toSet() shouldBe (1..39).toSet()
            ids.size shouldBe ids.toSet().size
        }

        "entries are declared in ascending id order" {
            EngineeringParameter.entries.map { it.id } shouldBe (1..39).toList()
        }

        "ofId round-trips every declared entry" {
            EngineeringParameter.entries.forAll { EngineeringParameter.ofId(it.id) shouldBe it }
        }

        "ofId resolves the anchors documented in the DSL surface note" {
            EngineeringParameter.ofId(1) shouldBe WEIGHT_OF_MOVING_OBJECT
            EngineeringParameter.ofId(3) shouldBe LENGTH_OF_MOVING_OBJECT
            EngineeringParameter.ofId(14) shouldBe STRENGTH
            EngineeringParameter.ofId(32) shouldBe EASE_OF_MANUFACTURE
        }

        "ofId throws for ids outside 1..39" {
            listOf(0, 40, -1, Int.MAX_VALUE, Int.MIN_VALUE).forAll { invalidId ->
                shouldThrow<IllegalStateException> { EngineeringParameter.ofId(invalidId) }
            }
        }

        "the ofId failure message names the valid range" {
            val exception = shouldThrow<IllegalStateException> { EngineeringParameter.ofId(0) }
            exception.message shouldContain "valid: 1..39"
            exception.message shouldContain "id=0"
        }

        "every entry carries non-blank English and German labels" {
            EngineeringParameter.entries.forAll {
                it.labelEn.shouldNotBeBlank()
                it.labelDe.shouldNotBeBlank()
            }
        }

        "labels are unique in both languages" {
            EngineeringParameter.entries
                .map { it.labelEn }
                .toSet()
                .size shouldBe 39
            EngineeringParameter.entries
                .map { it.labelDe }
                .toSet()
                .size shouldBe 39
        }
    })
