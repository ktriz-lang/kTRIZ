package dev.ktriz.render.kuml

import dev.ktriz.function.FunctionModel
import dev.ktriz.function.functionModel
import dev.kuml.layout.Point
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

// All assertions are relational (candidate A vs candidate B, before vs after) or compare two runs
// in the same process: text widths come from the platform's fonts, so no absolute position or
// width literal from one OS may appear here.

private const val X = 2100f
private const val MID_Y = 1000f

/** A model with [n] useful edges between two components -- geometry is replaced by hand below. */
private fun manyEdges(
    n: Int,
    verb: (Int) -> String = { "acts$it" },
): FunctionModel =
    functionModel {
        val a = component("Alpha")
        val b = component("Beta")
        repeat(n) { useful(a, b, verb(it)) }
    }

private fun hline(
    y: Float,
    from: Float,
    to: Float,
) = listOf(Point(from, y), Point(to, y))

/** Hand-built geometry far away from the laid-out nodes (x ~ 2100) so node boxes never interfere. */
private fun FunctionModel.placeOn(
    segments: List<List<Point>>,
    avoidLines: Boolean = true,
): Map<Int, EdgeLabelPlacement> {
    val plan = computeRenderPlan()
    val geometry = EdgeGeometry(bases = segments.withIndex().associate { it.index to it.value })
    return placeEdgeLabels(plan.result, geometry, avoidLines)
}

private fun stepFor(text: String): Float {
    val box = edgeLabelBoxAt(text, X, 0f)
    return maxOf(EDGE_LABEL_STACK_STEP_PX, (box.maxY - box.minY) + 2f * LABEL_COLLISION_PAD_PX)
}

/** Baseline of candidate [c] for a label of [text] on a route whose midpoint y is [midY]. */
private fun candidateBaseline(
    text: String,
    midY: Float,
    c: Int,
): Float {
    val level = (c + 1) / 2
    val sign = if (c % 2 == 1) -1f else 1f
    return midY - EDGE_LABEL_BASE_OFFSET_PX + sign * level * stepFor(text)
}

private fun centerYOfCandidate0(text: String): Float {
    val box = edgeLabelBoxAt(text, X, candidateBaseline(text, MID_Y, 0))
    return (box.minY + box.maxY) / 2f
}

private fun crossingPairs(
    fm: FunctionModel,
    plan: RenderPlan,
): Int {
    val obstacles = fm.lineObstacles(plan.geometry)
    var count = 0
    for ((i, p) in plan.labels) {
        val box = edgeLabelBoxAt(p.text, p.centerX, p.baselineY).inflate(LABEL_COLLISION_PAD_PX)
        for (j in plan.geometry.bases.keys) {
            if (j == i) continue
            if (obstacles.filter { it.edgeIndex == j }.lengthInside(box, i) > LABEL_LINE_EPSILON_PX) count++
        }
    }
    return count
}

private fun stress(n: Int): FunctionModel =
    functionModel {
        val a = component("Alpha")
        val b = component("Beta")
        repeat(n) { i ->
            val forward = i % 3 != 2
            val from = if (forward) a else b
            val to = if (forward) b else a
            when (i % 4) {
                0 -> useful(from, to, "act$i")
                1 -> insufficient(from, to, "act$i")
                2 -> excessive(from, to, "act$i")
                else -> harmful(from, to, "act$i")
            }
        }
    }

class EdgeLabelLineAvoidanceTest :
    StringSpec({
        "a label moves off a foreign line that crosses candidate 0, and stays on it without avoidance" {
            val fm = manyEdges(2, verb = { if (it == 0) "acts" else "other" })
            val line = hline(centerYOfCandidate0("acts"), X - 300f, X + 300f)
            val own = hline(MID_Y, X - 100f, X + 100f)
            val on = fm.placeOn(listOf(own, line))
            val off = fm.placeOn(listOf(own, line), avoidLines = false)
            off.getValue(0).baselineY shouldBe candidateBaseline("acts", MID_Y, 0)
            on.getValue(0).baselineY shouldNotBe candidateBaseline("acts", MID_Y, 0)
            // Within the first three levels: close to its own edge.
            (kotlin.math.abs(on.getValue(0).baselineY - candidateBaseline("acts", MID_Y, 0)) / stepFor("acts"))
                .shouldBeLessThanOrEqualTo(3.01f)
            on.getValue(0).truncated shouldBe false
        }

        "a line-free candidate beyond the search reach does not attract the label; shortest crossing wins" {
            val text = "acts"
            val step = stepFor(text)
            val c0 = centerYOfCandidate0(text)
            // Foreign lines through the centres of candidates 0..6 (levels 0..3). Candidate 2
            // (level 1, below) gets a short line that covers only a sliver of the label.
            val fm = manyEdges(8, verb = { if (it == 0) text else "other$it" })
            val own = hline(MID_Y, X - 100f, X + 100f)
            val halfWidth = edgeLabelBoxAt(text, X, 0f).let { (it.maxX - it.minX) / 2f }
            val lines =
                (-3..3).map { k ->
                    val to = if (k == 1) X - halfWidth + 2f else X + 300f
                    hline(c0 + k * step, X - 300f, to)
                }
            val placement = fm.placeOn(listOf(own) + lines).getValue(0)
            placement.baselineY shouldBe candidateBaseline(text, MID_Y, 2)
            placement.truncated shouldBe false
        }

        "overlap with other labels outweighs line crossing: line-free but overlapped candidates lose" {
            val text = "acts"
            val step = stepFor(text)
            // Six tiny blockers whose labels sit on the subject's candidates 1..6 (shifted a few px
            // outward so they overlap those candidates but not candidate 0), then the subject, then
            // one long line through the subject's candidate 0.
            val blockerCount = 6
            val verbs = List(blockerCount) { text } + text + "line"
            val fm = manyEdges(blockerCount + 2, verb = { verbs[it] })
            val blockers =
                (1..blockerCount).map { c ->
                    val level = (c + 1) / 2
                    val outward = (if (c % 2 == 1) -1f else 1f) * (3f + 2f * (level - 1))
                    // baseline of blocker candidate 0 == subject candidate c baseline + outward
                    val baseline = candidateBaseline(text, MID_Y, c) + outward
                    val midY = baseline + EDGE_LABEL_BASE_OFFSET_PX
                    listOf(Point(X - 0.05f, midY), Point(X + 0.05f, midY))
                }
            val subject = hline(MID_Y, X - 100f, X + 100f)
            val line = hline(centerYOfCandidate0(text), X - 300f, X + 300f)
            val segments = blockers + listOf(subject, line)
            val plan = fm.computeRenderPlan()
            val geometry = EdgeGeometry(bases = segments.withIndex().associate { it.index to it.value })
            val box = edgeLabelBoxAt(text, X, candidateBaseline(text, MID_Y, 0)).inflate(LABEL_COLLISION_PAD_PX)
            fm.lineObstacles(geometry).lengthInside(box, blockerCount) shouldBeGreaterThan LABEL_LINE_EPSILON_PX
            val placed = fm.placeEdgeLabels(plan.result, geometry)
            placed.getValue(blockerCount).baselineY shouldBe candidateBaseline(text, MID_Y, 0)
            step shouldBeGreaterThan 0f
        }

        "a verb that only crosses lines is never truncated" {
            val fm = manyEdges(2, verb = { if (it == 0) "acts" else "other" })
            val line = hline(centerYOfCandidate0("acts"), X - 300f, X + 300f)
            val p = fm.placeOn(listOf(hline(MID_Y, X - 100f, X + 100f), line)).getValue(0)
            p.truncated shouldBe false
            p.text shouldBe "acts"
        }

        "rendering a 24-edge stress model twice yields byte-identical SVG" {
            val fm = stress(24)
            fm.renderSvg() shouldBe fm.renderSvg()
        }

        "a single-edge model is placed identically with and without line avoidance" {
            val fm =
                functionModel {
                    val a = component("Engine")
                    val b = component("Coolant")
                    useful(b, a, "cools")
                    harmful(a, a, "wears")
                }
            val on = fm.computeRenderPlan(avoidLines = true).labels
            val off = fm.computeRenderPlan(avoidLines = false).labels
            on shouldBe off
        }

        "clipSegmentLength handles parallel, zero-length, inside and non-finite input" {
            val box = LabelBox(0f, 0f, 10f, 10f)
            clipSegmentLength(Point(-5f, 5f), Point(15f, 5f), box) shouldBe 10f
            clipSegmentLength(Point(-5f, 20f), Point(15f, 20f), box) shouldBe 0f
            clipSegmentLength(Point(5f, 5f), Point(5f, 5f), box) shouldBe 0f
            clipSegmentLength(Point(2f, 2f), Point(5f, 2f), box) shouldBe 3f
            clipSegmentLength(Point(Float.NaN, 0f), Point(1f, 1f), box) shouldBe 0f
            clipSegmentLength(Point(0f, Float.POSITIVE_INFINITY), Point(1f, 1f), box) shouldBe 0f
        }

        "measurement: label/foreign-line crossings before and after, bundles of 6, 12 and 24" {
            val rows = StringBuilder()
            for (n in listOf(6, 12, 24)) {
                val fm = stress(n)
                val before = crossingPairs(fm, fm.computeRenderPlan(avoidLines = false))
                val after = crossingPairs(fm, fm.computeRenderPlan(avoidLines = true))
                rows.append("bundle $n: before=$before after=$after\n")
                println("LABEL-LINE-CROSSINGS bundle $n: before=$before after=$after")
                after shouldBeLessThanOrEqualTo before
                if (n < 24) after shouldBeLessThan before
            }
        }

        "placement stays fast for a 400-edge bundle" {
            val fm = manyEdges(400)
            val t0 = System.nanoTime()
            fm.computeRenderPlan()
            ((System.nanoTime() - t0) / 1_000_000) shouldBeLessThan 60_000
        }
    })
