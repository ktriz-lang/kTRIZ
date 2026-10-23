package dev.ktriz.tests

import dev.ktriz.mcp.RenderOutcome
import dev.ktriz.mcp.guardedRender
import dev.ktriz.mcp.mcpToolCall
import dev.ktriz.mcp.tools.MAX_NAME_LENGTH
import dev.ktriz.mcp.tools.MAX_RENDER_COMPONENTS
import dev.ktriz.mcp.tools.MAX_RENDER_EDGES
import dev.ktriz.mcp.tools.MAX_RENDER_TOTAL_NAME_CHARS
import dev.ktriz.mcp.tools.buildFunctionModelHandler
import dev.ktriz.mcp.tools.lookupStandardSolutionHandler
import dev.ktriz.mcp.tools.renderFunctionModelSvgHandler
import dev.ktriz.mcp.tools.renderSuFieldSvgHandler
import dev.ktriz.sufield.StandardSolution
import dev.ktriz.sufield.StandardSolutionClass
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

private fun CallToolResult.errorKind(): String? = structuredContent?.get("errorKind")?.jsonPrimitive?.content

private fun CallToolResult.structured(): JsonObject = structuredContent ?: error("no structuredContent on $this")

private fun CallToolResult.text(): String = (content.first() as TextContent).text

private fun args(vararg pairs: Pair<String, Any?>): JsonObject =
    buildJsonObject {
        for ((key, value) in pairs) {
            when (value) {
                null -> put(key, JsonNull)
                is Int -> put(key, value)
                is String -> put(key, value)
                is JsonElement -> put(key, value)
                else -> error("unsupported arg type: $value")
            }
        }
    }

private fun edge(
    from: String,
    to: String,
    quality: String,
    verb: String,
): JsonObject =
    buildJsonObject {
        put("from", from)
        put("to", to)
        put("quality", quality)
        put("verb", verb)
    }

private fun edgesArg(vararg edges: JsonObject): JsonElement = buildJsonArray { edges.forEach { add(it) } }

/** Parses [svg] strictly as XML; fails the test on any parse error. */
private fun parseXml(svg: String) =
    DocumentBuilderFactory
        .newInstance()
        .newDocumentBuilder()
        .parse(ByteArrayInputStream(svg.toByteArray(Charsets.UTF_8)))

/** Chain-shaped model with [edgeCount] edges over [nodeCount] components (ring of useful functions). */
private fun ringArgs(
    nodeCount: Int,
    edgeCount: Int,
): JsonObject =
    args(
        "components" to buildJsonArray { repeat(nodeCount) { add(JsonPrimitive("Part $it")) } },
        "edges" to
            buildJsonArray {
                repeat(edgeCount) { i ->
                    add(edge("Part ${i % nodeCount}", "Part ${(i * 7 + 1) % nodeCount}", "useful", "acts $i"))
                }
            },
    )

class KtrizMcpRenderToolsTest :
    StringSpec({
        // -- render_su_field_svg ----------------------------------------------------------

        "render_su_field_svg renders a complete triangle as well-formed SVG with matching structuredContent" {
            val result =
                renderSuFieldSvgHandler(
                    args("s1" to "Werkstück", "s2" to "Laser", "fieldType" to "THERMAL", "quality" to "HARMFUL"),
                )
            result.isError.shouldBeNull()
            val svg = result.text()
            svg shouldContain "<svg"
            parseXml(svg)
            svg shouldContain "Werkstück"
            svg shouldContain "Laser"
            result.structured()["svg"]!!.jsonPrimitive.content shouldBe svg
            result.structured()["format"]!!.jsonPrimitive.content shouldBe "svg"
            result.structured()["quality"]!!.jsonPrimitive.content shouldBe "HARMFUL"
        }

        "render_su_field_svg renders an incomplete triangle with only S1" {
            val result = renderSuFieldSvgHandler(args("s1" to "Workpiece", "quality" to "INCOMPLETE"))
            result.isError.shouldBeNull()
            parseXml(result.text())
            result.structured()["s2"] shouldBe JsonNull
        }

        "render_su_field_svg returns self_contradiction and no SVG for COMPLETE without S2" {
            val result = renderSuFieldSvgHandler(args("s1" to "Workpiece", "quality" to "COMPLETE"))
            result.isError shouldBe true
            result.errorKind() shouldBe "self_contradiction"
            result.structured()["svg"].shouldBeNull()
        }

        "render_su_field_svg rejects missing s1, invalid fieldType and oversized names as malformed_input" {
            renderSuFieldSvgHandler(args("quality" to "INCOMPLETE")).errorKind() shouldBe "malformed_input"
            val badType =
                renderSuFieldSvgHandler(
                    args("s1" to "A", "s2" to "B", "fieldType" to "NOPE_${"x".repeat(300)}", "quality" to "COMPLETE"),
                )
            badType.errorKind() shouldBe "malformed_input"
            badType.text() shouldNotContain "x".repeat(100)
            renderSuFieldSvgHandler(args("s1" to "x".repeat(MAX_NAME_LENGTH + 1), "quality" to "INCOMPLETE"))
                .errorKind() shouldBe "malformed_input"
        }

        "render_su_field_svg escapes hostile names exactly once and stays well-formed XML" {
            val result =
                renderSuFieldSvgHandler(
                    args(
                        "s1" to "<script>&\"'",
                        "s2" to "B\u0001C",
                        "fieldType" to "MECHANICAL",
                        "quality" to "COMPLETE",
                    ),
                )
            result.isError.shouldBeNull()
            val svg = result.text()
            parseXml(svg)
            svg shouldNotContain "<script"
            svg shouldContain "&lt;script&gt;&amp;"
            svg shouldNotContain "&amp;lt;"
        }

        // -- render_function_model_svg ----------------------------------------------------

        "render_function_model_svg renders all four qualities with every verb and counts" {
            val result =
                renderFunctionModelSvgHandler(
                    args(
                        "edges" to
                            edgesArg(
                                edge("Motor", "Welle", "useful", "dreht"),
                                edge("Welle", "Lager", "harmful", "verschleißt"),
                                edge("Lager", "Lager", "excessive", "erhitzt"),
                                edge("Öl", "Lager", "insufficient", "kühlt"),
                            ),
                    ),
                )
            result.isError.shouldBeNull()
            val svg = result.text()
            parseXml(svg)
            listOf("dreht", "verschleißt", "erhitzt", "kühlt", "Motor", "Öl").forEach { svg shouldContain it }
            result.structured()["componentCount"]!!.jsonPrimitive.int shouldBe 4
            result.structured()["edgeCount"]!!.jsonPrimitive.int shouldBe 4
            result.structured()["svg"]!!.jsonPrimitive.content shouldBe svg
        }

        "render_function_model_svg renders an empty model as a minimal valid SVG" {
            val result = renderFunctionModelSvgHandler(args())
            result.isError.shouldBeNull()
            result.text() shouldContain "<svg"
            parseXml(result.text())
        }

        "render_function_model_svg escapes hostile names and verbs exactly once" {
            val result =
                renderFunctionModelSvgHandler(
                    args("edges" to edgesArg(edge("<script>&\"'", "B\u0001", "useful", "a<b&c"))),
                )
            result.isError.shouldBeNull()
            val svg = result.text()
            parseXml(svg)
            svg shouldNotContain "<script"
            svg shouldContain "&lt;script&gt;&amp;"
            svg shouldNotContain "&amp;lt;"
        }

        "render_function_model_svg enforces the tighter component and edge limits" {
            renderFunctionModelSvgHandler(ringArgs(MAX_RENDER_COMPONENTS + 1, 1)).errorKind() shouldBe "malformed_input"
            renderFunctionModelSvgHandler(ringArgs(5, MAX_RENDER_EDGES + 1)).errorKind() shouldBe "malformed_input"
        }

        "render_function_model_svg counts auto-declared edge endpoints against the component limit" {
            // 0 declared components, but 31 edges with all-distinct endpoints = 62 nodes.
            val edges =
                buildJsonArray {
                    repeat(31) { add(edge("From $it", "To $it", "useful", "v")) }
                }
            val result = renderFunctionModelSvgHandler(args("edges" to edges))
            result.errorKind() shouldBe "malformed_input"
            result.text() shouldContain "components"
        }

        "render_function_model_svg limits the summed length of names and verbs" {
            val longName = "n".repeat(MAX_NAME_LENGTH)
            val count = MAX_RENDER_TOTAL_NAME_CHARS / MAX_NAME_LENGTH + 2
            val edges = buildJsonArray { repeat(count) { add(edge("$longName$it", "short", "useful", "v")) } }
            renderFunctionModelSvgHandler(args("edges" to edges)).errorKind() shouldBe "malformed_input"
        }

        "build_function_model still accepts up to 200 components (its limits are unchanged)" {
            val result = buildFunctionModelHandler(ringArgs(200, 0))
            result.isError.shouldBeNull()
            result.structured()["components"]!!.jsonArray shouldHaveSize 200
        }

        "render_function_model_svg at the maximum size finishes well inside the render timeout" {
            val started = System.nanoTime()
            val result = renderFunctionModelSvgHandler(ringArgs(MAX_RENDER_COMPONENTS, MAX_RENDER_EDGES))
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            println("render_function_model_svg worst case ($MAX_RENDER_COMPONENTS/$MAX_RENDER_EDGES): ${elapsedMs}ms")
            result.isError.shouldBeNull()
            parseXml(result.text())
            elapsedMs shouldBeLessThan 6_000L
        }

        // -- RenderGuard ------------------------------------------------------------------

        "guardedRender returns TimedOut at the deadline instead of waiting for the worker" {
            val started = System.nanoTime()
            val outcome =
                guardedRender(timeoutMs = 100) {
                    Thread.sleep(1_500)
                    "x"
                }
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            outcome shouldBe RenderOutcome.TimedOut
            elapsedMs shouldBeLessThan 1_000L
        }

        "guardedRender reports Busy when all worker permits are held, and recovers afterwards" {
            val release = CountDownLatch(1)
            val started = CountDownLatch(2)
            val blockers =
                List(2) {
                    Thread {
                        guardedRender(timeoutMs = 5_000) {
                            started.countDown()
                            release.await(5, TimeUnit.SECONDS)
                            "done"
                        }
                    }.apply { start() }
                }
            started.await(5, TimeUnit.SECONDS) shouldBe true
            guardedRender(timeoutMs = 100) { "never runs" } shouldBe RenderOutcome.Busy
            release.countDown()
            blockers.forEach { it.join(5_000) }
            // permits are released by the workers; allow a moment for the last finally block
            var outcome: RenderOutcome = RenderOutcome.Busy
            repeat(50) {
                if (outcome == RenderOutcome.Busy) {
                    outcome = guardedRender(timeoutMs = 1_000) { "ok" }
                    if (outcome == RenderOutcome.Busy) Thread.sleep(20)
                }
            }
            outcome shouldBe RenderOutcome.Ok("ok")
        }

        "a hung render keeps its permit until the worker really ends" {
            val release = CountDownLatch(1)
            val started = CountDownLatch(1)
            guardedRender(timeoutMs = 100) {
                started.countDown()
                // ignores interrupts on purpose: simulates an ELK run that cannot be cancelled
                var done = false
                while (!done) {
                    try {
                        release.await()
                        done = true
                    } catch (_: InterruptedException) {
                        // keep going
                    }
                }
                "late"
            } shouldBe RenderOutcome.TimedOut
            started.await(2, TimeUnit.SECONDS) shouldBe true
            // one permit is still held by the hung worker; a second render still fits
            guardedRender(timeoutMs = 1_000) { "second" } shouldBe RenderOutcome.Ok("second")
            release.countDown()
        }

        "guardedRender runs on a named daemon thread" {
            var daemon = false
            var name = ""
            guardedRender(timeoutMs = 1_000) {
                daemon = Thread.currentThread().isDaemon
                name = Thread.currentThread().name
                "ok"
            }
            daemon shouldBe true
            name shouldStartWith "ktriz-mcp-render-"
        }

        "an exception inside the render surfaces as internal_error with the class name only" {
            val result =
                mcpToolCall("test_tool") {
                    guardedRender { throw IllegalStateException("secret internal detail") }
                    error("unreachable")
                }
            result.isError shouldBe true
            result.errorKind() shouldBe "internal_error"
            result.text() shouldContain "IllegalStateException"
            result.text() shouldNotContain "secret internal detail"
        }

        // -- lookup_standard_solution -----------------------------------------------------

        "lookup_standard_solution finds one solution by code, including the 5.1.1 anomaly entry" {
            val first = lookupStandardSolutionHandler(args("code" to "1.1.1"))
            first.isError.shouldBeNull()
            val solution = first.structured()["solution"]!!.jsonObject
            solution["symbol"]!!.jsonPrimitive.content shouldBe StandardSolution.COMPLETE_INCOMPLETE_MODEL.name
            solution["standardClass"]!!.jsonObject["id"]!!.jsonPrimitive.int shouldBe 1

            val anomaly = lookupStandardSolutionHandler(args("code" to " 5.1.1 "))
            anomaly.isError.shouldBeNull()
            anomaly
                .structured()["solution"]!!
                .jsonObject["code"]!!
                .jsonPrimitive.content shouldBe "5.1.1"
        }

        "lookup_standard_solution by class returns that class's solutions and all five sum to 76" {
            var total = 0
            for (c in StandardSolutionClass.entries) {
                val byInt = lookupStandardSolutionHandler(args("standardClass" to c.id))
                val byString = lookupStandardSolutionHandler(args("standardClass" to c.id.toString()))
                val bySymbol = lookupStandardSolutionHandler(args("standardClass" to c.name.lowercase()))
                val count = byInt.structured()["count"]!!.jsonPrimitive.int
                count shouldBe c.solutionCount
                byString.structured()["count"]!!.jsonPrimitive.int shouldBe count
                bySymbol.structured()["count"]!!.jsonPrimitive.int shouldBe count
                total += count
            }
            total shouldBe 76
            StandardSolutionClass.entries.map { it.solutionCount }.sum() shouldBe 76
        }

        "lookup_standard_solution without arguments returns all 76 solutions" {
            val result = lookupStandardSolutionHandler(null)
            result.isError.shouldBeNull()
            result.structured()["count"]!!.jsonPrimitive.int shouldBe 76
            result.structured()["solutions"]!!.jsonArray shouldHaveSize 76
        }

        "lookup_standard_solution reports an unknown code with all valid codes and a truncated echo" {
            val result = lookupStandardSolutionHandler(args("code" to "9.9.9"))
            result.isError shouldBe true
            result.errorKind() shouldBe "unknown_standard_solution"
            val codes = result.structured()["validCodes"]!!.jsonArray.map { it.jsonPrimitive.content }
            codes shouldHaveSize 76
            codes shouldContain "1.1.1"

            val long = lookupStandardSolutionHandler(args("code" to "z".repeat(300)))
            long.errorKind() shouldBe "unknown_standard_solution"
            long.text() shouldNotContain "z".repeat(100)
        }

        "lookup_standard_solution rejects an oversized code, mutually exclusive args and bad shapes" {
            val huge = lookupStandardSolutionHandler(args("code" to "z".repeat(600)))
            huge.errorKind() shouldBe "malformed_input"
            huge.text() shouldNotContain "z".repeat(100)

            lookupStandardSolutionHandler(args("code" to "1.1.1", "standardClass" to 1))
                .errorKind() shouldBe "malformed_input"
            lookupStandardSolutionHandler(args("code" to 111)).errorKind() shouldBe "malformed_input"
            for (bad in listOf<Any>(0, 6, "FOO")) {
                val r = lookupStandardSolutionHandler(args("standardClass" to bad))
                r.errorKind() shouldBe "malformed_input"
                r.text() shouldContain "APPLICATION_STRATEGIES"
            }
        }

        "lookup_standard_solution keeps natural code order (2.4.9 before 2.4.10)" {
            val codes =
                lookupStandardSolutionHandler(args("standardClass" to 2))
                    .structured()["solutions"]!!
                    .jsonArray
                    .map { it.jsonObject["code"]!!.jsonPrimitive.content }
            (codes.indexOf("2.4.9") >= 0) shouldBe true
            (codes.indexOf("2.4.9") < codes.indexOf("2.4.10")) shouldBe true
            (codes.indexOf("2.4.10") < codes.indexOf("2.4.11")) shouldBe true
            codes shouldBe StandardSolution.entries.filter { it.standardClass.id == 2 }.map { it.code }
        }

        "render tools return render_output_too_large when the SVG exceeds the limit" {
            val su = renderSuFieldSvgHandler(args("s1" to "A", "quality" to "INCOMPLETE"), maxSvgChars = 10)
            su.errorKind() shouldBe "render_output_too_large"
            val fm =
                renderFunctionModelSvgHandler(
                    args("components" to buildJsonArray { add(JsonPrimitive("A")) }),
                    maxSvgChars = 10,
                )
            fm.errorKind() shouldBe "render_output_too_large"
        }
    })
