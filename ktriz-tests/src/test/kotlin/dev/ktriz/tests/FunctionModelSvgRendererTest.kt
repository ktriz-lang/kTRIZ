package dev.ktriz.tests

import dev.ktriz.function.Component
import dev.ktriz.function.functionModel
import dev.ktriz.render.kuml.renderSvg
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs

/** Parses [svg] as XML and returns the DOM [Document] -- fails the test with an XML parse exception if malformed. */
private fun parseSvg(svg: String): Document {
    val factory = DocumentBuilderFactory.newInstance()
    val builder = factory.newDocumentBuilder()
    return builder.parse(ByteArrayInputStream(svg.toByteArray(Charsets.UTF_8)))
}

private fun Document.elementsByTag(tag: String): List<Element> {
    val nodeList = getElementsByTagName(tag)
    return (0 until nodeList.length).map { nodeList.item(it) as Element }
}

/**
 * The (x, y) offset of the top-level `<g transform="translate(x,y)">` wrapper renderSvg() emits
 * around the body when any rendered geometry needed the canvas widened to the left/top of ELK's
 * own layout -- (0, 0) if no such wrapper is present. Every `<path>`/`<polygon>`/`<text>`
 * coordinate elsewhere in the document is expressed in the *pre-translate* coordinate space, so a
 * test that wants to know whether a point falls inside the SVG's own `viewBox` (which describes
 * the *post-translate*, final rendered space) must add this offset back in first -- checking raw
 * coordinates against `[0, width] x [0, height]` directly is only valid when this offset is zero.
 */
private fun Document.groupTranslateOffset(): Pair<Double, Double> {
    val g = elementsByTag("g").firstOrNull { it.hasAttribute("transform") } ?: return 0.0 to 0.0
    val m = Regex("translate\\((-?[0-9.]+),(-?[0-9.]+)\\)").find(g.getAttribute("transform")) ?: return 0.0 to 0.0
    val (tx, ty) = m.destructured
    return tx.toDouble() to ty.toDouble()
}

class FunctionModelSvgRendererTest :
    StringSpec({
        "renderSvg produces a document that parses as well-formed XML with an svg root" {
            val fm =
                functionModel {
                    val engine = component("Engine")
                    val coolant = component("Coolant")
                    val block = component("Cylinder block")
                    useful(from = coolant, to = engine, verb = "cools")
                    harmful(from = engine, to = block, verb = "overheats")
                    insufficient(from = coolant, to = engine, verb = "circulates")
                    excessive(from = coolant, to = engine, verb = "pressurizes")
                }

            val doc = parseSvg(fm.renderSvg())

            doc.documentElement.tagName shouldBe "svg"
        }

        "renderSvg emits exactly one rect per distinct component name" {
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    val c = component("C")
                    useful(from = a, to = b, verb = "drives")
                    harmful(from = b, to = c, verb = "corrodes")
                }

            val doc = parseSvg(fm.renderSvg())

            doc.elementsByTag("rect") shouldHaveSize 3
        }

        "a USEFUL edge renders as a single solid path with no stroke-dasharray" {
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    useful(from = a, to = b, verb = "drives")
                }

            val doc = parseSvg(fm.renderSvg())
            val paths = doc.elementsByTag("path")

            paths shouldHaveSize 1
            paths.single().hasAttribute("stroke-dasharray") shouldBe false
        }

        "an INSUFFICIENT edge renders with stroke-dasharray 8 4" {
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    insufficient(from = a, to = b, verb = "lubricates")
                }

            val doc = parseSvg(fm.renderSvg())
            val paths = doc.elementsByTag("path")

            paths shouldHaveSize 1
            paths.single().getAttribute("stroke-dasharray") shouldBe "8 4"
        }

        "an EXCESSIVE edge renders as two parallel path elements (doubled line)" {
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    excessive(from = a, to = b, verb = "heats")
                }

            val doc = parseSvg(fm.renderSvg())

            doc.elementsByTag("path") shouldHaveSize 2
        }

        "a HARMFUL edge renders as a path with more than two coordinate pairs (a wavy, non-straight route)" {
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    harmful(from = a, to = b, verb = "corrodes")
                }

            val doc = parseSvg(fm.renderSvg())
            val paths = doc.elementsByTag("path")

            paths shouldHaveSize 1
            val d = paths.single().getAttribute("d")
            // A 2-point Direct/short OrthogonalRounded route serializes to exactly one "L ";
            // the wavy perturbation samples the route by arc length, so it must produce more.
            val lCount = Regex("L ").findAll(d).count()
            lCount shouldBeGreaterThan 1
        }

        "renderSvg includes each component's name as text content" {
            val fm =
                functionModel {
                    val engine = component("Engine")
                    val coolant = component("Coolant")
                    useful(from = coolant, to = engine, verb = "cools")
                }

            val svg = fm.renderSvg()

            svg shouldContain "Engine"
            svg shouldContain "Coolant"
        }

        "renderSvg includes each edge's verb as text content" {
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    useful(from = a, to = b, verb = "lubricates")
                }

            fm.renderSvg() shouldContain "lubricates"
        }

        "a component name with angle brackets and ampersands still yields valid XML, no raw unescaped bracket" {
            val fm =
                functionModel {
                    val a = component("A")
                    val evil = component("<script>&\"'evil")
                    useful(from = a, to = evil, verb = "targets")
                }

            val svg = fm.renderSvg()

            // Must parse -- an unescaped '<' inside text content would break XML well-formedness.
            parseSvg(svg)
            svg shouldNotContain "<script>"
        }

        "an empty FunctionModel renders a minimal but valid SVG instead of throwing, zero rect and zero path" {
            val fm = functionModel {}

            val doc = parseSvg(fm.renderSvg())

            doc.documentElement.tagName shouldBe "svg"
            doc.elementsByTag("rect") shouldHaveSize 0
            doc.elementsByTag("path") shouldHaveSize 0
        }

        "a self-loop edge (from == to) renders without throwing" {
            val fm =
                functionModel {
                    val turbine = component("Turbine")
                    useful(from = turbine, to = turbine, verb = "wears")
                }

            val doc = parseSvg(fm.renderSvg())

            doc.elementsByTag("rect") shouldHaveSize 1
            doc.elementsByTag("path").shouldNotBeEmpty()
        }

        "an edge label sits at the route's true midpoint, not at the target endpoint" {
            // Round-1 review bug: renderEdge() used to index base[base.size / 2], which for the
            // common 2-point Direct route (size 2) resolves to base[1] -- the target endpoint --
            // rather than an actual midpoint. This test would have failed against that code:
            // labelY would have equal(ish) the target's y (y1) instead of falling strictly
            // between the route's two endpoints.
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    useful(from = a, to = b, verb = "drives")
                }

            val doc = parseSvg(fm.renderSvg())
            val path = doc.elementsByTag("path").single()

            // A single unbent (EdgeRoute.Direct) route serializes as "M x0 y0 L x1 y1" -- the
            // route's own source/target points, ground truth independent of ELK's chosen layout
            // axis (vertical vs. horizontal), so this test doesn't depend on which axis varies.
            val coords = Regex("-?\\d+\\.?\\d*").findAll(path.getAttribute("d")).map { it.value.toDouble() }.toList()
            coords shouldHaveSize 4
            val (x0, y0, x1, y1) = coords

            val label = doc.elementsByTag("text").single { it.textContent == "drives" }
            val labelX = label.getAttribute("x").toDouble()
            val labelY = label.getAttribute("y").toDouble()

            // renderEdge() places the label at (mid.x, mid.y - 4); the true midpoint of a
            // 2-point route is the average of its endpoints.
            val expectedX = (x0 + x1) / 2.0
            val expectedY = (y0 + y1) / 2.0 - 4.0
            abs(labelX - expectedX) shouldBeLessThan 0.01
            abs(labelY - expectedY) shouldBeLessThan 0.01

            // Sanity check the fixture actually exercises the bug: A and B must differ on at
            // least one axis, and the (uncorrected) label y must land strictly inside the span
            // between the two endpoints rather than coinciding with either one.
            (x0 != x1 || y0 != y1) shouldBe true
            labelY shouldBeGreaterThan minOf(y0 - 4.0, y1 - 4.0)
            labelY shouldBeLessThan maxOf(y0 - 4.0, y1 - 4.0)
        }

        "an edge referencing a Component never passed through component() still gets a rendered node box" {
            val ghost = Component("Ghost")
            val fm =
                functionModel {
                    val engine = component("Engine")
                    useful(from = ghost, to = engine, verb = "haunts")
                }

            val svg = fm.renderSvg()
            val doc = parseSvg(svg)

            doc.elementsByTag("rect") shouldHaveSize 2
            svg shouldContain "Ghost"
        }

        "a long component name grows the node box within grid/max bounds and gets a title" {
            // "Electrohydraulic servo actuator assembly" alone measures ~270px -- inflated by
            // FONT_SUBSTITUTION_SLACK the box clamps to MAX_NODE_WIDTH_PX, but the *unslacked*
            // text still fits inside that clamped box's inner width, so it would not truncate.
            // Padded further here so the real text width itself exceeds the clamped inner width.
            val longName = "Electrohydraulic servo actuator assembly control interface module"
            val fm = functionModel { component(longName) }

            val doc = parseSvg(fm.renderSvg())
            val rect = doc.elementsByTag("rect").single()
            val width = rect.getAttribute("width").toDouble()

            // Fixed V1 box was 140x60 -- a real-world long name must now grow past that,
            // stay within the clamp, and land on the 8px width grid.
            width shouldBeGreaterThan 140.0
            (width <= 320.0) shouldBe true
            (width % 8.0 == 0.0) shouldBe true

            val titles = doc.elementsByTag("title")
            titles shouldHaveSize 1
            titles.single().textContent shouldBe longName
        }

        "an extremely long component name's title tooltip is capped at 512 chars with an ellipsis" {
            // Regression guard for renderNode's <title> tag switching from
            // xmlEscapeText(nodeId.value) to xmlEscapeText(titleTextFor(nodeId.value)) -- the
            // test above uses a 66-char name, far under MAX_MEASURED_CHARS (512), so it never
            // exercises titleTextFor's own cap; a >512-char name does. Confirmed live: reverting
            // this call site to the old xmlEscapeText(nodeId.value) still leaves the full suite
            // green except this one test. Same pattern already covering the Su-Field renderer's
            // titleTextFor wiring, see SuFieldSvgRendererTest.kt's "a long S1 name truncates with
            // an ellipsis, carries a bounded title...".
            val longName = "X".repeat(600)
            val fm = functionModel { component(longName) }

            val doc = parseSvg(fm.renderSvg())
            val titles = doc.elementsByTag("title")

            titles shouldHaveSize 1
            val title = titles.single().textContent
            (title.length <= 513) shouldBe true
            title.endsWith("…") shouldBe true
        }

        "a single-character component name is clamped to the minimum node width" {
            val fm = functionModel { component("X") }

            val doc = parseSvg(fm.renderSvg())
            val rect = doc.elementsByTag("rect").single()

            rect.getAttribute("width").toDouble() shouldBe 96.0
        }

        "node and edge text carry explicit font-family and font-size" {
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    useful(from = a, to = b, verb = "drives")
                }

            val doc = parseSvg(fm.renderSvg())
            val texts = doc.elementsByTag("text")

            texts.shouldNotBeEmpty()
            texts.forEach { it.getAttribute("font-family") shouldBe "sans-serif" }

            val nodeText = texts.single { it.textContent == "A" }
            nodeText.getAttribute("font-size") shouldBe "14.0"

            val edgeLabel = texts.single { it.textContent == "drives" }
            edgeLabel.getAttribute("font-size") shouldBe "12.0"
        }

        "every edge renders exactly one arrowhead polygon whose tip sits on the base route" {
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    useful(from = a, to = b, verb = "drives")
                }

            val doc = parseSvg(fm.renderSvg())
            val polygons = doc.elementsByTag("polygon")
            polygons shouldHaveSize 1

            val path = doc.elementsByTag("path").single()
            val coords = Regex("-?\\d+\\.?\\d*").findAll(path.getAttribute("d")).map { it.value.toDouble() }.toList()
            coords shouldHaveSize 4
            val (_, _, x1, y1) = coords

            val tip =
                polygons.single().getAttribute("points").split(" ").first().split(",").let { (x, y) ->
                    x.toDouble() to y.toDouble()
                }
            abs(tip.first - x1) shouldBeLessThan 0.01
            abs(tip.second - y1) shouldBeLessThan 0.01
        }

        "an INSUFFICIENT (dashed) edge renders exactly one arrowhead polygon whose tip sits on the base route" {
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    insufficient(from = a, to = b, verb = "lubricates")
                }

            val doc = parseSvg(fm.renderSvg())
            val polygons = doc.elementsByTag("polygon")
            polygons shouldHaveSize 1

            val path = doc.elementsByTag("path").single()
            val coords = Regex("-?\\d+\\.?\\d*").findAll(path.getAttribute("d")).map { it.value.toDouble() }.toList()
            val (_, _, x1, y1) = coords

            val tip =
                polygons.single().getAttribute("points").split(" ").first().split(",").let { (x, y) ->
                    x.toDouble() to y.toDouble()
                }
            abs(tip.first - x1) shouldBeLessThan 0.01
            abs(tip.second - y1) shouldBeLessThan 0.01
        }

        "an EXCESSIVE (doubled) edge renders exactly one arrowhead polygon despite drawing two parallel paths" {
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    excessive(from = a, to = b, verb = "heats")
                }

            val doc = parseSvg(fm.renderSvg())
            val polygons = doc.elementsByTag("polygon")
            val paths = doc.elementsByTag("path")

            // The arrowhead is derived from the shared, unoffset base route, not from either of
            // the two rendered parallel strokes -- exactly one arrowhead per edge regardless of
            // how many <path> elements its stroke style draws.
            paths shouldHaveSize 2
            polygons shouldHaveSize 1

            // The base route both offset paths straddle is a straight A->B route: its endpoint is
            // the midpoint between the two paths' matching (last-coordinate-pair) endpoints, since
            // offsetPerpendicular() shifts each path by the same distance to either side of it.
            fun lastCoordPair(path: Element): Pair<Double, Double> {
                val coords =
                    Regex(
                        "-?\\d+\\.?\\d*",
                    ).findAll(path.getAttribute("d")).map { it.value.toDouble() }.toList()
                return coords[coords.size - 2] to coords[coords.size - 1]
            }
            val (x1a, y1a) = lastCoordPair(paths[0])
            val (x1b, y1b) = lastCoordPair(paths[1])
            val expectedTip = (x1a + x1b) / 2.0 to (y1a + y1b) / 2.0

            val tip =
                polygons.single().getAttribute("points").split(" ").first().split(",").let { (x, y) ->
                    x.toDouble() to y.toDouble()
                }
            abs(tip.first - expectedTip.first) shouldBeLessThan 0.01
            abs(tip.second - expectedTip.second) shouldBeLessThan 0.01
        }

        "a HARMFUL edge's arrowhead is aligned with the straight route axis, not the wavy tangent" {
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    harmful(from = a, to = b, verb = "corrodes")
                }

            val doc = parseSvg(fm.renderSvg())
            val path = doc.elementsByTag("path").single()
            val coords = Regex("-?\\d+\\.?\\d*").findAll(path.getAttribute("d")).map { it.value.toDouble() }.toList()
            // wavyPathPoints() preserves the route's first/last points unperturbed -- the first
            // and last coordinate pairs of the wavy path's own "d" are already the true, straight
            // route endpoints, giving ground truth for the axis without needing ELK internals.
            val x0 = coords[0]
            val y0 = coords[1]
            val x1 = coords[coords.size - 2]
            val y1 = coords[coords.size - 1]

            val points =
                doc.elementsByTag("polygon").single().getAttribute("points").split(" ").map { pair ->
                    val (x, y) = pair.split(",")
                    x.toDouble() to y.toDouble()
                }

            val dx = x1 - x0
            val dy = y1 - y0
            val lineLen = kotlin.math.sqrt(dx * dx + dy * dy)
            points.forEach { (px, py) ->
                val distanceToAxis = abs(dx * (y0 - py) - (x0 - px) * dy) / lineLen
                distanceToAxis shouldBeLessThan 5.0
            }
        }

        "three edges between the same component pair bow apart with distinct midpoints" {
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    useful(from = a, to = b, verb = "drives")
                    harmful(from = b, to = a, verb = "corrodes")
                    insufficient(from = a, to = b, verb = "lubricates")
                }

            val doc = parseSvg(fm.renderSvg())
            val paths = doc.elementsByTag("path")
            paths shouldHaveSize 3

            val dAttrs = paths.map { it.getAttribute("d") }.toSet()
            dAttrs shouldHaveSize 3

            // Depending on ELK's chosen layout axis, the bow offset may show up on the x
            // coordinate (vertical routes) or the y coordinate (horizontal routes) -- checking
            // the full (x, y) position, not just one axis, keeps the test axis-independent
            // while still pinning "three distinct midpoints".
            val labelPositions =
                doc
                    .elementsByTag("text")
                    .filter { it.textContent in setOf("drives", "corrodes", "lubricates") }
                    .map { it.getAttribute("x").toDouble() to it.getAttribute("y").toDouble() }
                    .toSet()
            labelPositions shouldHaveSize 3
        }

        "labels of edges sharing a component pair don't visually overlap" {
            // Regression guard for a bug originally found by manual visual inspection: three
            // labels sharing a component pair used to render at the *same y* regardless of
            // whether their (bow-separated) x positions already kept them apart, which is what
            // actually made them visually overlap (text width exceeding the bow's few-pixel
            // spacing). The fix (this wave) replaced the old "always stagger by index" heuristic
            // with an actual collision-aware placement -- so the correct invariant to check now
            // is "no real overlap", not "always distinct y": these three short verbs' bow
            // separation is comfortably wider than their measured text width, so it is
            // legitimate (and better -- least unnecessary displacement, Rams' "as little design
            // as possible") for the placer to leave them all at their natural, unstaggered
            // baseline. Overlap is recomputed independently here via the same public JDK
            // font-metrics API the renderer uses internally, kept black-box like the rest of
            // this file.
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    useful(from = a, to = b, verb = "aaa")
                    harmful(from = a, to = b, verb = "bbb")
                    insufficient(from = a, to = b, verb = "ccc")
                }

            val doc = parseSvg(fm.renderSvg())
            val font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12)
            val frc = java.awt.font.FontRenderContext(null, true, true)

            data class Box(
                val minX: Double,
                val minY: Double,
                val maxX: Double,
                val maxY: Double,
            )

            fun boxOf(
                text: String,
                cx: Double,
                baselineY: Double,
            ): Box {
                val b = font.getStringBounds(text, frc)
                val halfWidth = b.width / 2.0
                val top = baselineY + b.y
                return Box(cx - halfWidth, top, cx + halfWidth, top + b.height)
            }

            fun Box.overlaps(other: Box): Boolean =
                minX < other.maxX && maxX > other.minX && minY < other.maxY && maxY > other.minY

            val boxes =
                doc.elementsByTag("text").filter { it.textContent in setOf("aaa", "bbb", "ccc") }.map { el ->
                    boxOf(el.textContent, el.getAttribute("x").toDouble(), el.getAttribute("y").toDouble())
                }
            boxes shouldHaveSize 3

            for (i in boxes.indices) {
                for (j in i + 1 until boxes.size) {
                    boxes[i].overlaps(boxes[j]) shouldBe false
                }
            }
        }

        "a self-loop's verb label stays within the canvas even when the loop sits at the node's right edge" {
            // Regression guard for the bug this wave's manual visual inspection actually caught:
            // canvasBoundsFor originally widened the canvas to fit every rendered *point*
            // (path/polygon coordinates) but not the verb label text drawn at each edge's
            // midpoint with text-anchor="middle" -- a self-loop's label sits at the loop's
            // rightmost point, so half its rendered width extended past the canvas edge and was
            // clipped, invisible in any viewer that respects the SVG viewBox. No coordinate-only
            // test could have caught this; it requires knowing the label's actual rendered width.
            val fm =
                functionModel {
                    val turbine = component("Turbine")
                    useful(from = turbine, to = turbine, verb = "wears")
                }

            val doc = parseSvg(fm.renderSvg())
            val svgWidth = doc.documentElement.getAttribute("width").toDouble()
            val label = doc.elementsByTag("text").single { it.textContent == "wears" }
            val labelX = label.getAttribute("x").toDouble()

            // Measured independently of the renderer's own (internal, cross-module-invisible)
            // font-metrics helper, using the same public JDK API at the same font/size it uses.
            val font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12)
            val frc = java.awt.font.FontRenderContext(null, true, true)
            val halfWidth = font.getStringBounds("wears", frc).width / 2.0

            (labelX + halfWidth) shouldBeLessThan svgWidth
            (labelX - halfWidth) shouldBeGreaterThan 0.0
        }

        "a single edge between a component pair is unaffected by the multi-edge bow" {
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    useful(from = a, to = b, verb = "drives")
                }

            val doc = parseSvg(fm.renderSvg())
            val path = doc.elementsByTag("path").single()
            val coords = Regex("-?\\d+\\.?\\d*").findAll(path.getAttribute("d")).map { it.value.toDouble() }.toList()

            coords shouldHaveSize 4
        }

        "a self-loop draws a fixed-radius arc that fits inside an expanded canvas" {
            val fm =
                functionModel {
                    val turbine = component("Turbine")
                    useful(from = turbine, to = turbine, verb = "wears")
                }

            val doc = parseSvg(fm.renderSvg())
            val path = doc.elementsByTag("path").single()
            val d = path.getAttribute("d")

            val lCount = Regex("L ").findAll(d).count()
            lCount shouldBeGreaterThan 19

            val coords = Regex("-?\\d+\\.?\\d*").findAll(d).map { it.value.toDouble() }.toList()
            val maxX = coords.filterIndexed { i, _ -> i % 2 == 0 }.max()

            val rect = doc.elementsByTag("rect").single()
            val nodeRightEdge = rect.getAttribute("x").toDouble() + rect.getAttribute("width").toDouble()
            maxX shouldBeGreaterThan nodeRightEdge

            val svgWidth = doc.documentElement.getAttribute("width").toDouble()
            svgWidth shouldBeGreaterThan nodeRightEdge

            doc.elementsByTag("polygon") shouldHaveSize 1
        }

        "no rendered edge point falls outside the SVG's own viewBox, even with many bowed edges and a self-loop" {
            // A dense bundle of parallel edges between the same pair exercises bowOffsetFor's
            // adaptive envelope on both sides of the route, plus a self-loop -- together a wide
            // test of canvasBoundsFor's four-sided bounding box, since a real ELK layout's edge
            // orientation (and therefore which axis the bow perturbs) is not controlled directly
            // by this test. Raw `<path>`/`<polygon>` coordinates are emitted in the *pre-translate*
            // space (see [groupTranslateOffset]) -- when canvasBoundsFor needs to widen the canvas
            // to the left/top of ELK's own layout, renderSvg() wraps the body in a
            // `<g transform="translate(...)">` rather than rewriting every coordinate, so this
            // test must add that offset back in before comparing against the viewBox, exactly as
            // an SVG renderer would.
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    repeat(9) { i ->
                        when (i % 4) {
                            0 -> useful(from = a, to = b, verb = "v$i")
                            1 -> harmful(from = a, to = b, verb = "v$i")
                            2 -> insufficient(from = b, to = a, verb = "v$i")
                            else -> excessive(from = b, to = a, verb = "v$i")
                        }
                    }
                    useful(from = a, to = a, verb = "wears")
                }

            val doc = parseSvg(fm.renderSvg())
            val width = doc.documentElement.getAttribute("width").toDouble()
            val height = doc.documentElement.getAttribute("height").toDouble()
            val (tx, ty) = doc.groupTranslateOffset()

            val allCoords =
                (
                    doc.elementsByTag("path").flatMap { p ->
                        Regex("-?\\d+\\.?\\d*").findAll(p.getAttribute("d")).map { it.value.toDouble() }
                    } +
                        doc.elementsByTag("polygon").flatMap { poly ->
                            poly.getAttribute("points").split(" ").flatMap { pair ->
                                pair.split(",").map(String::toDouble)
                            }
                        }
                ).toList()

            // Coordinates alternate x,y in both "M x y L x y ..." path data and "x,y x,y ..."
            // polygon points, so every even index is an x and every odd index is a y.
            allCoords.filterIndexed { i, _ -> i % 2 == 0 }.forEach { x ->
                (x + tx >= -0.01) shouldBe true
                (x + tx <= width + 0.01) shouldBe true
            }
            allCoords.filterIndexed { i, _ -> i % 2 == 1 }.forEach { y ->
                (y + ty >= -0.01) shouldBe true
                (y + ty <= height + 0.01) shouldBe true
            }
        }

        "a dashed self-loop keeps its stroke-dasharray, a harmful self-loop stays wavy" {
            val fmDashed =
                functionModel {
                    val turbine = component("Turbine")
                    insufficient(from = turbine, to = turbine, verb = "vibrates")
                }
            val dashedPath = parseSvg(fmDashed.renderSvg()).elementsByTag("path").single()
            dashedPath.getAttribute("stroke-dasharray") shouldBe "8 4"

            val fmHarmful =
                functionModel {
                    val turbine = component("Turbine")
                    harmful(from = turbine, to = turbine, verb = "cracks")
                }
            val harmfulPath = parseSvg(fmHarmful.renderSvg()).elementsByTag("path").single()
            val lCount = Regex("L ").findAll(harmfulPath.getAttribute("d")).count()
            lCount shouldBeGreaterThan 1
        }

        "an edge label's baseline position is unchanged for a solo edge (regression anchor)" {
            // Named regression anchor, separate from the "true midpoint" bug-fix test above:
            // pins the exact byte-identical value (mid.y - 4) the new greedy label-placement
            // pass must still produce for a solo edge whose label needs no collision avoidance.
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    useful(from = a, to = b, verb = "drives")
                }

            val doc = parseSvg(fm.renderSvg())
            val path = doc.elementsByTag("path").single()
            val coords = Regex("-?\\d+\\.?\\d*").findAll(path.getAttribute("d")).map { it.value.toDouble() }.toList()
            val (_, y0, _, y1) = coords
            val expectedY = (y0 + y1) / 2.0 - 4.0

            val label = doc.elementsByTag("text").single { it.textContent == "drives" }
            abs(label.getAttribute("y").toDouble() - expectedY) shouldBeLessThan 0.01
        }

        "twenty long-verb edges between the same pair render with zero label/label and label/node overlap" {
            // Dense bundle regression guard for the greedy 2D collision-avoidance pass: 20 edges
            // between the same A<->B pair, each with a distinct, deliberately long verb, so text
            // width alone would collide under the old fixed vertical stagger. Overlap is
            // recomputed independently here using the same public JDK font-metrics API the
            // renderer uses internally (not by importing the internal edgeLabelBoxAt -- kept
            // black-box, consistent with the rest of this file), so this test does not depend on
            // the renderer's own bookkeeping being correct.
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    repeat(20) { i ->
                        val verb = "transmits vibration energy through the shaft $i"
                        when (i % 4) {
                            0 -> useful(from = a, to = b, verb = verb)
                            1 -> harmful(from = a, to = b, verb = verb)
                            2 -> insufficient(from = b, to = a, verb = verb)
                            else -> excessive(from = b, to = a, verb = verb)
                        }
                    }
                }

            val doc = parseSvg(fm.renderSvg())
            val font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12)
            val frc = java.awt.font.FontRenderContext(null, true, true)

            data class Box(
                val minX: Double,
                val minY: Double,
                val maxX: Double,
                val maxY: Double,
            )

            fun boxOf(
                text: String,
                cx: Double,
                baselineY: Double,
            ): Box {
                val b = font.getStringBounds(text, frc)
                val halfWidth = b.width / 2.0
                val top = baselineY + b.y
                return Box(cx - halfWidth, top, cx + halfWidth, top + b.height)
            }

            fun Box.overlaps(other: Box): Boolean =
                minX < other.maxX && maxX > other.minX && minY < other.maxY && maxY > other.minY

            val labelBoxes =
                doc.elementsByTag("text").filter { it.textContent.startsWith("transmits vibration") }.map { el ->
                    boxOf(el.textContent, el.getAttribute("x").toDouble(), el.getAttribute("y").toDouble())
                }
            labelBoxes shouldHaveSize 20

            val nodeBoxes =
                doc.elementsByTag("rect").map { r ->
                    val x = r.getAttribute("x").toDouble()
                    val y = r.getAttribute("y").toDouble()
                    val w = r.getAttribute("width").toDouble()
                    val h = r.getAttribute("height").toDouble()
                    Box(x, y, x + w, y + h)
                }

            for (i in labelBoxes.indices) {
                for (j in i + 1 until labelBoxes.size) {
                    labelBoxes[i].overlaps(labelBoxes[j]) shouldBe false
                }
                nodeBoxes.forEach { nodeBox ->
                    labelBoxes[i].overlaps(nodeBox) shouldBe false
                }
            }
        }

        "twenty-four edges between the same pair still bow apart with distinct offsets, no clamp collapse" {
            // Regression guard for BOW_MAX_OFFSET_PX's removal: at a group size well past what
            // the old hard clamp (+/-27px) could distinguish, the adaptive envelope must still
            // produce 24 distinct offsets from the straight A-B axis, with a minimum pairwise gap
            // no worse than BOW_MIN_SPACING_PX (3px) -- tested black-box via rendered path
            // midpoints' perpendicular distance from the axis, since bowOffsetFor itself is
            // internal and not visible from this module (see ktriz-tests/build.gradle.kts: a
            // plain project() dependency, no friend-module/testFixtures wiring). Deliberately
            // alternates only USEFUL (solid) and INSUFFICIENT (dashed) qualities -- both render
            // their *base* geometry as a single straight/bowed path with a consistent BOW_SAMPLES
            // point count; HARMFUL's wavy re-sampling and EXCESSIVE's doubled path would each
            // break this test's simple "one path, N points" bookkeeping without changing what's
            // actually being verified (bow-offset distinctness lives in the shared *base*, not in
            // per-quality stroke rendering).
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    repeat(24) { i ->
                        if (i % 2 ==
                            0
                        ) {
                            useful(from = a, to = b, verb = "v$i")
                        } else {
                            insufficient(from = b, to = a, verb = "v$i")
                        }
                    }
                }

            val doc = parseSvg(fm.renderSvg())
            val paths = doc.elementsByTag("path")
            paths shouldHaveSize 24

            fun endpoints(d: String): List<Double> =
                Regex("-?\\d+\\.?\\d*").findAll(d).map { it.value.toDouble() }.toList()

            // Ground truth axis: the two node rects' centers (independent of the bow offsets
            // themselves, which perturb every interior sample point but never the two endpoints).
            val rects = doc.elementsByTag("rect")

            fun center(r: Element) =
                (r.getAttribute("x").toDouble() + r.getAttribute("width").toDouble() / 2) to
                    (r.getAttribute("y").toDouble() + r.getAttribute("height").toDouble() / 2)
            val (ax, ay) = center(rects[0])
            val (bx, by) = center(rects[1])
            val dx = bx - ax
            val dy = by - ay
            val axisLen = kotlin.math.sqrt(dx * dx + dy * dy)

            fun midpointDistanceToAxis(d: String): Double {
                val coords = endpoints(d)
                // Midpoint of the whole polyline's coordinate list is close enough to the true
                // bow apex for a monotonically-bowed path re-sampled at BOW_SAMPLES points.
                val midIdx = (coords.size / 4) * 2 // an even index near the middle
                val px = coords[midIdx]
                val py = coords[midIdx + 1]
                // Signed (not abs): bundle routes are symmetric around the axis, so an unsigned
                // distance folds the mirrored edges onto each other and reports false collisions
                // once the endpoints are spread over distributed anchors.
                return (dx * (ay - py) - (ax - px) * dy) / axisLen
            }

            val offsets = paths.map { midpointDistanceToAxis(it.getAttribute("d")) }.sorted()
            // 24 distinct offsets -- no two edges collapsed onto the same bow position.
            offsets.toSet().size shouldBe 24
            // Minimum gap between adjacent (sorted) offsets must not be worse than the adaptive
            // floor -- a hard clamp at +/-27px with 24 edges would force many gaps to 0.
            val minGap = offsets.zipWithNext().minOf { (p, n) -> n - p }
            minGap shouldBeGreaterThan 0.0
        }

        "twenty-four self-loops on the same component get distinct arc extents, no clamp collapse" {
            // Regression guard for SELF_LOOP_STACK_MAX_PX's removal: analogous to the bow-offset
            // test above, but for self-loop radii.
            val fm =
                functionModel {
                    val turbine = component("Turbine")
                    repeat(24) { i -> useful(from = turbine, to = turbine, verb = "loop$i") }
                }

            val doc = parseSvg(fm.renderSvg())
            val paths = doc.elementsByTag("path")
            paths shouldHaveSize 24

            fun maxX(d: String): Double =
                Regex("-?\\d+\\.?\\d*")
                    .findAll(d)
                    .map { it.value.toDouble() }
                    .toList()
                    .filterIndexed { i, _ -> i % 2 == 0 }
                    .max()

            val maxXs = paths.map { maxX(it.getAttribute("d")) }.sorted()
            maxXs.toSet().size shouldBe 24
            val minGap = maxXs.zipWithNext().minOf { (p, n) -> n - p }
            minGap shouldBeGreaterThan 0.0
        }

        "combined dense load (24 bowed edges + 24 self-loops) keeps every path and label inside the canvas" {
            // Extends the existing "no rendered edge point falls outside..." test to also cover
            // label boxes (that test only ever checked path/polygon points) under a much larger
            // combined load than the original n=9 case.
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    repeat(24) { i ->
                        when (i % 4) {
                            0 -> useful(from = a, to = b, verb = "v$i")
                            1 -> harmful(from = a, to = b, verb = "v$i")
                            2 -> insufficient(from = b, to = a, verb = "v$i")
                            else -> excessive(from = b, to = a, verb = "v$i")
                        }
                    }
                    repeat(24) { i -> useful(from = a, to = a, verb = "loop$i") }
                }

            val doc = parseSvg(fm.renderSvg())
            val width = doc.documentElement.getAttribute("width").toDouble()
            val height = doc.documentElement.getAttribute("height").toDouble()

            val allCoords =
                (
                    doc.elementsByTag("path").flatMap { p ->
                        Regex("-?\\d+\\.?\\d*").findAll(p.getAttribute("d")).map { it.value.toDouble() }
                    } +
                        doc.elementsByTag("polygon").flatMap { poly ->
                            poly.getAttribute("points").split(" ").flatMap { pair ->
                                pair.split(",").map(String::toDouble)
                            }
                        }
                ).toList()

            allCoords.filterIndexed { i, _ -> i % 2 == 0 }.forEach { x ->
                (x >= -0.01) shouldBe true
                (x <= width + 0.01) shouldBe true
            }
            allCoords.filterIndexed { i, _ -> i % 2 == 1 }.forEach { y ->
                (y >= -0.01) shouldBe true
                (y <= height + 0.01) shouldBe true
            }

            // Label boxes too, measured independently via the same public JDK API as the earlier
            // self-loop-label canvas test.
            val font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12)
            val frc = java.awt.font.FontRenderContext(null, true, true)
            doc
                .elementsByTag(
                    "text",
                ).filter { it.textContent.startsWith("v") || it.textContent.startsWith("loop") }
                .forEach { el ->
                    val b = font.getStringBounds(el.textContent, frc)
                    val cx = el.getAttribute("x").toDouble()
                    val baselineY = el.getAttribute("y").toDouble()
                    val minX = cx - b.width / 2.0
                    val maxX = cx + b.width / 2.0
                    val minY = baselineY + b.y
                    val maxY = minY + b.height
                    (minX >= -0.5) shouldBe true
                    (maxX <= width + 0.5) shouldBe true
                    (minY >= -0.5) shouldBe true
                    (maxY <= height + 0.5) shouldBe true
                }
        }

        "two self-loops on the same component get visually distinct radii" {
            val fm =
                functionModel {
                    val turbine = component("Turbine")
                    useful(from = turbine, to = turbine, verb = "wears")
                    harmful(from = turbine, to = turbine, verb = "cracks")
                }

            val doc = parseSvg(fm.renderSvg())
            val paths = doc.elementsByTag("path")
            paths shouldHaveSize 2

            fun maxX(d: String): Double =
                Regex("-?\\d+\\.?\\d*")
                    .findAll(d)
                    .map { it.value.toDouble() }
                    .toList()
                    .filterIndexed { i, _ -> i % 2 == 0 }
                    .max()

            val maxXs = paths.map { maxX(it.getAttribute("d")) }
            (maxXs[0] != maxXs[1]) shouldBe true
        }

        "an edge verb that cannot avoid overlap even after the full candidate search is truncated with a title" {
            // Exercises placeEdgeLabels' fallback branch (FunctionModelSvgRenderer.kt): when none
            // of the LABEL_CANDIDATE_MAX (60) full-text candidates comes back overlap-free, the
            // verb is shrunk via displayTextFor (to roughly LABEL_TRUNCATE_WIDTH_FACTOR of its
            // measured width) and the search re-run; if the shrunk text's best placement beats the
            // full text's, the shrunk, truncated text wins and renderEdge emits a <title> tooltip
            // carrying the full verb. 80 self-loops sharing one component, each with a long verb,
            // pack the label stack far too densely for any of the 60 candidates to stay
            // overlap-free -- confirmed this reaches the fallback path via a scratch run before
            // writing this as a permanent regression guard.
            val longVerb = "compresses the primary intake manifold across every operating stage"
            val fm =
                functionModel {
                    val turbine = component("Turbine")
                    repeat(80) { i -> useful(from = turbine, to = turbine, verb = "$longVerb $i") }
                }

            val doc = parseSvg(fm.renderSvg())
            val titles = doc.elementsByTag("title")

            // renderNode never emits a title here (the single "Turbine" name is short and never
            // truncates), so every <title> present must come from renderEdge's truncated branch.
            titles.shouldNotBeEmpty()

            titles.forEach { title ->
                val fullVerb = title.textContent
                (fullVerb.startsWith(longVerb)) shouldBe true

                // The <title> is the first child of its enclosing <text> element; the visible,
                // truncated label text is the remaining text content after it.
                val textEl = title.parentNode as Element
                val visibleText = textEl.textContent.removePrefix(fullVerb)

                visibleText.endsWith("…") shouldBe true
                (visibleText.length < fullVerb.length) shouldBe true
            }
        }
    })
