package dev.ktriz.render.kuml

import dev.ktriz.function.FunctionQuality

/** `stroke-dasharray` for [EdgeStrokeStyle.DASHED] -- 8px dash, 4px gap. */
internal const val DASH_ARRAY = "8 4"

/**
 * Perpendicular gap in px between the two parallel paths of an [EdgeStrokeStyle.DOUBLED] edge.
 * With `STROKE_WIDTH_PX = 2f`, a 3px gap left only ~1px of visible white space between the two
 * strokes -- widened to 5px so the doubled line reads clearly as two separate strokes rather
 * than one slightly-fat one (round 1 review feedback, non-blocking).
 */
internal const val DOUBLE_STROKE_GAP_PX = 5f

/** Length in px of the triangular arrowhead drawn at every edge's target, along the route axis. */
internal const val ARROW_LENGTH_PX = 10f

/** Half-width in px of the triangular arrowhead's base, perpendicular to the route axis. */
internal const val ARROW_HALF_WIDTH_PX = 4f

/**
 * kTRIZ's own visual vocabulary for [FunctionQuality] -- kUML's SVG renderer has no generic
 * wavy/doubled-line concept to reuse (kTRIZ-ADR-0002, "Update 2026-08-13"), so these four
 * treatments are defined and drawn entirely by this module (see `EdgeGeometry.kt`).
 */
internal enum class EdgeStrokeStyle { SOLID, DASHED, DOUBLED, WAVY }

/**
 * [FunctionQuality.USEFUL] -> [EdgeStrokeStyle.SOLID] (plain line, the effect works as
 * intended). [FunctionQuality.INSUFFICIENT] -> [EdgeStrokeStyle.DASHED] (present but broken
 * up -- reads as "too weak"). [FunctionQuality.EXCESSIVE] -> [EdgeStrokeStyle.DOUBLED] (two
 * parallel lines -- the engineering-drawing convention for an over-strong/reinforced
 * connection; chosen over a merely thicker line so it stays visually distinct from a bold
 * [FunctionQuality.USEFUL] line at a glance). [FunctionQuality.HARMFUL] ->
 * [EdgeStrokeStyle.WAVY] (a sine-perturbed path -- "disruptive," and a shape-coded distinction
 * rather than a color-only one, for accessibility).
 */
internal fun FunctionQuality.strokeStyle(): EdgeStrokeStyle =
    when (this) {
        FunctionQuality.USEFUL -> EdgeStrokeStyle.SOLID
        FunctionQuality.INSUFFICIENT -> EdgeStrokeStyle.DASHED
        FunctionQuality.EXCESSIVE -> EdgeStrokeStyle.DOUBLED
        FunctionQuality.HARMFUL -> EdgeStrokeStyle.WAVY
    }
