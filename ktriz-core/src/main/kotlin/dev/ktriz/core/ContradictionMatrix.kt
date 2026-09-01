package dev.ktriz.core

/**
 * The classical Altshuller contradiction matrix. Rows = improving parameter, columns =
 * worsening parameter; each populated cell holds an *ordered* list of recommended principle
 * ids (order = TRIZ recommendation rank).
 *
 * ## Data source (kTRIZ-ADR-0003)
 *
 * This object loads the full classical 39x39 matrix (1248 populated cells) from the bundled
 * classpath resource `contradiction-matrix-1971.csv`, next to this class. That file is
 * kTRIZ's own reconciled compilation of four independent, freely available transcriptions
 * of the classical Altshuller matrix -- never a 1:1 copy of a single source, and never any
 * part of the later Matrix 2003/2010/2022 (Darrell Mann/CREAX/Systematic Innovation), which
 * kTRIZ ships and will ship no data from. Full sourcing, the reconciliation method, and
 * known anomalies are documented in `docs/matrix-provenance.adoc`.
 *
 * ADR-0003 records an open legal risk that this wave does *not* close: the EU *sui generis*
 * database right that can attach to a concrete digital transcription even when the
 * underlying facts (the classical matrix itself, released into the public domain by
 * Altshuller's and his coworkers' goodwill -- see the provenance doc) are free. Treat this
 * as a *check* position, not a *done* position; see the ADR for the current status.
 *
 * An empty lookup result for an unpopulated cell is *not* distinguishable from "the
 * classical matrix genuinely recommends nothing here". Both are the same empty list. That
 * ambiguity is a known, accepted V1 limitation (see the DSL-Surface note, section 6).
 *
 * The parsing itself is delegated to [ContradictionMatrixData.parse], which is public on
 * purpose -- it is the pluggable matrix-source seam ADR-0003 calls for, so a user holding a
 * Matrix 2003/2010/2022 licence can parse their own file locally without kTRIZ ever shipping
 * that data.
 */
object ContradictionMatrix {
    private const val RESOURCE_NAME = "contradiction-matrix-1971.csv"

    private const val PROVENANCE =
        "Classical Altshuller 39x39 matrix, kTRIZ in-house reconciliation of four independent " +
            "transcriptions, 2026-09-02; see docs/matrix-provenance.adoc"

    private val classical: ContradictionMatrixData by lazy { loadClassical() }

    /**
     * Deterministic, pure lookup. Returns the recommended principles in TRIZ rank order,
     * or an empty list where this table holds no cell -- a legitimate "no classical
     * recommendation" result, never an error. Contrast with an invented parameter, which
     * does not compile in the first place.
     */
    fun lookup(contradiction: Contradiction): List<InventivePrinciple> = classical.lookup(contradiction)

    /**
     * Number of populated cells (1248 in the bundled classical matrix). Exposed so tests can
     * pin the data boundary.
     *
     * Deliberately `public`, not `internal`: `ktriz-tests` is a separate Gradle module/Kotlin
     * compilation unit from `ktriz-core` (a project dependency does not grant `internal`
     * visibility across module boundaries), so an `internal` declaration here would make the
     * boundary test in `ktriz-tests` fail to compile rather than fail to pass.
     */
    val populatedCellCount: Int get() = classical.populatedCellCount

    /** Origin of the bundled data, for attribution/audit. See `docs/matrix-provenance.adoc`. */
    val provenance: String get() = classical.provenance

    private fun loadClassical(): ContradictionMatrixData {
        // Class#getResourceAsStream (not ClassLoader#getResourceAsStream) resolves
        // package-relative to this class with no leading slash -- deliberately not an
        // absolute file path, which would break CI (the classpath resource travels with the
        // jar; a filesystem path does not).
        val stream =
            ContradictionMatrix::class.java.getResourceAsStream(RESOURCE_NAME)
                ?: error(
                    "Bundled contradiction matrix resource '$RESOURCE_NAME' is missing from " +
                        "the classpath next to dev.ktriz.core.ContradictionMatrix. This is a " +
                        "packaging bug -- the file should live at " +
                        "ktriz-core/src/main/resources/dev/ktriz/core/$RESOURCE_NAME.",
                )
        return stream.reader(Charsets.UTF_8).use { reader ->
            ContradictionMatrixData.parse(reader, provenance = PROVENANCE)
        }
    }
}

/** Ergonomic extension so call sites read fluently: `problem.resolve()`. */
fun Contradiction.resolve(): List<InventivePrinciple> = ContradictionMatrix.lookup(this)
