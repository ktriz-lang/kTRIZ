package dev.ktriz.render.kuml

import dev.kuml.layout.Size
import java.awt.Font
import java.awt.font.FontRenderContext
import kotlin.math.ceil
import kotlin.math.min

internal const val NODE_FONT_FAMILY = "sans-serif"
internal const val NODE_FONT_SIZE_PX = 14f
internal const val EDGE_LABEL_FONT_SIZE_PX = 12f
internal const val NODE_PAD_X_PX = 18f
internal const val NODE_PAD_Y_PX = 12f
internal const val FONT_SUBSTITUTION_SLACK = 1.10f
internal const val NODE_WIDTH_GRID_PX = 8f
internal const val MIN_NODE_WIDTH_PX = 96f
internal const val MIN_NODE_HEIGHT_PX = 48f
internal const val MAX_NODE_WIDTH_PX = 320f
internal const val MAX_MEASURED_CHARS = 512

/**
 * Defensive, idempotent: guarantees the same metrics pipeline (Font2D, no Toolkit) whether the
 * host JVM process runs with or without a DISPLAY. In the empirical-verification sandbox
 * (kTRIZ-CLAUDE.md's "Empirische Verifikation" for this wave), [Font.getStringBounds] returned
 * the same value with and without this line -- set anyway as insurance against font-substitution
 * differences on other operating systems/JVMs.
 */
private val headlessInit: Unit by lazy {
    System.setProperty("java.awt.headless", "true")
    Unit
}

private val measureFont: Font by lazy {
    headlessInit
    Font(Font.SANS_SERIF, Font.PLAIN, NODE_FONT_SIZE_PX.toInt())
}

/** Same rationale as [measureFont], but at [EDGE_LABEL_FONT_SIZE_PX] -- edge verb labels render smaller than node names. */
private val edgeLabelMeasureFont: Font by lazy {
    headlessInit
    Font(Font.SANS_SERIF, Font.PLAIN, EDGE_LABEL_FONT_SIZE_PX.toInt())
}
private val frc = FontRenderContext(null, true, true)

/** Measured width of [text] in px at [NODE_FONT_SIZE_PX], `Font.SANS_SERIF`, `PLAIN`. */
internal fun measuredTextWidthPx(text: String): Float {
    val capped = if (text.length > MAX_MEASURED_CHARS) text.take(MAX_MEASURED_CHARS) else text
    return measureFont.getStringBounds(capped, frc).width.toFloat()
}

/**
 * Measured width of [text] in px at [EDGE_LABEL_FONT_SIZE_PX] -- used to keep an edge verb
 * label's rendered extent (it is drawn with `text-anchor="middle"` at the route's midpoint, not
 * node-boxed like a component name) inside the canvas bounds. See
 * `FunctionModelSvgRenderer.kt`'s `canvasBoundsFor`.
 */
internal fun measuredEdgeLabelWidthPx(text: String): Float {
    val capped = if (text.length > MAX_MEASURED_CHARS) text.take(MAX_MEASURED_CHARS) else text
    return edgeLabelMeasureFont.getStringBounds(capped, frc).width.toFloat()
}

private fun ceilToGrid(
    value: Float,
    grid: Float,
): Float = ceil(value / grid) * grid

/** Node size for [name] -- measured, padded, snapped to the width grid, clamped between min/max. */
internal fun measureNodeSize(name: String): Size {
    val rawWidth = measuredTextWidthPx(name) * FONT_SUBSTITUTION_SLACK + 2 * NODE_PAD_X_PX
    val clampedWidth = rawWidth.coerceIn(MIN_NODE_WIDTH_PX, MAX_NODE_WIDTH_PX)
    val width = ceilToGrid(clampedWidth, NODE_WIDTH_GRID_PX)
    // "Mg" is a generic ascent+descent reference, not name-dependent -- every node gets the
    // same height, only the width varies with the name (see this file's design note below).
    val metrics = measureFont.getStringBounds("Mg", frc)
    val height = (ceil(-metrics.y).toFloat() + 2 * NODE_PAD_Y_PX).coerceAtLeast(MIN_NODE_HEIGHT_PX)
    return Size(width = width, height = height)
}

internal data class DisplayText(
    val text: String,
    val truncated: Boolean,
)

/** [name] truncated with an ellipsis if it overflows the available inner width [innerWidthPx]. */
internal fun displayTextFor(
    name: String,
    innerWidthPx: Float,
): DisplayText {
    if (measuredTextWidthPx(name) <= innerWidthPx) return DisplayText(name, truncated = false)
    var lo = 0
    var hi = min(name.length, MAX_MEASURED_CHARS)
    // Largest prefix + "…" that still fits (binary search instead of a linear shrink for long names).
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        val candidate = name.take(mid) + "…"
        if (measuredTextWidthPx(candidate) <= innerWidthPx) lo = mid else hi = mid - 1
    }
    val fitted = if (lo == 0) "…" else name.take(lo) + "…"
    return DisplayText(fitted, truncated = true)
}
