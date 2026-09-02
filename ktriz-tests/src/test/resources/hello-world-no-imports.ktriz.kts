// hello-world-no-imports.ktriz.kts
//
// Identical to hello-world.ktriz.kts, minus the `import dev.ktriz.core.*` line -- proves that
// KtrizScriptCompilationConfiguration's defaultImports actually make dev.ktriz.core.* resolvable
// without an explicit import (see KtrizScript.kt).

// ── Problem (natürliche Sprache, vom Nutzer) ──────────────────────────────
// "Ich will das Fahrgestell leichter machen, aber sobald ich Material
//  wegnehme, wird es zu schwach und bricht."
//
// ── Vom LLM extrahierte, typisierte Contradiction ─────────────────────────
// improving: Gewicht des beweglichen Objekts   verschlechtert: Festigkeit
val problem = contradiction(
    improving = EngineeringParameter.WEIGHT_OF_MOVING_OBJECT,
    worsening = EngineeringParameter.STRENGTH,
)

// ── Deterministischer Matrix-Lookup ───────────────────────────────────────
val principles = problem.resolve()

// ── Ausgabe ───────────────────────────────────────────────────────────────
println("Widerspruch: ${problem.improving.labelDe} ↑  vs.  ${problem.worsening.labelDe} ↓")
println("Empfohlene erfinderische Prinzipien:")
principles.forEach { p -> println("  #${p.id}  ${p.labelDe}  (${p.labelEn})") }
