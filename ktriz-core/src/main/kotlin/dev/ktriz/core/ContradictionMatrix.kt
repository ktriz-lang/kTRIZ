package dev.ktriz.core

/**
 * The classical Altshuller contradiction matrix. Rows = improving parameter,
 * columns = worsening parameter; each populated cell holds an *ordered* list of
 * recommended principle ids (order = TRIZ recommendation rank).
 *
 * ## Deliberately incomplete -- do not "complete" this table
 *
 * The full classical matrix is 39x39. This object intentionally contains **only the three
 * example cells already documented in the vault note "kTRIZ - DSL-Surface (Hello World)"**,
 * because the sourcing and licence clearance of a complete, transcribed matrix is its own
 * work item under **kTRIZ-ADR-0003 (Matrix-Datenbasis)** and is explicitly reserved for a
 * later wave with user sign-off. ADR-0003 records two open legal risks that must be settled
 * first: the EU *sui generis* database right that can attach to a concrete digital
 * transcription even when the underlying facts are free, and third-party IP in derived
 * illustrations. It is a *check* position, not a *done* position.
 *
 * TODO(kTRIZ-ADR-0003): populate the remaining cells of the 39x39 classical matrix from a
 *  provably freely-licensed source (or a clean in-house transcription), move the data out of
 *  this file into a bundled classpath resource (CSV/JSON, loaded via `getResourceAsStream`,
 *  never from an absolute path -- that breaks CI), and add the pluggable
 *  matrix-source seam ADR-0003 calls for so users holding a Matrix 2003/2010 licence can
 *  feed in their own data without kTRIZ ever shipping it.
 *
 * Until then, an empty lookup result for an unpopulated cell is *not* distinguishable from
 * "the classical matrix genuinely recommends nothing here". Both are the same empty list.
 * That ambiguity is a known, accepted V1 limitation (see the DSL-Surface note, section 6).
 */
object ContradictionMatrix {
    /**
     * (improving.id, worsening.id) -> ordered principle ids.
     *
     * Placeholder subset only -- see this object's KDoc and kTRIZ-ADR-0003 before adding
     * anything here.
     */
    private val cells: Map<Pair<Int, Int>, List<Int>> =
        mapOf(
            // Weight of moving object x Strength
            (1 to 14) to listOf(1, 8, 40, 15),
            // Weight of moving object x Length of moving object
            (1 to 3) to listOf(8, 15, 29, 34),
            // Force x Strength
            (10 to 14) to listOf(35, 10, 14, 27),
        )

    /**
     * Deterministic, pure lookup. Returns the recommended principles in TRIZ rank order,
     * or an empty list where this table holds no cell -- a legitimate "no classical
     * recommendation" result, never an error. Contrast with an invented parameter, which
     * does not compile in the first place.
     */
    fun lookup(contradiction: Contradiction): List<InventivePrinciple> =
        cells[contradiction.improving.id to contradiction.worsening.id]
            .orEmpty()
            .map(InventivePrinciple::ofId)

    /**
     * Number of populated cells. Exposed so tests can pin the ADR-0003 boundary.
     *
     * Deliberately `public`, not `internal`: `ktriz-tests` is a separate Gradle module/Kotlin
     * compilation unit from `ktriz-core` (a project dependency does not grant `internal`
     * visibility across module boundaries), so an `internal` declaration here would make the
     * ADR-0003 boundary test in `ktriz-tests` fail to compile rather than fail to pass.
     */
    val populatedCellCount: Int get() = cells.size
}

/** Ergonomic extension so call sites read fluently: `problem.resolve()`. */
fun Contradiction.resolve(): List<InventivePrinciple> = ContradictionMatrix.lookup(this)
