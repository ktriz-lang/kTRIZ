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
 *
 * ## Pluggable matrix source (kTRIZ-ADR-0003)
 *
 * The data this object serves is resolved once, lazily, via [ContradictionMatrixSource.resolve]:
 * by default the bundled classical matrix, or -- if the system property
 * `ktriz.matrix.file` names an absolute local file -- that file instead, loaded through the
 * same hardened gate and the exact same parser as the bundled data. See
 * [ContradictionMatrixSource] for the full mechanism, its threat model, and why it
 * deliberately never falls back silently to the classical matrix on a bad override, and
 * never supports a remote/URL source on any layer. The override is only settable by whoever
 * starts the JVM; it is never derived from network input, a config file parsed from
 * untrusted data, or any per-request value.
 */
object ContradictionMatrix {
    private val resolved: ContradictionMatrixSource.Resolved by lazy { ContradictionMatrixSource.resolve() }

    /**
     * Deterministic, pure lookup. Returns the recommended principles in TRIZ rank order,
     * or an empty list where this table holds no cell -- a legitimate "no classical
     * recommendation" result, never an error. Contrast with an invented parameter, which
     * does not compile in the first place.
     */
    fun lookup(contradiction: Contradiction): List<InventivePrinciple> = resolved.data.lookup(contradiction)

    /**
     * Number of populated cells (1248 in the bundled classical matrix; a different count if
     * `ktriz.matrix.file` is set). Exposed so tests can pin the data boundary.
     *
     * Deliberately `public`, not `internal`: `ktriz-tests` is a separate Gradle module/Kotlin
     * compilation unit from `ktriz-core` (a project dependency does not grant `internal`
     * visibility across module boundaries), so an `internal` declaration here would make the
     * boundary test in `ktriz-tests` fail to compile rather than fail to pass.
     */
    val populatedCellCount: Int get() = resolved.data.populatedCellCount

    /** Origin of the loaded data, for attribution/audit. See `docs/matrix-provenance.adoc`. */
    val provenance: String get() = resolved.data.provenance

    /**
     * Whether [lookup]/[populatedCellCount]/[provenance] currently serve the bundled
     * classical matrix or an operator-supplied external file. See
     * [ContradictionMatrixSource.Origin].
     */
    val origin: ContradictionMatrixSource.Origin get() = resolved.origin
}

/** Ergonomic extension so call sites read fluently: `problem.resolve()`. */
fun Contradiction.resolve(): List<InventivePrinciple> = ContradictionMatrix.lookup(this)

/**
 * Resolves against an explicitly held [matrix] instead of [ContradictionMatrix]'s
 * process-wide default -- for callers who already loaded their own matrix via
 * [ContradictionMatrixSource.fromFile] and want the same fluent call-site shape as the
 * no-argument [resolve]. No overload ambiguity with the no-argument variant: the parameter
 * is mandatory here.
 */
fun Contradiction.resolve(matrix: ContradictionMatrixData): List<InventivePrinciple> = matrix.lookup(this)
