package dev.ktriz.tests

import dev.ktriz.sufield.FieldType
import dev.ktriz.sufield.FieldType.CHEMICAL
import dev.ktriz.sufield.FieldType.ELECTRIC
import dev.ktriz.sufield.FieldType.GRAVITATIONAL
import dev.ktriz.sufield.FieldType.MAGNETIC
import dev.ktriz.sufield.FieldType.MECHANICAL
import dev.ktriz.sufield.FieldType.THERMAL
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank

class FieldTypeTest :
    StringSpec({
        "enum declares exactly 6 field types" {
            FieldType.entries.size shouldBe 6
        }

        "ids are exactly 1..6, no gaps, no duplicates" {
            val ids = FieldType.entries.map { it.id }
            ids.toSet() shouldBe (1..6).toSet()
            ids.size shouldBe ids.toSet().size
        }

        "entries are declared in ascending id order" {
            FieldType.entries.map { it.id } shouldBe (1..6).toList()
        }

        "ofId round-trips every declared entry" {
            FieldType.entries.forAll { FieldType.ofId(it.id) shouldBe it }
        }

        "ofId resolves the six classical field types" {
            FieldType.ofId(1) shouldBe MECHANICAL
            FieldType.ofId(2) shouldBe THERMAL
            FieldType.ofId(3) shouldBe CHEMICAL
            FieldType.ofId(4) shouldBe ELECTRIC
            FieldType.ofId(5) shouldBe MAGNETIC
            FieldType.ofId(6) shouldBe GRAVITATIONAL
        }

        "ofId throws for ids outside 1..6" {
            listOf(0, 7, -1, Int.MAX_VALUE, Int.MIN_VALUE).forAll { invalidId ->
                shouldThrow<IllegalStateException> { FieldType.ofId(invalidId) }
            }
        }

        "the ofId failure message names the valid range" {
            val exception = shouldThrow<IllegalStateException> { FieldType.ofId(0) }
            exception.message shouldContain "valid: 1..6"
            exception.message shouldContain "id=0"
        }

        "every entry carries non-blank English and German labels" {
            FieldType.entries.forAll {
                it.labelEn.shouldNotBeBlank()
                it.labelDe.shouldNotBeBlank()
            }
        }

        "labels are unique in both languages" {
            FieldType.entries
                .map { it.labelEn }
                .toSet()
                .size shouldBe 6
            FieldType.entries
                .map { it.labelDe }
                .toSet()
                .size shouldBe 6
        }
    })
