package dev.ktriz.tests

import dev.ktriz.core.ContradictionMatrixData
import dev.ktriz.core.EngineeringParameter.LENGTH_OF_MOVING_OBJECT
import dev.ktriz.core.EngineeringParameter.WEIGHT_OF_MOVING_OBJECT
import dev.ktriz.core.InventivePrinciple
import dev.ktriz.core.contradiction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.StringReader

/**
 * Unit tests for the [ContradictionMatrixData] parser -- the pluggable matrix-source seam
 * kTRIZ-ADR-0003 calls for. [ContradictionMatrixTest] covers the bundled classical matrix
 * end to end; this suite covers the parser's own contract in isolation, including every
 * malformed-input path.
 */
class ContradictionMatrixDataTest :
    StringSpec({
        "parses a happy-path file with two cells and an extra sources column" {
            val csv =
                """
                improving,worsening,principles,sources
                1,3,15 8 29 34,ANT+FFS+SOR+XLS
                3,1,8 15 29 34,ANT+SOR+XLS
                """.trimIndent()

            val data = ContradictionMatrixData.parse(StringReader(csv), provenance = "test")

            data.populatedCellCount shouldBe 2
            data.lookup(
                contradiction(improving = WEIGHT_OF_MOVING_OBJECT, worsening = LENGTH_OF_MOVING_OBJECT),
            ) shouldBe
                listOf(
                    InventivePrinciple.DYNAMICS,
                    InventivePrinciple.ANTI_WEIGHT,
                    InventivePrinciple.PNEUMATICS_AND_HYDRAULICS,
                    InventivePrinciple.DISCARDING_AND_RECOVERING,
                )
            data.lookup(
                contradiction(improving = LENGTH_OF_MOVING_OBJECT, worsening = WEIGHT_OF_MOVING_OBJECT),
            ) shouldBe
                listOf(
                    InventivePrinciple.ANTI_WEIGHT,
                    InventivePrinciple.DYNAMICS,
                    InventivePrinciple.PNEUMATICS_AND_HYDRAULICS,
                    InventivePrinciple.DISCARDING_AND_RECOVERING,
                )
        }

        "skips comment lines, blank lines and the header line" {
            val csv =
                """
                # a comment
                improving,worsening,principles,sources

                1,3,15 8 29 34,ANT+FFS+SOR+XLS
                # another comment in the middle
                """.trimIndent()

            val data = ContradictionMatrixData.parse(StringReader(csv), provenance = "test")

            data.populatedCellCount shouldBe 1
        }

        "rejects a parameter id of 0" {
            val error =
                shouldThrow<IllegalStateException> {
                    ContradictionMatrixData.parse(StringReader("0,3,15,X"), provenance = "test")
                }

            error.message shouldContain "line 1"
            error.message shouldContain "out of range"
        }

        "rejects a parameter id of 40 (one past the classical 39)" {
            val error =
                shouldThrow<IllegalStateException> {
                    ContradictionMatrixData.parse(StringReader("40,3,15,X"), provenance = "test")
                }

            error.message shouldContain "out of range"
        }

        "rejects a parameter id of 48 (a Matrix 2003/2010-shaped file)" {
            val error =
                shouldThrow<IllegalStateException> {
                    ContradictionMatrixData.parse(StringReader("1,48,15,X"), provenance = "test")
                }

            error.message shouldContain "out of range"
            error.message shouldContain "48-parameter"
        }

        "rejects a diagonal cell (improving equals worsening)" {
            val error =
                shouldThrow<IllegalStateException> {
                    ContradictionMatrixData.parse(StringReader("5,5,15,X"), provenance = "test")
                }

            error.message shouldContain "cannot contradict itself"
        }

        "rejects a principle id of 0" {
            val error =
                shouldThrow<IllegalStateException> {
                    ContradictionMatrixData.parse(StringReader("1,3,0,X"), provenance = "test")
                }

            error.message shouldContain "out of range"
        }

        "rejects a principle id of 41 (one past the classical 40)" {
            val error =
                shouldThrow<IllegalStateException> {
                    ContradictionMatrixData.parse(StringReader("1,3,41,X"), provenance = "test")
                }

            error.message shouldContain "out of range"
        }

        "rejects a duplicate cell key" {
            val csv =
                """
                1,3,15 8,X
                1,3,29 34,X
                """.trimIndent()

            val error =
                shouldThrow<IllegalStateException> {
                    ContradictionMatrixData.parse(StringReader(csv), provenance = "test")
                }

            error.message shouldContain "line 2"
            error.message shouldContain "duplicate cell"
        }

        "rejects a non-numeric improving field" {
            val error =
                shouldThrow<IllegalStateException> {
                    ContradictionMatrixData.parse(StringReader("one,3,15,X"), provenance = "test")
                }

            error.message shouldContain "not a valid integer"
        }

        "rejects a line with fewer than four columns" {
            val error =
                shouldThrow<IllegalStateException> {
                    ContradictionMatrixData.parse(StringReader("1,3"), provenance = "test")
                }

            error.message shouldContain "exactly 4"
        }

        "rejects a comma-separated principles list instead of truncating it silently" {
            // A naive reader might see this as columns[2] == "15" and silently drop
            // 8, 29, 34 -- the column-count check must catch this instead.
            val error =
                shouldThrow<IllegalStateException> {
                    ContradictionMatrixData.parse(StringReader("1,3,15,8,29,34"), provenance = "test")
                }

            error.message shouldContain "exactly 4"
            error.message shouldContain "found 6"
        }

        "rejects a two-principle comma-separated list that lands on exactly four columns" {
            // "1,3,15,8" means principles "15,8" written with a comma instead of a space --
            // it silently parses as four columns (improving=1, worsening=3, principles=15,
            // sources=8) unless the purely-numeric-sources guard catches it.
            val error =
                shouldThrow<IllegalStateException> {
                    ContradictionMatrixData.parse(StringReader("1,3,15,8"), provenance = "test")
                }

            error.message shouldContain "line 1"
            error.message shouldContain "purely numeric"
        }

        "rejects a duplicate principle id within one cell's principles list" {
            val error =
                shouldThrow<IllegalStateException> {
                    ContradictionMatrixData.parse(StringReader("19,9,8 35 35,X"), provenance = "test")
                }

            error.message shouldContain "line 1"
            error.message shouldContain "duplicate principle id"
        }

        "rejects an empty principles list" {
            val error =
                shouldThrow<IllegalStateException> {
                    ContradictionMatrixData.parse(StringReader("1,3,,X"), provenance = "test")
                }

            error.message shouldContain "empty principles list"
        }

        "error messages carry a 1-based line number" {
            val csv =
                """
                1,3,15 8,X
                2,4,29 34,X
                5,5,1,X
                """.trimIndent()

            val error =
                shouldThrow<IllegalStateException> {
                    ContradictionMatrixData.parse(StringReader(csv), provenance = "test")
                }

            error.message shouldContain "line 3"
        }

        "carries the provenance string through unchanged" {
            val data =
                ContradictionMatrixData.parse(
                    StringReader("1,3,15,X"),
                    provenance = "unit-test-provenance-marker",
                )

            data.provenance shouldBe "unit-test-provenance-marker"
        }
    })
