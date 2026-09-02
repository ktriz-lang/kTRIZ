package dev.ktriz.tests

import dev.ktriz.function.Component
import dev.ktriz.render.kuml.renderSvg
import dev.ktriz.sufield.FieldType.GRAVITATIONAL
import dev.ktriz.sufield.FieldType.MECHANICAL
import dev.ktriz.sufield.SuField
import dev.ktriz.sufield.SuFieldQuality
import dev.ktriz.sufield.SuFieldQuality.COMPLETE
import dev.ktriz.sufield.SuFieldQuality.EXCESSIVE
import dev.ktriz.sufield.SuFieldQuality.HARMFUL
import dev.ktriz.sufield.SuFieldQuality.INCOMPLETE
import dev.ktriz.sufield.SuFieldQuality.INSUFFICIENT
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
import java.io.File
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

private fun List<Element>.withClass(cssClass: String): List<Element> = filter { it.getAttribute("class") == cssClass }

/**
 * The text directly inside [element], excluding any descendant element's content -- unlike
 * `textContent`, which concatenates a `<title>` child's (untruncated) full-name text in front of
 * the label's own (possibly truncated) display text. Needed wherever a label may carry a
 * `<title>` tooltip, e.g. [OutsideLabel.toSvgTag]'s `titleTag + display.text` structure.
 */
private fun Element.ownTextContent(): String =
    (0 until childNodes.length)
        .map { childNodes.item(it) }
        .filter { it.nodeType == org.w3c.dom.Node.TEXT_NODE }
        .joinToString("") { it.nodeValue }

private fun coordsOf(d: String): List<Double> = Regex("-?\\d+\\.?\\d*").findAll(d).map { it.value.toDouble() }.toList()

private fun lastCoordPair(path: Element): Pair<Double, Double> {
    val coords = coordsOf(path.getAttribute("d"))
    return coords[coords.size - 2] to coords[coords.size - 1]
}

private fun tipOf(polygon: Element): Pair<Double, Double> =
    polygon
        .getAttribute("points")
        .split(" ")
        .first()
        .split(",")
        .let { (x, y) -> x.toDouble() to y.toDouble() }

private val workpiece = Component("Workpiece")
private val tool = Component("Grinding wheel")

private fun completeTriangle(quality: SuFieldQuality) =
    SuField(s1 = workpiece, s2 = tool, field = MECHANICAL, quality = quality)

class SuFieldSvgRendererTest :
    StringSpec({
        "COMPLETE renders 3 circles, 3 paths, 1 polygon, and an unstyled action path" {
            val sf = completeTriangle(COMPLETE)
            val doc = parseSvg(sf.renderSvg())

            doc.elementsByTag("circle") shouldHaveSize 3
            doc.elementsByTag("path") shouldHaveSize 3
            doc.elementsByTag("polygon") shouldHaveSize 1

            val action = doc.elementsByTag("path").withClass("su-field-action").single()
            action.hasAttribute("stroke-dasharray") shouldBe false
        }

        "INSUFFICIENT dashes only the action path, never a leg" {
            val sf = completeTriangle(INSUFFICIENT)
            val doc = parseSvg(sf.renderSvg())

            val action = doc.elementsByTag("path").withClass("su-field-action").single()
            action.getAttribute("stroke-dasharray") shouldBe "8 4"

            val legs = doc.elementsByTag("path").withClass("su-field-leg")
            legs shouldHaveSize 2
            legs.forEach { it.hasAttribute("stroke-dasharray") shouldBe false }
        }

        "EXCESSIVE draws two action paths and one polygon, straddling the base symmetrically" {
            val sf = completeTriangle(EXCESSIVE)
            val doc = parseSvg(sf.renderSvg())

            val actions = doc.elementsByTag("path").withClass("su-field-action")
            actions shouldHaveSize 2
            val polygons = doc.elementsByTag("polygon")
            polygons shouldHaveSize 1

            val (x1a, y1a) = lastCoordPair(actions[0])
            val (x1b, y1b) = lastCoordPair(actions[1])
            val expectedTip = (x1a + x1b) / 2.0 to (y1a + y1b) / 2.0

            val tip = tipOf(polygons.single())
            abs(tip.first - expectedTip.first) shouldBeLessThan 0.01
            abs(tip.second - expectedTip.second) shouldBeLessThan 0.01
        }

        "HARMFUL renders a wavy action path but plain straight legs" {
            val sf = completeTriangle(HARMFUL)
            val doc = parseSvg(sf.renderSvg())

            val action = doc.elementsByTag("path").withClass("su-field-action").single()
            val actionLCount = Regex("L ").findAll(action.getAttribute("d")).count()
            actionLCount shouldBeGreaterThan 1

            val legs = doc.elementsByTag("path").withClass("su-field-leg")
            legs shouldHaveSize 2
            legs.forEach { leg ->
                Regex("L ").findAll(leg.getAttribute("d")).count() shouldBe 1
            }
        }

        "INCOMPLETE with s2 and field both null renders only S1" {
            val sf = SuField(s1 = workpiece, s2 = null, field = null, quality = INCOMPLETE)
            val doc = parseSvg(sf.renderSvg())

            doc.elementsByTag("circle") shouldHaveSize 1
            doc.elementsByTag("path") shouldHaveSize 0
            doc.elementsByTag("polygon") shouldHaveSize 0
            sf.renderSvg() shouldContain "Workpiece"
        }

        "INCOMPLETE with s2 set and field null renders S1 and S2 with no legs or base" {
            val sf = SuField(s1 = workpiece, s2 = tool, field = null, quality = INCOMPLETE)
            val doc = parseSvg(sf.renderSvg())

            doc.elementsByTag("circle") shouldHaveSize 2
            doc.elementsByTag("path") shouldHaveSize 0
            doc.elementsByTag("polygon") shouldHaveSize 0
        }

        "INCOMPLETE with s2 null and field set renders S1 and F joined by a single leg, no arrowhead" {
            val sf = SuField(s1 = workpiece, s2 = null, field = MECHANICAL, quality = INCOMPLETE)
            val doc = parseSvg(sf.renderSvg())

            doc.elementsByTag("circle") shouldHaveSize 2
            val paths = doc.elementsByTag("path")
            paths shouldHaveSize 1
            paths.single().getAttribute("class") shouldBe "su-field-leg"
            doc.elementsByTag("polygon") shouldHaveSize 0
        }

        "no INCOMPLETE shape invents a placeholder vertex or an empty symbol" {
            val shapes =
                listOf(
                    SuField(s1 = workpiece, s2 = null, field = null, quality = INCOMPLETE) to 1,
                    SuField(s1 = workpiece, s2 = tool, field = null, quality = INCOMPLETE) to 2,
                    SuField(s1 = workpiece, s2 = null, field = MECHANICAL, quality = INCOMPLETE) to 2,
                )
            shapes.forEach { (sf, expectedVertexCount) ->
                val doc = parseSvg(sf.renderSvg())
                val circles = doc.elementsByTag("circle")
                circles shouldHaveSize expectedVertexCount

                val symbols = doc.elementsByTag("text").withClass("su-field-symbol")
                symbols shouldHaveSize expectedVertexCount
                symbols.forEach { it.textContent.isNotBlank() shouldBe true }
            }
        }

        "a component name with angle brackets/ampersands still yields valid XML, no raw unescaped bracket" {
            val evil = Component("A & B <script>alert('x')</script>")
            val sf = SuField(s1 = evil, s2 = null, field = null, quality = INCOMPLETE)

            val svg = sf.renderSvg()

            parseSvg(svg)
            svg shouldNotContain "<script>"
        }

        "hostile control characters and a lone surrogate in a name still parse as well-formed XML" {
            val hostile = Component("Bad\u0000Name\bWith\uD800Lone")
            val sf = SuField(s1 = hostile, s2 = null, field = null, quality = INCOMPLETE)

            val svg = sf.renderSvg()

            // Would throw an XML parse exception against the pre-hardening xmlEscapeText.
            parseSvg(svg)
        }

        "a component name with an emoji outside the BMP keeps the valid surrogate pair intact in the output" {
            val emoji = "Grinder\uD83D\uDD27" // U+1F527 WRENCH, a valid high/low surrogate pair
            val withEmoji = Component(emoji)
            val sf = SuField(s1 = withEmoji, s2 = null, field = null, quality = INCOMPLETE)

            val svg = sf.renderSvg()

            // Proves stripXmlIllegal's code-point-aware iteration keeps a valid pair together
            // rather than dropping it as an "unpaired surrogate": asserts on what survives, not
            // merely that the document still parses.
            svg shouldContain emoji
            val doc = parseSvg(svg)
            val label =
                doc
                    .elementsByTag("text")
                    .withClass("su-field-label")
                    .single()
            label.textContent shouldContain emoji
        }

        "a component name with tab, newline, and carriage return keeps all three as literal legal characters" {
            val whitespace = "Bad\tTab\nLine\rReturn"
            val withWhitespace = Component(whitespace)
            val sf = SuField(s1 = withWhitespace, s2 = null, field = null, quality = INCOMPLETE)

            val svg = sf.renderSvg()

            // Proves stripXmlIllegal's explicit U+0009/U+000A/U+000D exception keeps these three
            // legal rather than stripping them -- a narrowed condition would still leave a
            // parseable document, so this checks what survives, not just that parsing succeeds.
            // Asserted against the full literal name rather than the three whitespace characters
            // in isolation: svgDocument()'s style block itself contains raw newlines, so a bare
            // `svg shouldContain "\n"` would pass unconditionally regardless of whether the
            // renderer preserves whitespace in component names. The label isn't truncated, so the
            // full 19-character name lands verbatim in the <text> element.
            svg shouldContain whitespace
            parseSvg(svg)
        }

        "renderSvg produces a well-formed document with a parseable viewBox and positive dimensions" {
            val sf = completeTriangle(COMPLETE)
            val doc = parseSvg(sf.renderSvg())

            doc.documentElement.tagName shouldBe "svg"
            val viewBoxNumbers =
                doc.documentElement
                    .getAttribute("viewBox")
                    .split(" ")
                    .map { it.toDouble() }
            viewBoxNumbers shouldHaveSize 4
            doc.documentElement.getAttribute("width").toDouble() shouldBeGreaterThan 0.0
            doc.documentElement.getAttribute("height").toDouble() shouldBeGreaterThan 0.0
        }

        "no rendered label's bounding box falls outside the viewBox, for long S1/S2/field names" {
            // Regression guard for the labels.forEach block in SuFieldScene.bounds() (this file's
            // KDoc at SuFieldSvgRenderer.kt:201-210 names the exact bug class this prevents:
            // "the exact clipped-label bug class documented in FunctionModelSvgRenderer.kt's
            // canvasBoundsFor KDoc"). Deleting that block entirely still leaves every existing
            // test in this file green (circle/path/polygon counts, viewBox shape, deterministic
            // dimensions) because none of them measure a label's rendered extent against the
            // canvas -- confirmed live by removing the block and rerunning the full suite before
            // writing this test. Exercises all three anchor branches at once: S1 ("end", widens
            // only x, to the *left* of its x), S2 ("start", widens only x, to the *right*), and
            // the field's top label ("middle", widens x on both sides *and* y upward by its own
            // font size for glyph ascent above the baseline). Same pattern as
            // FunctionModelSvgRendererTest.kt's "a self-loop's verb label stays within the
            // canvas..." and "no rendered edge point falls outside the SVG's own viewBox...":
            // width measured independently via java.awt.Font/FontRenderContext at the renderer's
            // own font sizes (NODE_FONT_SIZE_PX=14 for side labels, EDGE_LABEL_FONT_SIZE_PX=12
            // for the top label), never via this module's own (internal) measuring helper.
            val sf =
                SuField(
                    s1 = Component("Precision-ground workpiece surface under active load"),
                    s2 = Component("High-speed diamond-abrasive grinding wheel assembly"),
                    field = GRAVITATIONAL,
                    quality = COMPLETE,
                )
            val doc = parseSvg(sf.renderSvg())
            val width = doc.documentElement.getAttribute("width").toDouble()
            val height = doc.documentElement.getAttribute("height").toDouble()

            val sideFont = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 14)
            val topFont = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 12)
            val frc = java.awt.font.FontRenderContext(null, true, true)

            val labels = doc.elementsByTag("text").withClass("su-field-label")
            labels shouldHaveSize 3

            labels.forEach { label ->
                // Not textContent: a truncated label carries a <title> child holding the *full*
                // (untruncated) name ahead of its own display text in document order, which
                // textContent would concatenate in -- see ownTextContent's own doc comment.
                val text = label.ownTextContent()
                val anchor = label.getAttribute("text-anchor")
                val x = label.getAttribute("x").toDouble()
                val y = label.getAttribute("y").toDouble()
                val font = if (anchor == "middle") topFont else sideFont
                val measuredWidth = font.getStringBounds(text, frc).width

                val (minX, maxX) =
                    when (anchor) {
                        "end" -> (x - measuredWidth) to x
                        "start" -> x to (x + measuredWidth)
                        else -> (x - measuredWidth / 2.0) to (x + measuredWidth / 2.0)
                    }
                (minX >= -0.5) shouldBe true
                (maxX <= width + 0.5) shouldBe true
                (y <= height + 0.5) shouldBe true
                if (anchor == "middle") {
                    // The field's top label sits on its own baseline; its glyphs rise
                    // topFont's size above it, per SuFieldSvgRenderer.kt:236-240.
                    (y - 12.0 >= -0.5) shouldBe true
                }
            }
        }

        "FieldType.MECHANICAL renders an F symbol with a Mec subscript and a Mechanical outside label" {
            val sf = completeTriangle(COMPLETE)
            val doc = parseSvg(sf.renderSvg())

            val tspan = doc.elementsByTag("tspan").single()
            tspan.textContent shouldBe "Mec"

            val fieldSymbol =
                doc
                    .elementsByTag(
                        "text",
                    ).withClass("su-field-symbol")
                    .single { it.textContent.startsWith("F") }
            fieldSymbol.textContent shouldBe "FMec"

            val fieldLabel =
                doc.elementsByTag("text").withClass("su-field-label").single {
                    it.textContent ==
                        "Mechanical"
                }
            fieldLabel.getAttribute("text-anchor") shouldBe "middle"
        }

        "renderSvg is deterministic across two calls on equal models" {
            val a = completeTriangle(EXCESSIVE)
            val b = completeTriangle(EXCESSIVE)

            a.renderSvg() shouldBe b.renderSvg()
        }

        "a long S1 name truncates with an ellipsis, carries a bounded title, and keeps the canvas well under 1000px" {
            val longName = "X".repeat(600)
            val sf = completeTriangle(COMPLETE).copy(s1 = Component(longName))
            val doc = parseSvg(sf.renderSvg())

            val titles = doc.elementsByTag("title")
            titles.shouldNotBeEmpty()
            titles.forEach { (it.textContent.length <= 513) shouldBe true }
            titles.any { it.textContent.endsWith("…") } shouldBe true

            doc.documentElement.getAttribute("width").toDouble() shouldBeLessThan 1000.0
        }

        "S1's rendered cx is identical whether the triangle is complete or S1-only, for the same S1 name" {
            val complete = SuField(s1 = Component("Workpiece"), s2 = tool, field = MECHANICAL, quality = COMPLETE)
            val s1Only = SuField(s1 = Component("Workpiece"), s2 = null, field = null, quality = INCOMPLETE)

            fun s1Cx(sf: SuField): Double {
                val doc = parseSvg(sf.renderSvg())
                val circles = doc.elementsByTag("circle")
                // The smallest cx is always S1's (S1 sits at the leftmost slot in the local frame).
                return circles.minOf { it.getAttribute("cx").toDouble() }
            }

            abs(s1Cx(complete) - s1Cx(s1Only)) shouldBeLessThan 0.01
        }

        "the base action points right-to-left into S1, and the arrowhead tip sits on it" {
            val sf = completeTriangle(INSUFFICIENT)
            val doc = parseSvg(sf.renderSvg())

            val circles = doc.elementsByTag("circle")
            val s1Cx = circles.minOf { it.getAttribute("cx").toDouble() }
            val s2Cx = circles.maxOf { it.getAttribute("cx").toDouble() }
            s2Cx shouldBeGreaterThan s1Cx

            val action = doc.elementsByTag("path").withClass("su-field-action").single()
            val (endX, endY) = lastCoordPair(action)
            val tip = tipOf(doc.elementsByTag("polygon").single())

            abs(tip.first - endX) shouldBeLessThan 0.01
            abs(tip.second - endY) shouldBeLessThan 0.01
        }

        // Manual visual-inspection fixtures for the renderer-validation routine (CLAUDE.md):
        // one SVG per SuFieldQuality plus all three INCOMPLETE shapes, written to
        // ktriz-tests/build/sample-output/su-field/ (build/ is gitignored, nothing committed).
        // No custom Gradle task -- kUML's configuration-cache lesson (CLAUDE.md) says a
        // tasks.register { doLast { ... } } writing external files reliably breaks the config
        // cache; this afterSpec hook already has everything it needs.
        afterSpec {
            val dir = File("build/sample-output/su-field").apply { mkdirs() }
            val samples =
                mapOf(
                    "complete" to completeTriangle(COMPLETE),
                    "insufficient" to completeTriangle(INSUFFICIENT),
                    "excessive" to completeTriangle(EXCESSIVE),
                    "harmful" to completeTriangle(HARMFUL),
                    "incomplete-s1-only" to SuField(s1 = workpiece, s2 = null, field = null, quality = INCOMPLETE),
                    "incomplete-s2-no-field" to SuField(s1 = workpiece, s2 = tool, field = null, quality = INCOMPLETE),
                    "incomplete-field-no-s2" to
                        SuField(s1 = workpiece, s2 = null, field = MECHANICAL, quality = INCOMPLETE),
                )
            samples.forEach { (name, sf) -> File(dir, "$name.svg").writeText(sf.renderSvg()) }
        }
    })
