package dev.ktriz.tests

import dev.ktriz.function.FunctionModel
import dev.ktriz.function.functionModel
import dev.ktriz.render.kuml.renderSvg
import dev.ktriz.sufield.FieldType
import dev.ktriz.sufield.SuFieldQuality
import dev.ktriz.sufield.suField
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.awt.Font
import java.awt.font.FontRenderContext
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

private fun parse(svg: String): Document =
    DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
        ByteArrayInputStream(svg.toByteArray(Charsets.UTF_8)),
    )

private fun Document.tags(tag: String): List<Element> =
    getElementsByTagName(tag).let { list -> (0 until list.length).map { list.item(it) as Element } }

private fun Document.translate(): Pair<Double, Double> {
    val g = tags("g").firstOrNull { it.hasAttribute("transform") } ?: return 0.0 to 0.0
    val m = Regex("translate\\((-?[0-9.]+),(-?[0-9.]+)\\)").find(g.getAttribute("transform")) ?: return 0.0 to 0.0
    return m.groupValues[1].toDouble() to m.groupValues[2].toDouble()
}

private data class P(
    val x: Double,
    val y: Double,
)

private fun pathPoints(d: String): List<P> {
    val nums = Regex("-?\\d+\\.?\\d*(?:E-?\\d+)?").findAll(d).map { it.value.toDouble() }.toList()
    return nums.chunked(2).map { P(it[0], it[1]) }
}

private fun cross(
    o: P,
    a: P,
    b: P,
): Double = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

/** True only for a proper crossing (interiors intersect); touching endpoints do not count. */
private fun properlyCross(
    a: P,
    b: P,
    c: P,
    d: P,
): Boolean {
    val eps = 1e-6
    val d1 = cross(a, b, c)
    val d2 = cross(a, b, d)
    val d3 = cross(c, d, a)
    val d4 = cross(c, d, b)
    return ((d1 > eps && d2 < -eps) || (d1 < -eps && d2 > eps)) &&
        ((d3 > eps && d4 < -eps) || (d3 < -eps && d4 > eps))
}

private fun mixedBundle(
    n: Int,
    solidOnly: Boolean,
): FunctionModel =
    functionModel {
        val a = component("Alpha")
        val b = component("Beta")
        repeat(n) { i ->
            val forward = i % 3 != 2
            val from = if (forward) a else b
            val to = if (forward) b else a
            val verb = "act$i"
            if (solidOnly) {
                if (i % 2 == 0) useful(from, to, verb) else insufficient(from, to, verb)
            } else {
                when (i % 4) {
                    0 -> useful(from, to, verb)
                    1 -> insufficient(from, to, verb)
                    2 -> excessive(from, to, verb)
                    else -> harmful(from, to, verb)
                }
            }
        }
    }

private fun resource(name: String): String =
    FunctionModelBundleRenderTest::class.java
        .getResourceAsStream(
            "/golden/$name",
        )!!
        .readBytes()
        .toString(Charsets.UTF_8)

class FunctionModelBundleRenderTest :
    StringSpec({
        // Byte-identity guard: models without any multi-edge pair must render exactly as before
        // this change (the reference was generated from the unmodified renderer).
        "a model with only single edges and a self-loop renders byte-identically to the pre-bundle reference" {
            val fm =
                functionModel {
                    val a = component("Engine")
                    val b = component("Coolant")
                    val c = component("Cylinder block")
                    val d = component("Piston")
                    useful(from = b, to = a, verb = "cools")
                    harmful(from = a, to = c, verb = "overheats")
                    insufficient(from = d, to = c, verb = "seals")
                    excessive(from = a, to = d, verb = "pushes")
                    harmful(from = c, to = c, verb = "wears")
                }
            fm.renderSvg() shouldBe resource("function-model-single-edges.svg")
        }

        // The Su-Field renderer shares NodeSizing with the function-model renderer; the new
        // parameters must not leak into it.
        "a Su-Field model renders byte-identically to the pre-bundle reference" {
            val sf =
                suField {
                    val w = component("Workpiece")
                    val t = component("Grinding wheel")
                    s1(w)
                    s2(t)
                    field(FieldType.MECHANICAL)
                    quality(SuFieldQuality.INSUFFICIENT)
                }
            sf.renderSvg() shouldBe resource("su-field.svg")
        }

        // Bundled nodes grow, single-edge nodes do not (Rams: no growth without need).
        "nodes carrying a 24-edge bundle are widened to the 320 px cap, single-edge nodes keep their size" {
            val bundle = parse(mixedBundle(24, solidOnly = true).renderSvg())
            bundle.tags("rect").forEach { it.getAttribute("width").toDouble() shouldBe 320.0 }

            val single =
                parse(
                    functionModel {
                        val a = component("Alpha")
                        val b = component("Beta")
                        useful(a, b, "acts")
                    }.renderSvg(),
                )
            single.tags("rect").forEach { it.getAttribute("width").toDouble() shouldBe 96.0 }
        }

        // The old failure mode: lines of a bundle crossing each other. Only solid and dashed
        // edges are used so each <path> is exactly the edge's base polyline (doubled strokes
        // add a second path, wavy strokes add wiggles that cross by design).
        "no two edges of a bundle cross, for 6, 12 and 24 edges with mixed directions" {
            for (n in listOf(6, 12, 24)) {
                val doc = parse(mixedBundle(n, solidOnly = true).renderSvg())
                val paths = doc.tags("path").map { pathPoints(it.getAttribute("d")) }
                paths shouldHaveSize n
                var crossings = 0
                for (i in paths.indices) {
                    for (j in i + 1 until paths.size) {
                        for (s in 0 until paths[i].size - 1) {
                            for (t in 0 until paths[j].size - 1) {
                                if (properlyCross(
                                        paths[i][s],
                                        paths[i][s + 1],
                                        paths[j][t],
                                        paths[j][t + 1],
                                    )
                                ) {
                                    crossings++
                                }
                            }
                        }
                    }
                }
                crossings shouldBe 0
            }
        }

        // Edges that run the same way sit together: at the Alpha end, all edges leaving Alpha
        // start on one side of all edges arriving at Alpha.
        "forward and backward edges of a bundle occupy separate runs on the node side" {
            val doc = parse(mixedBundle(12, solidOnly = true).renderSvg())
            val paths = doc.tags("path").map { pathPoints(it.getAttribute("d")) }
            // Edge i is Alpha->Beta unless i % 3 == 2 (Beta->Alpha); paths are in edge order.
            val fwd = paths.indices.filter { it % 3 != 2 }.map { paths[it].first().x }
            val bwd = paths.indices.filter { it % 3 == 2 }.map { paths[it].last().x }
            val separated = fwd.max() < bwd.min() || fwd.min() > bwd.max()
            separated shouldBe true
        }

        // Every anchor is distinct: no two edge ends of a bundle at the same node coincide.
        "all edge endpoints of a 24-edge bundle are distinct" {
            val doc = parse(mixedBundle(24, solidOnly = true).renderSvg())
            val paths = doc.tags("path").map { pathPoints(it.getAttribute("d")) }
            val ends = paths.flatMap { listOf(it.first(), it.last()) }
            ends.map { "%.2f,%.2f".format(it.x, it.y) }.toSet() shouldHaveSize ends.size
        }

        // Nothing may be clipped: every path point, arrowhead corner and label box stays inside
        // the SVG viewport (after the translate wrapper), for dense bundles in all stroke styles
        // and for a normal multi-node model with a self-loop next to a bundle.
        "paths, arrowheads and labels stay inside the viewport for dense bundles and a mixed model" {
            val font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
            val frc = FontRenderContext(null, true, true)
            val models =
                listOf(6, 12, 24).map { mixedBundle(it, solidOnly = false) } +
                    functionModel {
                        val a = component("Engine")
                        val b = component("Coolant")
                        val c = component("Cylinder block")
                        val d = component("Piston")
                        val e = component("Crankshaft")
                        useful(b, a, "cools")
                        insufficient(b, a, "circulates")
                        excessive(b, a, "pressurizes")
                        harmful(a, c, "overheats")
                        useful(d, e, "turns")
                        useful(e, d, "lifts")
                        useful(a, d, "drives")
                        harmful(c, c, "wears")
                        harmful(c, c, "cracks")
                    }
            models.forEach { fm ->
                val doc = parse(fm.renderSvg())
                val root = doc.documentElement
                val w = root.getAttribute("width").toDouble()
                val h = root.getAttribute("height").toDouble()
                val (tx, ty) = doc.translate()

                fun inside(
                    x: Double,
                    y: Double,
                ) {
                    (x + tx) shouldBeGreaterThanOrEqualTo 0.0
                    (y + ty) shouldBeGreaterThanOrEqualTo 0.0
                    (x + tx) shouldBeLessThanOrEqualTo w
                    (y + ty) shouldBeLessThanOrEqualTo h
                }
                doc.tags("path").forEach { el -> pathPoints(el.getAttribute("d")).forEach { inside(it.x, it.y) } }
                doc.tags("polygon").forEach { el ->
                    el.getAttribute("points").split(" ").forEach { pair ->
                        val (x, y) = pair.split(",")
                        inside(x.toDouble(), y.toDouble())
                    }
                }
                doc.tags("text").filter { it.getAttribute("font-size") == "12.0" }.forEach { el ->
                    val text = el.textContent
                    val b = font.getStringBounds(text, frc)
                    val cx = el.getAttribute("x").toDouble()
                    val by = el.getAttribute("y").toDouble()
                    inside(cx - b.width / 2, by + b.y)
                    inside(cx + b.width / 2, by + b.y + b.height)
                }
            }
        }
    })
