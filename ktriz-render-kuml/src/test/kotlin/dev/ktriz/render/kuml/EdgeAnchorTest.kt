package dev.ktriz.render.kuml

import dev.ktriz.function.functionModel
import dev.kuml.layout.LayoutDirection
import dev.kuml.layout.Point
import dev.kuml.layout.Rect
import dev.kuml.layout.Size
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlin.math.abs

private fun box(
    x: Float = 100f,
    y: Float = 50f,
    w: Float = 320f,
    h: Float = 48f,
) = Rect(origin = Point(x, y), size = Size(w, h))

/** Coordinate of [p] along [side] (x for TOP/BOTTOM, y for LEFT/RIGHT). */
private fun along(
    p: Point,
    side: BoxSide,
): Float = if (side == BoxSide.TOP || side == BoxSide.BOTTOM) p.x else p.y

private fun onEdge(
    p: Point,
    b: Rect,
    side: BoxSide,
): Float =
    when (side) {
        BoxSide.TOP -> abs(p.y - b.origin.y)
        BoxSide.BOTTOM -> abs(p.y - (b.origin.y + b.size.height))
        BoxSide.LEFT -> abs(p.x - b.origin.x)
        BoxSide.RIGHT -> abs(p.x - (b.origin.x + b.size.width))
    }

class EdgeAnchorTest :
    StringSpec({
        // Anchors must lie exactly on the box edge, otherwise the arrowhead floats or sinks into
        // the node; the corner inset keeps the arrowhead on the flat side.
        "anchors sit on the box edge and keep the corner inset, for every side and bundle size" {
            val b = box(h = 96f)
            for (side in BoxSide.entries) {
                for (count in listOf(2, 6, 12, 24)) {
                    val anchors = distributeAnchors(b, side, count, hasSelfLoops = false)
                    anchors shouldHaveSize count
                    val start = if (side == BoxSide.TOP || side == BoxSide.BOTTOM) b.origin.x else b.origin.y
                    val length = if (side == BoxSide.TOP || side == BoxSide.BOTTOM) b.size.width else b.size.height
                    anchors.forEach { a ->
                        onEdge(a, b, side) shouldBeLessThan 0.01f
                        (along(a, side) - start) shouldBeGreaterThanOrEqualTo ANCHOR_CORNER_INSET_PX - 0.01f
                        (start + length - along(a, side)) shouldBeGreaterThanOrEqualTo ANCHOR_CORNER_INSET_PX - 0.01f
                    }
                }
            }
        }

        // Distinct anchors are the whole point: 24 endpoints on the widest box (320 px) still get
        // about 12.3 px between neighbours, comfortably above the 10 px floor.
        "24 anchors on a 320 px side are pairwise distinct and at least 10 px apart, ascending" {
            val anchors = distributeAnchors(box(), BoxSide.TOP, 24, hasSelfLoops = false)
            anchors.zipWithNext().forEach { (p, q) ->
                (q.x - p.x) shouldBeGreaterThanOrEqualTo 10f
            }
            anchors.map { it.x }.toSet() shouldHaveSize 24
        }

        // The right side hosts the self-loop arc (exit 30% / return 70% of the height); an
        // anchor inside that band would sit on the loop. renderSvg() always uses a top-to-bottom
        // layout, so this is covered here on constructed rectangles, with a tall box so the band
        // leaves usable segments on both sides.
        "self-loop band on the right side is kept free, with clearance, on tall boxes" {
            val b = box(h = 200f)
            val bandLo = b.origin.y + 200f * SELF_LOOP_EXIT_FRACTION - SELF_LOOP_ANCHOR_CLEARANCE_PX
            val bandHi = b.origin.y + 200f * SELF_LOOP_RETURN_FRACTION + SELF_LOOP_ANCHOR_CLEARANCE_PX
            usableSpans(b, BoxSide.RIGHT, hasSelfLoops = true) shouldHaveSize 2
            val anchors = distributeAnchors(b, BoxSide.RIGHT, 10, hasSelfLoops = true)
            anchors shouldHaveSize 10
            anchors.forEach { a -> (a.y <= bandLo || a.y >= bandHi) shouldBe true }
            // Without self-loops the same side is one continuous range.
            usableSpans(b, BoxSide.RIGHT, hasSelfLoops = false) shouldHaveSize 1
            // The band only applies to the RIGHT side.
            usableSpans(b, BoxSide.LEFT, hasSelfLoops = true) shouldHaveSize 1
        }

        // A box too short to leave anything outside the band falls back to the uncut range
        // instead of returning no anchor positions at all.
        "when the self-loop band covers the whole side the spans fall back to the uncut range" {
            val spans = usableSpans(box(h = 48f), BoxSide.RIGHT, hasSelfLoops = true)
            spans shouldHaveSize 1
            distributeAnchors(box(h = 48f), BoxSide.RIGHT, 3, hasSelfLoops = true) shouldHaveSize 3
        }

        // Degenerate input: count 0 is an empty list, a negative count is a programming error,
        // and a huge count stays linear (no quadratic work in the DoS sense).
        "distributeAnchors handles count 0, rejects negative counts and stays fast for 10000" {
            distributeAnchors(box(), BoxSide.TOP, 0, false) shouldHaveSize 0
            var threw = false
            try {
                distributeAnchors(box(), BoxSide.TOP, -1, false)
            } catch (_: IllegalArgumentException) {
                threw = true
            }
            threw shouldBe true
            val t0 = System.nanoTime()
            distributeAnchors(box(), BoxSide.TOP, 10_000, false) shouldHaveSize 10_000
            ((System.nanoTime() - t0) / 1_000_000) shouldBeLessThan 2_000
        }

        // A box smaller than twice the corner inset has no usable range; the anchors collapse
        // onto the centre line instead of producing NaN or an exception.
        "a box too small for the corner inset still yields finite anchors" {
            val tiny = box(w = 20f, h = 20f)
            val anchors = distributeAnchors(tiny, BoxSide.TOP, 3, false)
            anchors shouldHaveSize 3
            anchors.forEach { (it.x.isFinite() && it.y.isFinite()) shouldBe true }
        }

        // Ties resolve in declaration order so corner points behave the same on every JVM/OS.
        "nearestSide picks the closest side and breaks ties TOP, BOTTOM, LEFT, RIGHT" {
            val b = box(w = 100f, h = 100f) // x 100..200, y 50..150
            nearestSide(Point(150f, 52f), b) shouldBe BoxSide.TOP
            nearestSide(Point(150f, 149f), b) shouldBe BoxSide.BOTTOM
            nearestSide(Point(101f, 100f), b) shouldBe BoxSide.LEFT
            nearestSide(Point(199f, 100f), b) shouldBe BoxSide.RIGHT
            nearestSide(Point(100f, 50f), b) shouldBe BoxSide.TOP // corner tie TOP/LEFT
            nearestSide(Point(100f, 150f), b) shouldBe BoxSide.BOTTOM // corner tie BOTTOM/LEFT
            nearestSide(Point(200f, 100f), b) shouldBe BoxSide.RIGHT
        }

        // deformRoute must land exactly on the anchors (arrowhead tip == anchor) and keep a
        // smooth, monotone blend in between.
        "deformRoute starts and ends exactly on the anchors" {
            val raw = listOf(Point(0f, 0f), Point(0f, 50f), Point(10f, 100f))
            val s = Point(7f, -1f)
            val e = Point(33f, 101f)
            val out = deformRoute(raw, s, e)
            out shouldHaveSize BOW_SAMPLES
            out.first() shouldBe s
            out.last().x shouldBe e.x
            out.last().y shouldBe e.y
        }

        // Wavy strokes inside a bundle must not swing wider than the gap to the neighbour:
        // 40% of the spacing, never above the single-edge amplitude.
        "group wave amplitude is min(6, 0.4 x spacing)" {
            groupWaveAmplitudePx(10f) shouldBe 4f
            groupWaveAmplitudePx(14f) shouldBe 5.6f
            groupWaveAmplitudePx(15f) shouldBe WAVE_AMPLITUDE_PX
            groupWaveAmplitudePx(100f) shouldBe WAVE_AMPLITUDE_PX
            groupWaveAmplitudePx(0f) shouldBe 0f
            groupWaveAmplitudePx(Float.MAX_VALUE) shouldBe WAVE_AMPLITUDE_PX
        }

        // Sizing: a plain node keeps its text-driven size (this is the byte-identity guard for
        // models without bundles); a bundle grows the axis the lines dock to, up to the cap.
        "measureNodeSize without a bundle is unchanged for every direction" {
            val plain = measureNodeSize("Engine")
            for (d in LayoutDirection.entries) {
                measureNodeSize("Engine", 0, d) shouldBe plain
            }
        }

        "a bundle grows the width in vertical layouts and the height in horizontal layouts" {
            val plain = measureNodeSize("Engine")
            val v = measureNodeSize("Engine", 12, LayoutDirection.TopToBottom)
            v.width shouldBeGreaterThan plain.width
            v.height shouldBe plain.height
            v.width shouldBeGreaterThanOrEqualTo 12 * ANCHOR_SPACING_PX + 2 * ANCHOR_CORNER_INSET_PX
            val h = measureNodeSize("Engine", 12, LayoutDirection.LeftToRight)
            h.height shouldBeGreaterThan plain.height
            h.width shouldBe plain.width
            measureNodeSize("Engine", 12, LayoutDirection.BottomToTop) shouldBe v
            measureNodeSize("Engine", 12, LayoutDirection.RightToLeft) shouldBe h
        }

        // No growth when the text already needs more than the bundle does, and a hard cap at
        // 320 px even for absurd bundle sizes.
        "growth is capped at 320 px and skipped when the text is already wider" {
            measureNodeSize("Engine", 10_000, LayoutDirection.TopToBottom).width shouldBe MAX_NODE_WIDTH_PX
            measureNodeSize("Engine", 10_000, LayoutDirection.LeftToRight).height shouldBe MAX_NODE_WIDTH_PX
            measureNodeSize("Engine", Int.MAX_VALUE, LayoutDirection.TopToBottom).width shouldBe MAX_NODE_WIDTH_PX
            val longName = "A very long component name that needs a wide box on its own"
            measureNodeSize(longName, 2, LayoutDirection.TopToBottom) shouldBe measureNodeSize(longName)
        }

        // Layout-side bookkeeping: only bundles of 2+ edges count, self-loops never do, and a
        // component's count is the number of bundle endpoints it carries.
        "groupedEdgeCounts counts bundle endpoints per node and ignores single edges and self-loops" {
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    val c = component("C")
                    useful(from = a, to = b, verb = "x1")
                    harmful(from = b, to = a, verb = "x2")
                    useful(from = a, to = b, verb = "x3")
                    useful(from = b, to = c, verb = "single")
                    useful(from = c, to = c, verb = "loop")
                    useful(from = c, to = c, verb = "loop2")
                }
            fm.groupedEdgeCounts() shouldBe mapOf("A" to 3, "B" to 3)
            fm.pairGroups().map { it.size }.sorted() shouldBe listOf(1, 3)
        }

        // The grouping is one pass: a very large bundle must not turn quadratic.
        "pairGroups is linear for a 5000-edge bundle" {
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    repeat(5_000) { useful(from = a, to = b, verb = "v$it") }
                }
            val t0 = System.nanoTime()
            fm.pairGroups().single() shouldHaveSize 5_000
            fm.groupedEdgeCounts() shouldBe mapOf("A" to 5_000, "B" to 5_000)
            ((System.nanoTime() - t0) / 1_000_000) shouldBeLessThan 2_000
        }

        "anchor spacing helper values stay consistent with the sizing rule" {
            // need = m * spacing + 2 * inset must fit the span the anchors are distributed over.
            val m = 12
            val size = measureNodeSize("Engine", m, LayoutDirection.TopToBottom)
            val spans = usableSpans(box(w = size.width), BoxSide.TOP, false)
            val total = spans.sumOf { (it.endInclusive - it.start).toDouble() }
            (total / m) shouldBeGreaterThanOrEqualTo ANCHOR_SPACING_PX.toDouble() - 0.01
            total shouldBeLessThanOrEqualTo size.width.toDouble()
        }
    })
