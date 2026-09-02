package dev.ktriz.render.kuml

/**
 * The shared visual palette and document scaffold for every renderer in this module.
 *
 * Extracted from `FunctionModelSvgRenderer.kt` when `SuFieldSvgRenderer.kt` was added, so the
 * colour palette and the `<svg>` document wrapper exist exactly once rather than being copied a
 * second time -- both renderers draw the same node/edge visual language (black stroke, white
 * fill, `#333333` edge strokes), just over a different scene shape.
 */
internal const val STROKE_WIDTH_PX = 2f
internal const val NODE_STROKE_COLOR = "#000000"
internal const val NODE_FILL_COLOR = "#ffffff"
internal const val EDGE_STROKE_COLOR = "#333333"

/** Canvas size plus the translation applied to every emitted coordinate. */
internal data class CanvasBounds(
    val width: Float,
    val height: Float,
    val offsetX: Float,
    val offsetY: Float,
)

/** Wraps [body] (already-serialized SVG markup) in a complete, standalone SVG document string. */
internal fun svgDocument(
    width: Float,
    height: Float,
    body: String,
): String =
    """<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $width $height" width="$width" height="$height">
$body
</svg>
"""
