package dev.ktriz.tests

import dev.ktriz.sufield.StandardSolutionClass
import dev.ktriz.sufield.StandardSolutionClass.APPLICATION_STRATEGIES
import dev.ktriz.sufield.StandardSolutionClass.DEVELOPMENT
import dev.ktriz.sufield.StandardSolutionClass.MEASUREMENT_AND_DETECTION
import dev.ktriz.sufield.StandardSolutionClass.SYNTHESIS_AND_DESTRUCTION
import dev.ktriz.sufield.StandardSolutionClass.TRANSITION_TO_SUPERSYSTEM_OR_MICROLEVEL
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldNotMatch

class StandardSolutionClassTest :
    StringSpec({
        "enum declares exactly 5 classes" {
            StandardSolutionClass.entries.size shouldBe 5
        }

        "ids are exactly 1..5, no gaps, no duplicates" {
            val ids = StandardSolutionClass.entries.map { it.id }
            ids.toSet() shouldBe (1..5).toSet()
            ids.size shouldBe ids.toSet().size
        }

        "entries are declared in ascending id order" {
            StandardSolutionClass.entries.map { it.id } shouldBe (1..5).toList()
        }

        "ofId round-trips every declared entry" {
            StandardSolutionClass.entries.forAll { StandardSolutionClass.ofId(it.id) shouldBe it }
        }

        "ofId resolves the five classes" {
            StandardSolutionClass.ofId(1) shouldBe SYNTHESIS_AND_DESTRUCTION
            StandardSolutionClass.ofId(2) shouldBe DEVELOPMENT
            StandardSolutionClass.ofId(3) shouldBe TRANSITION_TO_SUPERSYSTEM_OR_MICROLEVEL
            StandardSolutionClass.ofId(4) shouldBe MEASUREMENT_AND_DETECTION
            StandardSolutionClass.ofId(5) shouldBe APPLICATION_STRATEGIES
        }

        "ofId throws for ids outside 1..5" {
            listOf(0, 6, -1, Int.MAX_VALUE, Int.MIN_VALUE).forAll { invalidId ->
                shouldThrow<IllegalStateException> { StandardSolutionClass.ofId(invalidId) }
            }
        }

        "the ofId failure message names the valid range" {
            val exception = shouldThrow<IllegalStateException> { StandardSolutionClass.ofId(0) }
            exception.message shouldContain "valid: 1..5"
            exception.message shouldContain "id=0"
        }

        "every entry carries non-blank English and German labels" {
            StandardSolutionClass.entries.forAll {
                it.labelEn.shouldNotBeBlank()
                it.labelDe.shouldNotBeBlank()
            }
        }

        "labels are unique in both languages" {
            StandardSolutionClass.entries
                .map { it.labelEn }
                .toSet()
                .size shouldBe 5
            StandardSolutionClass.entries
                .map { it.labelDe }
                .toSet()
                .size shouldBe 5
        }

        "every entry carries a non-blank description" {
            StandardSolutionClass.entries.forAll {
                it.description.shouldNotBeBlank()
            }
        }

        "solutionCount matches the documented per-class split" {
            val expected =
                mapOf(
                    SYNTHESIS_AND_DESTRUCTION to 13,
                    DEVELOPMENT to 23,
                    TRANSITION_TO_SUPERSYSTEM_OR_MICROLEVEL to 6,
                    MEASUREMENT_AND_DETECTION to 17,
                    APPLICATION_STRATEGIES to 17,
                )
            StandardSolutionClass.entries.forAll { entry ->
                entry.solutionCount shouldBe expected.getValue(entry)
            }
        }

        "solutionCount sums to the full 76 standard solutions" {
            StandardSolutionClass.entries.sumOf { it.solutionCount } shouldBe 76
        }

        "descriptions stay short prose, not a bundled list of the 76 individual solutions" {
            // Heuristic guard, not a proof of absence -- the real check is the manual diff
            // review in the security/provenance loop (see docs/standard-solutions-provenance.adoc).
            StandardSolutionClass.entries.forAll {
                it.description.length shouldBeLessThan 400
                // A "1.1", "2.3", etc. digit-dot-digit pattern would suggest a sub-class
                // enumeration -- one step closer to the 76 individual solutions than the
                // 5-class structure this enum is scoped to.
                it.description shouldNotMatch """.*\b\d+\.\d+\b.*"""
            }
        }
    })
