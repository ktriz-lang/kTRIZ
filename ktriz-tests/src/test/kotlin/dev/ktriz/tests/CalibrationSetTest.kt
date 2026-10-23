package dev.ktriz.tests

import dev.ktriz.core.ContradictionMatrix
import dev.ktriz.core.ContradictionMatrixSource
import dev.ktriz.core.EngineeringParameter
import dev.ktriz.core.contradiction
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Validates `benchmark/calibration-set.csv` (see `docs/calibration-set.adoc`).
 *
 * The CSV lives at the repository root and is handed in by Gradle as the system property
 * [CSV_PROPERTY] (absolute path), because the test working directory is the module directory.
 * There is deliberately no fallback path: a missing property fails loudly.
 */
class CalibrationSetTest :
    StringSpec({

        val header =
            listOf(
                "id",
                "description_en",
                "improving_id",
                "worsening_id",
                "principle_ids",
                "source_url",
                "source_citation",
                "licence",
                "confidence",
                "matrix_cell_populated",
                "notes",
            )

        val rows: List<List<String>> by lazy { parseCsv(loadCsvText()) }
        val cases: List<CalibrationCase> by lazy { rows.drop(1).map { CalibrationCase.of(it) } }

        // --- parser self-test (no file involved) ---

        "parser handles commas, escaped quotes, CRLF and empty trailing fields" {
            val parsed = parseCsv("a,\"b,c\",\"say \"\"hi\"\"\",\r\nx,,y,\r\n")
            parsed shouldBe
                listOf(
                    listOf("a", "b,c", "say \"hi\"", ""),
                    listOf("x", "", "y", ""),
                )
        }

        "parser accepts a missing final newline and a leading BOM" {
            parseCsv("a,b\nc,d") shouldBe listOf(listOf("a", "b"), listOf("c", "d"))
        }

        "parser rejects an unterminated quote" {
            shouldThrow<IllegalStateException> { parseCsv("a,\"b\nc,d\n") }
        }

        // --- calibration set ---

        "the matrix under test is the bundled one" {
            ContradictionMatrix.origin shouldBe ContradictionMatrixSource.Origin.BUNDLED_CLASSICAL
        }

        "header has exactly the expected columns in order" {
            rows.first() shouldBe header
        }

        "every data row has exactly eleven fields" {
            rows.drop(1).forEachIndexed { index, row ->
                withClue("data row ${index + 1} (${row.firstOrNull()})") { row shouldHaveSize header.size }
            }
        }

        "ids are present, unique and slug-shaped" {
            (cases.size >= 1) shouldBe true
            cases.map { it.id }.toSet() shouldHaveSize cases.size
            cases.forEach { check(Regex("[a-z0-9-]+").matches(it.id)) { "bad id '${it.id}'" } }
        }

        "parameter ids are integers in 1..39 and differ" {
            cases.forEach { c ->
                check(c.improvingId in 1..39) { "${c.id}: improving_id ${c.improvingId} outside 1..39" }
                check(c.worseningId in 1..39) { "${c.id}: worsening_id ${c.worseningId} outside 1..39" }
                check(c.improvingId != c.worseningId) { "${c.id}: improving_id equals worsening_id" }
            }
        }

        "matrix_cell_populated agrees with the bundled matrix" {
            cases.forEach { c ->
                val cell = ContradictionMatrix.lookup(c.contradiction())
                check(cell.isNotEmpty() == c.matrixCellPopulated) {
                    "${c.id}: matrix_cell_populated=${c.matrixCellPopulated} but the cell has ${cell.size} principles"
                }
            }
        }

        "listed principle ids are in 1..40 and equal the matrix cell, or the mismatch is documented" {
            cases.filter { it.principleIds.isNotEmpty() }.forEach { c ->
                c.principleIds.forEach { check(it in 1..40) { "${c.id}: principle id $it outside 1..40" } }
                val cell = ContradictionMatrix.lookup(c.contradiction()).map { it.id }
                if (c.principleIds != cell) {
                    check(c.notes.contains(MISMATCH_MARKER)) {
                        "${c.id}: principle_ids ${c.principleIds} differ from matrix cell $cell " +
                            "and notes carry no '$MISMATCH_MARKER' marker"
                    }
                }
            }
        }

        "source urls are https and parseable" {
            cases.forEach { c ->
                check(c.sourceUrl.startsWith("https://")) { "${c.id}: source_url must start with https://" }
                check(c.sourceUrl.none { it.isWhitespace() }) { "${c.id}: source_url contains whitespace" }
                check(URI(c.sourceUrl).host != null) { "${c.id}: source_url has no host" }
            }
        }

        "description, citation and licence are non-blank" {
            cases.forEach { c ->
                check(c.descriptionEn.isNotBlank()) { "${c.id}: description_en blank" }
                check(c.sourceCitation.isNotBlank()) { "${c.id}: source_citation blank" }
                check(c.licence.isNotBlank()) { "${c.id}: licence blank" }
            }
        }

        "confidence and matrix_cell_populated use the allowed vocabulary" {
            cases.forEach { c ->
                check(c.confidence in setOf("high", "medium", "low")) { "${c.id}: bad confidence '${c.confidence}'" }
            }
            rows.drop(1).forEach { row ->
                check(
                    row[9] == "true" || row[9] == "false",
                ) { "${row[0]}: matrix_cell_populated must be true or false" }
            }
        }

        "high confidence requires a populated cell and a named attributable source" {
            cases.forEach { c ->
                if (c.confidence == "high") {
                    check(c.matrixCellPopulated) { "${c.id}: high confidence with an empty matrix cell" }
                }
                if (c.licence.contains("CC BY")) {
                    check(listOf("WIPO", "Stevko", "MATRIZ").any { c.sourceCitation.contains(it) }) {
                        "${c.id}: CC BY case without an attributable source in source_citation"
                    }
                }
            }
        }

        "the set stays honest about its confidence" {
            cases.map { it.confidence } shouldContain "low"
            cases.filter { it.id.startsWith("matriz") }.forEach { c ->
                check(c.confidence != "high") { "${c.id}: MATRIZ-derived case must not be high confidence" }
            }
        }
    })

private const val CSV_PROPERTY = "ktriz.calibration.csv"
private const val MISMATCH_MARKER = "principle-mismatch:"

private fun loadCsvText(): String {
    val raw =
        System.getProperty(CSV_PROPERTY)
            ?: error(
                "System property '$CSV_PROPERTY' is not set; run the test through Gradle (ktriz-tests/build.gradle.kts sets it).",
            )
    val path = Path.of(raw)
    check(Files.isRegularFile(path)) { "Calibration CSV not found at $path" }
    return Files.readString(path, StandardCharsets.UTF_8)
}

private fun <T> withClue(
    clue: String,
    block: () -> T,
): T =
    try {
        block()
    } catch (e: AssertionError) {
        throw AssertionError("$clue: ${e.message}", e)
    }

private data class CalibrationCase(
    val id: String,
    val descriptionEn: String,
    val improvingId: Int,
    val worseningId: Int,
    val principleIds: List<Int>,
    val sourceUrl: String,
    val sourceCitation: String,
    val licence: String,
    val confidence: String,
    val matrixCellPopulated: Boolean,
    val notes: String,
) {
    fun contradiction() =
        contradiction(
            improving = EngineeringParameter.ofId(improvingId),
            worsening = EngineeringParameter.ofId(worseningId),
        )

    companion object {
        fun of(row: List<String>): CalibrationCase {
            check(row.size == 11) { "row '${row.firstOrNull()}' has ${row.size} fields, expected 11" }

            fun int(
                index: Int,
                name: String,
            ) = row[index].trim().toIntOrNull() ?: error("${row[0]}: $name '${row[index]}' is not an integer")
            return CalibrationCase(
                id = row[0],
                descriptionEn = row[1],
                improvingId = int(2, "improving_id"),
                worseningId = int(3, "worsening_id"),
                principleIds =
                    row[4].trim().let { s ->
                        if (s.isEmpty()) {
                            emptyList()
                        } else {
                            s.split(';').map {
                                it.trim().toIntOrNull()
                                    ?: error("${row[0]}: bad principle id '$it'")
                            }
                        }
                    },
                sourceUrl = row[5],
                sourceCitation = row[6],
                licence = row[7],
                confidence = row[8],
                matrixCellPopulated = row[9].trim() == "true",
                notes = row[10],
            )
        }
    }
}

/** RFC 4180-light: quoted fields, `""` escape, comma separator, LF or CRLF line ends. */
internal fun parseCsv(input: String): List<List<String>> {
    val text = input.removePrefix("﻿")
    val result = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var i = 0
    var pending = false // true if the current row has any content (field text or separator)
    while (i < text.length) {
        val ch = text[i]
        if (inQuotes) {
            if (ch == '"') {
                if (i + 1 < text.length && text[i + 1] == '"') {
                    field.append('"')
                    i++
                } else {
                    inQuotes = false
                }
            } else {
                field.append(ch)
            }
        } else {
            when (ch) {
                '"' -> {
                    inQuotes = true
                    pending = true
                }
                ',' -> {
                    row.add(field.toString())
                    field.setLength(0)
                    pending = true
                }
                '\r' -> {}
                '\n' -> {
                    if (pending || field.isNotEmpty()) {
                        row.add(field.toString())
                        result.add(row)
                    }
                    row = mutableListOf()
                    field.setLength(0)
                    pending = false
                }
                else -> {
                    field.append(ch)
                    pending = true
                }
            }
        }
        i++
    }
    check(!inQuotes) { "Unterminated quoted field in CSV" }
    if (pending || field.isNotEmpty()) {
        row.add(field.toString())
        result.add(row)
    }
    return result
}
