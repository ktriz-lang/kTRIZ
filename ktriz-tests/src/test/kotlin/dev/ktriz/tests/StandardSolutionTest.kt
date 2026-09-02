package dev.ktriz.tests

import dev.ktriz.sufield.StandardSolution
import dev.ktriz.sufield.StandardSolution.APPLY_DECOMPOSITION_AND_JOINING_AT_NEXT_STRUCTURAL_LEVEL
import dev.ktriz.sufield.StandardSolution.COMPLETE_INCOMPLETE_MODEL
import dev.ktriz.sufield.StandardSolution.INDIRECT_SUBSTANCE_INTRODUCTION
import dev.ktriz.sufield.StandardSolution.MATCH_RHYTHMS_OF_TWO_FIELDS
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

class StandardSolutionTest :
    StringSpec({
        "enum declares exactly 76 standard solutions" {
            StandardSolution.entries.size shouldBe 76
        }

        "codes are unique across all 76 entries" {
            val codes = StandardSolution.entries.map { it.code }
            codes.size shouldBe codes.toSet().size
        }

        "every entry carries a non-blank code and English label" {
            StandardSolution.entries.forAll {
                it.code.shouldNotBeBlank()
                it.labelEn.shouldNotBeBlank()
            }
        }

        "per-class counts sum to the documented 13/23/6/17/17 split" {
            val byClass = StandardSolution.entries.groupBy { it.standardClass }
            byClass[SYNTHESIS_AND_DESTRUCTION]?.size shouldBe 13
            byClass[DEVELOPMENT]?.size shouldBe 23
            byClass[TRANSITION_TO_SUPERSYSTEM_OR_MICROLEVEL]?.size shouldBe 6
            byClass[MEASUREMENT_AND_DETECTION]?.size shouldBe 17
            byClass[APPLICATION_STRATEGIES]?.size shouldBe 17
        }

        "every StandardSolutionClass's solutionCount matches the actual number of StandardSolution entries in it" {
            StandardSolutionClass.entries.forAll { cls ->
                StandardSolution.entries.count { it.standardClass == cls } shouldBe cls.solutionCount
            }
        }

        "grand total across all classes is 76" {
            StandardSolution.entries
                .groupBy { it.standardClass }
                .values
                .sumOf { it.size } shouldBe 76
        }

        "ofCode round-trips every declared entry" {
            StandardSolution.entries.forAll { StandardSolution.ofCode(it.code) shouldBe it }
        }

        "ofCode throws for an unknown code" {
            listOf("0.0.0", "1.1.9", "5.1.1.1", "6.1.1", "", "bogus").forAll { invalidCode ->
                shouldThrow<IllegalStateException> { StandardSolution.ofCode(invalidCode) }
            }
        }

        "the ofCode failure message names the offending code" {
            val exception = shouldThrow<IllegalStateException> { StandardSolution.ofCode("9.9.9") }
            exception.message shouldContain "code=9.9.9"
        }

        // Spot checks -- a regression guard, not exhaustive per-item verification (that
        // happens in the dedicated transcription-verification step of the review pipeline).
        "spot check: 1.1.1 (first item) matches the extraction" {
            COMPLETE_INCOMPLETE_MODEL.code shouldBe "1.1.1"
            COMPLETE_INCOMPLETE_MODEL.standardClass shouldBe SYNTHESIS_AND_DESTRUCTION
            COMPLETE_INCOMPLETE_MODEL.labelEn shouldBe
                "Complete an incomplete model. If there is only an object S1, add a second " +
                "object S2 and an interaction (field) F."
        }

        "spot check: 5.5.3 (last item) matches the extraction" {
            APPLY_DECOMPOSITION_AND_JOINING_AT_NEXT_STRUCTURAL_LEVEL.code shouldBe "5.5.3"
            APPLY_DECOMPOSITION_AND_JOINING_AT_NEXT_STRUCTURAL_LEVEL.standardClass shouldBe APPLICATION_STRATEGIES
            APPLY_DECOMPOSITION_AND_JOINING_AT_NEXT_STRUCTURAL_LEVEL.labelEn shouldBe
                "Applying the Standard Solutions 5.5.1 and 5.5.2. If a substance of a high " +
                "structural level has to be decomposed, and it cannot be decomposed, start " +
                "with the substance of the next highest level. Likewise, if a substance must " +
                "be formed from materials of a low structural level, and it cannot be, then " +
                "start with the next higher level of structure. In the antenna problem (5.4.1) " +
                "the gas molecules are ionized, not the whole antenna, to create the path for " +
                "the lightning, and the ions and electrons are recombined to restore neutrality."
        }

        "spot check: 2.3.2 (middle class, Development) matches the extraction" {
            MATCH_RHYTHMS_OF_TWO_FIELDS.code shouldBe "2.3.2"
            MATCH_RHYTHMS_OF_TWO_FIELDS.standardClass shouldBe DEVELOPMENT
            MATCH_RHYTHMS_OF_TWO_FIELDS.labelEn shouldBe "Matching the rhythms of F1 and F2."
        }

        "spot check: 5.1.1's numbering-anomaly resolution -- folded into one entry, not nine" {
            INDIRECT_SUBSTANCE_INTRODUCTION.code shouldBe "5.1.1"
            INDIRECT_SUBSTANCE_INTRODUCTION.standardClass shouldBe APPLICATION_STRATEGIES
            INDIRECT_SUBSTANCE_INTRODUCTION.labelEn shouldContain "Indirect ways"
            INDIRECT_SUBSTANCE_INTRODUCTION.labelEn shouldContain "use nothing"
            INDIRECT_SUBSTANCE_INTRODUCTION.labelEn shouldContain
                "decomposition of either the environment or the object itself"
            // No StandardSolution exists for the source's own sub-enumeration (5.1.1.1 etc.) --
            // it is not a valid classical code and must not resolve.
            shouldThrow<IllegalStateException> { StandardSolution.ofCode("5.1.1.1") }
        }

        "no description field distinct from labelEn (labels stay short prose)" {
            // Heuristic guard mirroring StandardSolutionClassTest's own description-length
            // check -- individual solution texts are naturally longer than the 5-class
            // descriptions, so this bound is generous, not tight.
            StandardSolution.entries.forAll { it.labelEn.length shouldBeLessThan 1000 }
        }
    })
