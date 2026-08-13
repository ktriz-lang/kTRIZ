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
    })
