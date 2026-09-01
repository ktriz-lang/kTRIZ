package dev.ktriz.core

import java.io.Reader

/**
 * An immutable, parsed contradiction matrix: `(improving, worsening) -> ordered principles`.
 *
 * This is the pluggable matrix-source seam kTRIZ-ADR-0003 asks for. [ContradictionMatrix]
 * uses [parse] internally to load the classical, bundled 39x39 matrix from a classpath
 * resource -- but [parse] itself is a public, pure function of any [Reader], so a user
 * holding a licence to a later matrix (2003/2010/2022, Darrell Mann/CREAX/Systematic
 * Innovation) can parse *their own* file locally into the same shape. kTRIZ never ships,
 * downloads, or bundles such data itself.
 *
 * Row = improving parameter, column = worsening parameter -- the same convention as the
 * original Altshuller table and as [ContradictionMatrix]. See `docs/matrix-provenance.adoc`
 * for the sourcing, reconciliation method, and known anomalies of the bundled classical
 * matrix.
 */
class ContradictionMatrixData private constructor(
    private val cells: Map<Pair<Int, Int>, List<InventivePrinciple>>,
    /** Human-readable origin of this data, surfaced for attribution/audit. */
    val provenance: String,
) {
    /**
     * Deterministic, pure lookup. An empty list means "this matrix holds no cell for this
     * pair" -- a legitimate result, never an error.
     */
    fun lookup(contradiction: Contradiction): List<InventivePrinciple> =
        cells[contradiction.improving.id to contradiction.worsening.id].orEmpty()

    /** Number of populated cells. */
    val populatedCellCount: Int get() = cells.size

    companion object {
        private const val MIN_PARAMETER_ID = 1
        private const val MAX_PARAMETER_ID = 39
        private const val MIN_PRINCIPLE_ID = 1
        private const val MAX_PRINCIPLE_ID = 40
        private const val EXPECTED_DATA_COLUMNS = 4
        private val WHITESPACE = Regex("\\s+")
        private val PURELY_NUMERIC = Regex("\\d+")

        /**
         * Parses the kTRIZ matrix CSV shape:
         * ```
         * improving,worsening,principles,sources
         * 1,3,15 8 29 34,ANT+FFS+SOR+XLS
         * ```
         * - Blank lines and lines starting with `#` are comments and are skipped.
         * - The literal header line `improving,worsening,principles...` is skipped if present.
         * - `principles` is a **space**-separated list of principle ids (1..40), highest rank
         *   first, with no id repeated. A comma-separated principle list (`15,8,29,34`) is
         *   rejected, not silently reinterpreted as extra columns -- see the column-count check
         *   below.
         * - Exactly four comma-separated columns are required per data line: `improving`,
         *   `worsening`, `principles`, `sources`. The fourth column is a free-form provenance
         *   note; it is never purely numeric in the shipped matrix, so a purely numeric
         *   `sources` value is rejected -- it is far more likely to be a stray principle id
         *   from a comma-separated principles list that happened to land on exactly four
         *   columns (e.g. `1,3,15,8` meaning principles `15,8`) than a genuine provenance note.
         *
         * Every violation below throws [IllegalStateException] with a `line <n>:` prefixed,
         * self-explanatory message -- these are meant to double as generate-compile-repair
         * signals for anyone hand-authoring or generating a matrix file.
         *
         * @throws IllegalStateException on any malformed line, a duplicate principle id within
         *  one cell's principle list, or a duplicate `(improving, worsening)` key across the
         *  whole file.
         */
        fun parse(
            source: Reader,
            provenance: String,
        ): ContradictionMatrixData {
            val cells = mutableMapOf<Pair<Int, Int>, List<InventivePrinciple>>()

            source.buffered().useLines { lines ->
                lines.forEachIndexed { index, rawLine ->
                    val lineNumber = index + 1
                    val line = rawLine.trim()

                    if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
                    if (line.startsWith("improving,worsening,principles")) return@forEachIndexed

                    val columns = line.split(",")
                    check(columns.size == EXPECTED_DATA_COLUMNS) {
                        "line $lineNumber: expected exactly $EXPECTED_DATA_COLUMNS comma-separated " +
                            "columns (improving,worsening,principles,sources), found ${columns.size}: " +
                            "'$line' -- if this is meant to be a comma-separated principles list " +
                            "(e.g. '15,8,29,34'), use spaces instead ('15 8 29 34')"
                    }
                    check(!PURELY_NUMERIC.matches(columns[3].trim())) {
                        "line $lineNumber: 'sources' column is purely numeric ('${columns[3].trim()}') -- " +
                            "a genuine provenance note is never purely numeric, so this looks like a " +
                            "comma-separated principles list that happened to land on exactly four " +
                            "columns (e.g. '$line' meaning principles '${columns[2].trim()},${columns[3].trim()}') " +
                            "-- use a space-separated principles list instead ('${columns[2].trim()} " +
                            "${columns[3].trim()}') and a real provenance note in the sources column"
                    }

                    val improving = parseParameterId(columns[0].trim(), lineNumber, "improving")
                    val worsening = parseParameterId(columns[1].trim(), lineNumber, "worsening")
                    check(improving != worsening) {
                        "line $lineNumber: improving and worsening are both '$improving' -- " +
                            "a parameter cannot contradict itself, the diagonal must stay empty"
                    }

                    val principleTokens = columns[2].trim().split(WHITESPACE).filter { it.isNotEmpty() }
                    check(principleTokens.isNotEmpty()) {
                        "line $lineNumber: empty principles list for cell ($improving, $worsening) -- " +
                            "an unpopulated cell must be omitted entirely, not written with no principles"
                    }
                    check(principleTokens.size == principleTokens.toSet().size) {
                        "line $lineNumber: duplicate principle id in the principles list for cell " +
                            "($improving, $worsening): '${columns[2].trim()}' -- a ranked principle " +
                            "list must not repeat an id; deduplicate it before shipping this file"
                    }

                    // Collections.unmodifiableList (not just the `List` return type of `.map`,
                    // which is a castable-back-to-MutableList ArrayList under the hood) so a
                    // caller cannot mutate a cell's principle list via an unchecked cast.
                    val principles =
                        java.util.Collections.unmodifiableList(
                            principleTokens
                                .map { token -> parsePrincipleId(token, lineNumber) }
                                .map(InventivePrinciple::ofId),
                        )

                    val key = improving to worsening
                    check(key !in cells) {
                        "line $lineNumber: duplicate cell ($improving, $worsening) -- " +
                            "already defined earlier in this file"
                    }
                    cells[key] = principles
                }
            }

            return ContradictionMatrixData(cells = cells.toMap(), provenance = provenance)
        }

        private fun parseParameterId(
            token: String,
            lineNumber: Int,
            columnName: String,
        ): Int {
            val value =
                token.toIntOrNull()
                    ?: error("line $lineNumber: '$columnName' is not a valid integer: '$token'")
            check(value in MIN_PARAMETER_ID..MAX_PARAMETER_ID) {
                "line $lineNumber: '$columnName' id $value is out of range " +
                    "($MIN_PARAMETER_ID..$MAX_PARAMETER_ID) -- this parser only accepts the " +
                    "classical 39-parameter matrix, not a Matrix 2003/2010/2022 (48-parameter) file"
            }
            return value
        }

        private fun parsePrincipleId(
            token: String,
            lineNumber: Int,
        ): Int {
            val value =
                token.toIntOrNull()
                    ?: error("line $lineNumber: principle id is not a valid integer: '$token'")
            check(value in MIN_PRINCIPLE_ID..MAX_PRINCIPLE_ID) {
                "line $lineNumber: principle id $value is out of range " +
                    "($MIN_PRINCIPLE_ID..$MAX_PRINCIPLE_ID)"
            }
            return value
        }
    }
}
