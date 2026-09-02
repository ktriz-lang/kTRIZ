// function-model-svg.ktriz.kts
//
// Proves FunctionModel.renderSvg() (module ktriz-render-kuml) is callable from inside a
// .ktriz.kts script -- the gap this wave closes (see README.adoc's <<roadmap>>). No explicit
// `import dev.ktriz.render.kuml.*` on purpose: KtrizScriptCompilationConfiguration's
// defaultImports now carries that package -- proving the default import actually works,
// mirroring hello-world-no-imports.ktriz.kts's role for dev.ktriz.core.*/dev.ktriz.function.*.

val fm =
    functionModel {
        val engine = component("Engine")
        val coolant = component("Coolant")
        useful(from = coolant, to = engine, verb = "cools")
    }

val svg = fm.renderSvg()
println(svg)
svg
