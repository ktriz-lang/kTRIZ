package dev.ktriz.render.kuml

import dev.ktriz.sufield.SuField
import dev.kuml.layout.Point

/**
 * Renders this [SuField] as a complete, standalone SVG document string: the classical
 * S1-S2-Field triangle, F at the apex, S1 bottom-left, S2 bottom-right, action reading
 * right-to-left (S2 acts on S1).
 *
 * ## Why this renderer never calls kUML's layout engine
 *
 * Unlike [dev.ktriz.function.FunctionModel.renderSvg] (`FunctionModelSvgRenderer.kt`), this
 * renderer never invokes [dev.kuml.layout.elk.ElkLayoutEngine], or any kUML layout API at all
 * -- it uses only kUML's plain geometry *types* ([Point], [dev.kuml.layout.Rect],
 * [dev.kuml.layout.Size]), which every helper in `EdgeGeometry.kt` (`wavyPathPoints`,
 * `offsetPerpendicular`, `arrowheadPoints`, `toSvgPathData`, `toSvgPolygonPoints`) already
 * speaks, so this module stays free to reuse them verbatim.
 *
 * A layout engine's value is that it *decides* something the caller cannot. Here there is
 * nothing to decide: F sits at the apex, S1 bottom-left, S2 bottom-right, because the classical
 * Su-Field notation says so -- confirmed against two independent primary sources (MATRIZ's own
 * wiki, and the worked figures in *Systematic Innovation*, "4 Su-Field Analysis and Standard
 * Solutions", citing *A Thread in the Labyrinth*, Petrozavodsk: Karelia, 1988,
 * ISBN 5-7545-0020-3). An engine that ever placed them differently would be *wrong*, so fixed,
 * deterministic geometry is a correctness requirement here, not merely an optimization -- and
 * it lets this module's tests assert exact coordinates instead of tolerance ranges. Running ELK
 * (with its EMF/Guava dependency weight, nondeterminism, and per-call cost) over a rigid
 * three-node figure would be unjustified cost for zero benefit.
 *
 * Placement lives in exactly one, easily replaced function -- [vertexPlacement] in
 * `SuFieldGeometry.kt` -- returning a coordinate map keyed by [SuFieldVertex]. If a future wave
 * adds a fourth vertex (e.g. standard solution 1.1.2 introducing an S3), that one function is
 * what changes; everything downstream of it (trimming, styling, bounding, serialization) is
 * agnostic to how many vertices exist.
 *
 * Two-phase construction, not `<g transform="translate(...)">`: [buildScene] lays the triangle
 * out in a local frame centred on the apex/base midline (S1/S2 can be negative-x), [bounds]
 * measures it, and [SuFieldScene.translated] shifts every coordinate into non-negative,
 * viewBox-relative space *before* [SuFieldScene.toSvgBody] serializes it. Every coordinate that
 * ends up in the emitted markup is therefore already absolute -- a test can read `cx`/`d`/
 * `points` directly and assert "no point outside the viewBox" without reconstructing a
 * transform, mirroring the same design choice `FunctionModelSvgRenderer.kt` makes for a
 * different reason (there, ELK's own canvas can need widening on any side).
 *
 * [dev.ktriz.function.Component.name] and [dev.ktriz.sufield.FieldType.labelEn] are
 * XML-escaped (see `SvgEscape.kt`) exactly as component names and edge verbs are in
 * `FunctionModelSvgRenderer.kt` -- this module never trusts either string to already be safe
 * SVG content. A label too wide for [MAX_LABEL_WIDTH_PX] is truncated with an ellipsis and
 * carries the full name in a `<title>` tooltip, via the same [displayTextFor]/[titleTextFor]
 * machinery `FunctionModelSvgRenderer.kt` uses for node names.
 *
 * The base (S2's judged effect on S1) is drawn iff [dev.ktriz.sufield.SuField.quality] maps to
 * a non-null [EdgeStrokeStyle] via [dev.ktriz.render.kuml.strokeStyle] on
 * [dev.ktriz.sufield.SuFieldQuality] -- reusing [EdgeStrokeStyle] itself (`EdgeStroke.kt`)
 * rather than inventing a second stroke vocabulary, per MATRIZ: "Symbols of interactions used
 * in Su-Field models are similar to those used in function modeling for devices." A leg
 * (F-S1, F-S2) is always a single plain solid line -- it exists whenever its two endpoints
 * exist, carries no quality judgement of its own, and never gets an arrowhead.
 */
public fun SuField.renderSvg(): String {
    val scene = buildScene()
    val bounds = scene.bounds()
    val positioned = scene.translated(bounds.offsetX, bounds.offsetY)
    return svgDocument(width = bounds.width, height = bounds.height, body = positioned.toSvgBody())
}

private data class VertexCircle(
    val center: Point,
    val symbol: String,
    val subscript: String?,
)

private data class OutsideLabel(
    val x: Float,
    val y: Float,
    // "start" | "middle" | "end"
    val anchor: String,
    val display: DisplayText,
    val fullText: String,
    val fontSizePx: Float,
    val widthPx: Float,
)

private data class StyledPath(
    val points: List<Point>,
    val dashed: Boolean,
    val cssClass: String,
)

private data class SuFieldScene(
    val circles: List<VertexCircle>,
    val paths: List<StyledPath>,
    // null unless a base action is drawn
    val arrow: List<Point>?,
    val labels: List<OutsideLabel>,
)

/**
 * Builds the scene in the local frame [vertexPlacement] returns -- S1 at
 * `(-BASE_SPAN_PX / 2, 0)`, S2 (if present) at `(+BASE_SPAN_PX / 2, 0)`, F (if present) at
 * `(0, -APEX_RISE_PX)`. Every element is derived structurally from which of `s2`/`field` are
 * non-null (via a local smart-cast `val`, never `!!`) rather than re-deriving
 * [SuField]'s own `INCOMPLETE ⟺ (s2 == null || field == null)` invariant, which
 * `SuField.init` already enforces.
 */
private fun SuField.buildScene(): SuFieldScene {
    val placement = vertexPlacement()
    val s1Point = placement.getValue(SuFieldVertex.S1)
    val s2 = this.s2
    val field = this.field

    val circles = mutableListOf(VertexCircle(center = s1Point, symbol = "S1", subscript = null))
    val paths = mutableListOf<StyledPath>()
    val labels = mutableListOf(sideLabel(s1Point, s1.name, anchor = "end"))

    if (s2 != null) {
        val s2Point = placement.getValue(SuFieldVertex.S2)
        circles += VertexCircle(center = s2Point, symbol = "S2", subscript = null)
        labels += sideLabel(s2Point, s2.name, anchor = "start")
    }
    if (field != null) {
        val fieldPoint = placement.getValue(SuFieldVertex.FIELD)
        circles += VertexCircle(center = fieldPoint, symbol = "F", subscript = field.abbreviation)
        labels += topLabel(fieldPoint, field.labelEn)
        paths += StyledPath(points = trimmedSegment(fieldPoint, s1Point), dashed = false, cssClass = "su-field-leg")
        if (s2 != null) {
            val s2Point = placement.getValue(SuFieldVertex.S2)
            paths += StyledPath(points = trimmedSegment(fieldPoint, s2Point), dashed = false, cssClass = "su-field-leg")
        }
    }

    // strokeStyle() is non-null only for a quality other than INCOMPLETE, which (by SuField's
    // own constructor invariant) guarantees both s2 and field are set -- so both getValue()
    // lookups below are safe without re-checking nullability here.
    var arrow: List<Point>? = null
    quality.strokeStyle()?.let { style ->
        val s2Point = placement.getValue(SuFieldVertex.S2)
        val base = trimmedSegment(s2Point, s1Point)
        val actionPaths: List<List<Point>> =
            when (style) {
                EdgeStrokeStyle.SOLID, EdgeStrokeStyle.DASHED -> listOf(base)
                EdgeStrokeStyle.DOUBLED ->
                    listOf(
                        base.offsetPerpendicular(DOUBLE_STROKE_GAP_PX / 2f),
                        base.offsetPerpendicular(-DOUBLE_STROKE_GAP_PX / 2f),
                    )
                EdgeStrokeStyle.WAVY -> listOf(wavyPathPoints(base))
            }
        actionPaths.forEach { pts ->
            paths += StyledPath(points = pts, dashed = style == EdgeStrokeStyle.DASHED, cssClass = "su-field-action")
        }
        // Always from the unperturbed base, never from a doubled/wavy drawn stroke -- exactly
        // one arrowhead regardless of how many <path> elements the style draws. See
        // FunctionModelSvgRenderer.kt's renderEdge for the same rule.
        arrow = arrowheadPoints(base)
    }

    return SuFieldScene(circles = circles, paths = paths, arrow = arrow, labels = labels)
}

/** S1/S2's outside label: horizontal, anchored away from the triangle, vertically centred on the circle. */
private fun sideLabel(
    circleCenter: Point,
    name: String,
    anchor: String,
): OutsideLabel {
    val sign = if (anchor == "end") -1f else 1f
    val x = circleCenter.x + sign * (VERTEX_RADIUS_PX + LABEL_GAP_PX)
    val display = displayTextFor(name, MAX_LABEL_WIDTH_PX, ::measuredTextWidthPx)
    val widthPx = measuredTextWidthPx(display.text)
    return OutsideLabel(
        x = x,
        y = circleCenter.y,
        anchor = anchor,
        display = display,
        fullText = name,
        fontSizePx = NODE_FONT_SIZE_PX,
        widthPx = widthPx,
    )
}

/** The field name's outside label: centred above the apex circle. */
private fun topLabel(
    apex: Point,
    name: String,
): OutsideLabel {
    val display = displayTextFor(name, MAX_LABEL_WIDTH_PX, ::measuredEdgeLabelWidthPx)
    val widthPx = measuredEdgeLabelWidthPx(display.text)
    return OutsideLabel(
        x = apex.x,
        y = apex.y - VERTEX_RADIUS_PX - LABEL_GAP_PX,
        anchor = "middle",
        display = display,
        fullText = name,
        fontSizePx = EDGE_LABEL_FONT_SIZE_PX,
        widthPx = widthPx,
    )
}

/**
 * The canvas bounds needed to fit every circle, path point, arrow point, and outside label
 * without clipping -- [dev.ktriz.render.kuml.CANVAS_MARGIN_PX] padded on all four sides. A
 * side label's (`anchor` `"end"`/`"start"`) vertical extent never exceeds the circle it sits
 * beside (font size well under `2 * VERTEX_RADIUS_PX`, `dominant-baseline="middle"`), so only
 * its horizontal extent is tracked; the field's top label (`anchor` `"middle"`) is the one that
 * can extend above every circle, so both its horizontal extent and its vertical extent above
 * its own baseline are tracked -- the exact clipped-label bug class documented in
 * `FunctionModelSvgRenderer.kt`'s `canvasBoundsFor` KDoc.
 */
private fun SuFieldScene.bounds(): CanvasBounds {
    val xs = mutableListOf<Float>()
    val ys = mutableListOf<Float>()

    circles.forEach { c ->
        xs += c.center.x - VERTEX_RADIUS_PX
        xs += c.center.x + VERTEX_RADIUS_PX
        ys += c.center.y - VERTEX_RADIUS_PX
        ys += c.center.y + VERTEX_RADIUS_PX
    }
    paths.forEach { p ->
        p.points.forEach { pt ->
            xs += pt.x
            ys += pt.y
        }
    }
    arrow?.forEach { pt ->
        xs += pt.x
        ys += pt.y
    }
    labels.forEach { l ->
        when (l.anchor) {
            "end" -> xs += listOf(l.x - l.widthPx, l.x)
            "start" -> xs += listOf(l.x, l.x + l.widthPx)
            else -> {
                xs += listOf(l.x - l.widthPx / 2f, l.x + l.widthPx / 2f)
                // SVG text y is a baseline, not a box top -- pad upward by the font size for
                // the glyphs' ascent above the baseline (same convention as
                // FunctionModelSvgRenderer.kt's canvasBoundsFor for edge verb labels).
                ys += listOf(l.y - l.fontSizePx, l.y)
            }
        }
    }

    val minX = (xs.minOrNull() ?: 0f) - CANVAS_MARGIN_PX
    val minY = (ys.minOrNull() ?: 0f) - CANVAS_MARGIN_PX
    val maxX = (xs.maxOrNull() ?: 0f) + CANVAS_MARGIN_PX
    val maxY = (ys.maxOrNull() ?: 0f) + CANVAS_MARGIN_PX
    val offsetX = -minX
    val offsetY = -minY
    return CanvasBounds(width = maxX + offsetX, height = maxY + offsetY, offsetX = offsetX, offsetY = offsetY)
}

private fun SuFieldScene.translated(
    dx: Float,
    dy: Float,
): SuFieldScene =
    SuFieldScene(
        circles = circles.map { it.copy(center = Point(x = it.center.x + dx, y = it.center.y + dy)) },
        paths = paths.map { p -> p.copy(points = p.points.map { pt -> Point(x = pt.x + dx, y = pt.y + dy) }) },
        arrow = arrow?.map { Point(x = it.x + dx, y = it.y + dy) },
        labels = labels.map { it.copy(x = it.x + dx, y = it.y + dy) },
    )

private fun SuFieldScene.toSvgBody(): String =
    buildString {
        paths.forEach { p ->
            val d = xmlEscapeAttr(p.points.toSvgPathData())
            val dashAttr = if (p.dashed) """ stroke-dasharray="$DASH_ARRAY"""" else ""
            append(
                """<path class="${p.cssClass}" d="$d" fill="none" stroke="$EDGE_STROKE_COLOR" """ +
                    """stroke-width="$STROKE_WIDTH_PX"$dashAttr/>""",
            )
        }
        arrow?.let { pts ->
            append(
                """<polygon class="su-field-arrow" points="${pts.toSvgPolygonPoints()}" """ +
                    """fill="$EDGE_STROKE_COLOR"/>""",
            )
        }
        circles.forEach { c -> append(c.toSvgTag()) }
        labels.forEach { l -> append(l.toSvgTag()) }
    }

private fun VertexCircle.toSvgTag(): String {
    val circleTag =
        """<circle class="su-field-vertex" cx="${center.x}" cy="${center.y}" r="$VERTEX_RADIUS_PX" """ +
            """fill="$NODE_FILL_COLOR" stroke="$NODE_STROKE_COLOR" stroke-width="$STROKE_WIDTH_PX"/>"""
    val symbolBody =
        if (subscript != null) {
            "${xmlEscapeText(symbol)}<tspan font-size=\"$VERTEX_SUBSCRIPT_FONT_SIZE_PX\" " +
                "dy=\"$VERTEX_SUBSCRIPT_DY_PX\">${xmlEscapeText(subscript)}</tspan>"
        } else {
            xmlEscapeText(symbol)
        }
    val symbolTag =
        """<text class="su-field-symbol" x="${center.x}" y="${center.y}" text-anchor="middle" """ +
            """dominant-baseline="middle" font-family="$NODE_FONT_FAMILY" """ +
            """font-size="$VERTEX_SYMBOL_FONT_SIZE_PX">$symbolBody</text>"""
    return circleTag + symbolTag
}

private fun OutsideLabel.toSvgTag(): String {
    val titleTag = if (display.truncated) "<title>${xmlEscapeText(titleTextFor(fullText))}</title>" else ""
    // Horizontal side labels ("end"/"start") are vertically centred on their circle; the
    // field's top label ("middle") sits on its own baseline above the apex, matching every
    // other baseline-positioned text this module emits (node names, edge verbs).
    val baselineAttr = if (anchor == "middle") "" else """ dominant-baseline="middle""""
    return """<text class="su-field-label" x="$x" y="$y" text-anchor="$anchor"$baselineAttr """ +
        """font-family="$NODE_FONT_FAMILY" font-size="$fontSizePx">$titleTag${xmlEscapeText(display.text)}</text>"""
}
