package dev.ktriz.render.kuml

import dev.kuml.layout.Rect
import dev.kuml.layout.Size
import java.awt.Font
import java.awt.font.FontRenderContext
import kotlin.math.ceil
import kotlin.math.max
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

/**
 * [name] truncated with an ellipsis if it overflows the available inner width [innerWidthPx],
 * measured via [measure] -- defaults to [measuredTextWidthPx] (node-name font size), so every
 * existing call site keeps its exact prior behaviour byte-for-byte. `SuFieldSvgRenderer.kt`
 * passes [measuredEdgeLabelWidthPx] for the Su-Field field-name label, which renders at the
 * smaller edge-label font size, not the node-name font size this default assumes.
 */
internal fun displayTextFor(
    name: String,
    innerWidthPx: Float,
    measure: (String) -> Float = ::measuredTextWidthPx,
): DisplayText {
    if (measure(name) <= innerWidthPx) return DisplayText(name, truncated = false)
    var lo = 0
    var hi = min(name.length, MAX_MEASURED_CHARS)
    // Largest prefix + "…" that still fits (binary search instead of a linear shrink for long names).
    while (lo < hi) {
        val mid = (lo + hi + 1) / 2
        val candidate = name.take(mid) + "…"
        if (measure(candidate) <= innerWidthPx) lo = mid else hi = mid - 1
    }
    val fitted = if (lo == 0) "…" else name.take(lo) + "…"
    return DisplayText(fitted, truncated = true)
}

/**
 * An axis-aligned bounding box in SVG canvas coordinates, used to detect overlap between
 * rendered edge-verb labels and between a label and a node's `<rect>`. [FunctionModelSvgRenderer]'s
 * `placeEdgeLabels` greedily searches candidate label positions and picks the one with the least
 * total [overlapAreaWith] against every box already placed.
 */
internal data class LabelBox(
    val minX: Float,
    val minY: Float,
    val maxX: Float,
    val maxY: Float,
) {
    /** This box grown by [padPx] on every side -- used to keep a small visual gap between labels/nodes, not just avoid literal pixel touching. */
    fun inflate(padPx: Float): LabelBox = LabelBox(minX - padPx, minY - padPx, maxX + padPx, maxY + padPx)

    /** True if `this` and [other] share any area (touching edges alone do not count as overlap). */
    fun overlaps(other: LabelBox): Boolean =
        minX < other.maxX && maxX > other.minX && minY < other.maxY && maxY > other.minY

    /** Area of the intersection rectangle between `this` and [other], `0f` if they don't overlap. */
    fun overlapAreaWith(other: LabelBox): Float {
        val ox = (min(maxX, other.maxX) - max(minX, other.minX)).coerceAtLeast(0f)
        val oy = (min(maxY, other.maxY) - max(minY, other.minY)).coerceAtLeast(0f)
        return ox * oy
    }
}

/**
 * The true bounding box of [text] rendered at [EDGE_LABEL_FONT_SIZE_PX] with `text-anchor="middle"`
 * at ([centerX], [baselineY]) -- uses [Font.getStringBounds]'s real ascent/descent instead of the
 * old flat [EDGE_LABEL_FONT_SIZE_PX] approximation this module used to apply for canvas sizing.
 * [Font.getStringBounds]'s `bounds.y` is negative (the ascent, measured upward from the baseline);
 * `bounds.height` is ascent + descent.
 */
internal fun edgeLabelBoxAt(
    text: String,
    centerX: Float,
    baselineY: Float,
): LabelBox {
    val capped = if (text.length > MAX_MEASURED_CHARS) text.take(MAX_MEASURED_CHARS) else text
    val bounds = edgeLabelMeasureFont.getStringBounds(capped, frc)
    val halfWidth = bounds.width.toFloat() / 2f
    val top = baselineY + bounds.y.toFloat()
    return LabelBox(
        minX = centerX - halfWidth,
        maxX = centerX + halfWidth,
        minY = top,
        maxY = top + bounds.height.toFloat(),
    )
}

/** This node/canvas [Rect] as a [LabelBox], for overlap checks against edge-verb labels. */
internal fun Rect.toLabelBox(): LabelBox = LabelBox(origin.x, origin.y, origin.x + size.width, origin.y + size.height)
