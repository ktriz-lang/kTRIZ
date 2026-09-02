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

        "labels of edges sharing a component pair are staggered so they don't all sit at the same height" {
            // Regression guard for a bug found by manual visual inspection, not by the existing
            // "distinct midpoints" test below: that test only asserts the (x, y) label position
            // *pairs* are distinct, which the multi-edge bow already guaranteed via x alone on a
            // vertical route -- it did not catch that all three labels rendered at the *same y*,
            // which is what actually made them visually overlap (text width exceeds the bow's
            // few-pixel horizontal spacing).
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    useful(from = a, to = b, verb = "aaa")
                    harmful(from = a, to = b, verb = "bbb")
                    insufficient(from = a, to = b, verb = "ccc")
                }

            val doc = parseSvg(fm.renderSvg())
            val ys =
                doc
                    .elementsByTag("text")
                    .filter { it.textContent in setOf("aaa", "bbb", "ccc") }
                    .map { it.getAttribute("y").toDouble() }

            ys.toSet() shouldHaveSize 3
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
            // A dense bundle of parallel edges between the same pair pushes bowOffsetFor's
            // clamp (BOW_MAX_OFFSET_PX) on both sides of the route, plus a self-loop -- together
            // the widest plausible test of canvasBoundsFor's four-sided bounding box, since a
            // real ELK layout's edge orientation (and therefore which axis the bow perturbs)
            // is not controlled directly by this test.
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
                (x >= -0.01) shouldBe true
                (x <= width + 0.01) shouldBe true
            }
            allCoords.filterIndexed { i, _ -> i % 2 == 1 }.forEach { y ->
                (y >= -0.01) shouldBe true
                (y <= height + 0.01) shouldBe true
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
    })
