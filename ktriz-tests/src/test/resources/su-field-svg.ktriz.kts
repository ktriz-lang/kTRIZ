// su-field-svg.ktriz.kts
//
// Proves SuField.renderSvg() (module ktriz-render-kuml) is callable from inside a .ktriz.kts
// script -- and that suField { } itself resolves with no explicit import, exercising the new
// "dev.ktriz.sufield.*" default import (see README.adoc's <<su-field-svg>>). No explicit
// imports on purpose -- mirrors function-model-svg.ktriz.kts's role for
// "dev.ktriz.render.kuml.*".

val sf =
    suField {
        val workpiece = component("Workpiece")
        val tool = component("Grinding wheel")
        s1(workpiece)
        s2(tool)
        field(FieldType.MECHANICAL)
        quality(SuFieldQuality.INSUFFICIENT)
    }

val svg = sf.renderSvg()
println(svg)
svg
