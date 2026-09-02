// hello-world.ktriz.kts
//
// Verbatim from the "kTRIZ - DSL-Surface (Hello World)" design note, section 5, including its
// `import dev.ktriz.core.*` line. That import is redundant -- dev.ktriz.core.* is already in
// KtrizScriptCompilationConfiguration's defaultImports (see KtrizScript.kt) -- but kept here on
// purpose, to prove the exact script from the note runs unmodified. See
// hello-world-no-imports.ktriz.kts for the same script proving defaultImports actually works.
import dev.ktriz.core.*

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
