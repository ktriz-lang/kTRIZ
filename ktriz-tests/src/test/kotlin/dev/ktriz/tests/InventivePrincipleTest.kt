package dev.ktriz.tests

import dev.ktriz.core.InventivePrinciple
import dev.ktriz.core.InventivePrinciple.ANTI_WEIGHT
import dev.ktriz.core.InventivePrinciple.COMPOSITE_MATERIALS
import dev.ktriz.core.InventivePrinciple.DYNAMICS
import dev.ktriz.core.InventivePrinciple.SEGMENTATION
import dev.ktriz.core.InventivePrinciple.TAKING_OUT
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank

class InventivePrincipleTest :
    StringSpec({
        "enum declares exactly 40 principles" {
            InventivePrinciple.entries.size shouldBe 40
        }

        "ids are exactly 1..40, no gaps, no duplicates" {
            val ids = InventivePrinciple.entries.map { it.id }
            ids.toSet() shouldBe (1..40).toSet()
            ids.size shouldBe ids.toSet().size
        }

        "entries are declared in ascending id order" {
            InventivePrinciple.entries.map { it.id } shouldBe (1..40).toList()
        }

        "ofId round-trips every declared entry" {
            InventivePrinciple.entries.forAll { InventivePrinciple.ofId(it.id) shouldBe it }
        }

        "ofId resolves the anchors documented in the DSL surface note" {
            InventivePrinciple.ofId(1) shouldBe SEGMENTATION
            InventivePrinciple.ofId(2) shouldBe TAKING_OUT
            InventivePrinciple.ofId(8) shouldBe ANTI_WEIGHT
            InventivePrinciple.ofId(15) shouldBe DYNAMICS
            InventivePrinciple.ofId(40) shouldBe COMPOSITE_MATERIALS
        }

        "ofId throws for ids outside 1..40" {
            listOf(0, 41, -1, Int.MAX_VALUE).forAll { invalidId ->
                shouldThrow<IllegalStateException> { InventivePrinciple.ofId(invalidId) }
            }
        }

        "the ofId failure message names the valid range" {
            val exception = shouldThrow<IllegalStateException> { InventivePrinciple.ofId(0) }
            exception.message shouldContain "valid: 1..40"
        }

        "every entry carries non-blank English and German labels" {
            InventivePrinciple.entries.forAll {
                it.labelEn.shouldNotBeBlank()
                it.labelDe.shouldNotBeBlank()
            }
        }

        "labels are unique in both languages" {
            InventivePrinciple.entries
                .map { it.labelEn }
                .toSet()
                .size shouldBe 40
            InventivePrinciple.entries
                .map { it.labelDe }
                .toSet()
                .size shouldBe 40
        }
    })
