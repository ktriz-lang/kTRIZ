package dev.ktriz.tests

import dev.ktriz.mcp.tools.buildPhysicalContradictionHandler
import dev.ktriz.mcp.tools.listSeparationPrinciplesHandler
import dev.ktriz.physical.MAX_PHYSICAL_REASON_LENGTH
import dev.ktriz.physical.MAX_PHYSICAL_TEXT_LENGTH
import dev.ktriz.script.KtrizScriptHost
import dev.ktriz.script.KtrizScriptOutcome
import io.kotest.core.annotation.Isolate
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private fun CallToolResult.errorKind(): String? = structuredContent?.get("errorKind")?.jsonPrimitive?.content

private fun CallToolResult.structured(): JsonObject = structuredContent ?: error("no structuredContent on $this")

private fun baseArgs(vararg overrides: Pair<String, JsonElement?>): JsonObject {
    val map =
        linkedMapOf<String, JsonElement?>(
            "element" to JsonPrimitive("Landing gear"),
            "property" to JsonPrimitive("Presence"),
            "state" to JsonPrimitive("present"),
            "oppositeState" to JsonPrimitive("absent"),
        )
    overrides.forEach { (k, v) -> map[k] = v }
    return buildJsonObject {
        for ((k, v) in map) if (v != null) put(k, v)
    }
}

private fun s(v: String) = JsonPrimitive(v)

private fun evalSnippet(snippet: String): KtrizScriptOutcome = KtrizScriptHost.evalSource(snippet)

// @Isolate: the stdout test swaps System.out and the roundtrip tests compile scripts.
@Isolate
class KtrizMcpPhysicalToolsTest :
    StringSpec({
        "happy path with all fields" {
            val r =
                buildPhysicalContradictionHandler(
                    baseArgs(
                        "stateBecause" to s("landing"),
                        "oppositeStateBecause" to s("drag"),
                        "separation" to s("IN_TIME"),
                    ),
                )
            r.isError.shouldBeNull()
            val st = r.structured()
            st["element"]!!.jsonObject["name"]!!.jsonPrimitive.content shouldBe "Landing gear"
            st["stateBecause"]!!.jsonPrimitive.content shouldBe "landing"
            st["separation"]!!.jsonObject["symbol"]!!.jsonPrimitive.content shouldBe "IN_TIME"
            st["kotlin"]!!.jsonPrimitive.content shouldContain "because = \"landing\""
        }

        "happy path with required fields only yields nulls" {
            val st = buildPhysicalContradictionHandler(baseArgs()).structured()
            st["stateBecause"] shouldBe JsonNull
            st["oppositeStateBecause"] shouldBe JsonNull
            st["separation"] shouldBe JsonNull
        }

        "separation is case-insensitive" {
            val st = buildPhysicalContradictionHandler(baseArgs("separation" to s("upon_condition"))).structured()
            st["separation"]!!.jsonObject["symbol"]!!.jsonPrimitive.content shouldBe "UPON_CONDITION"
        }

        "each missing required field yields malformed_input" {
            listOf("element", "property", "state", "oppositeState").forEach { f ->
                val r = buildPhysicalContradictionHandler(baseArgs(f to null))
                r.errorKind() shouldBe "malformed_input"
            }
        }

        "blank values are rejected" {
            val fields = listOf("element", "property", "state", "oppositeState", "stateBecause", "oppositeStateBecause")
            fields.forEach { f ->
                buildPhysicalContradictionHandler(baseArgs(f to s("  "))).errorKind() shouldBe "malformed_input"
            }
        }

        "over-long values are rejected without echoing them" {
            val long = "Z".repeat(MAX_PHYSICAL_TEXT_LENGTH + 1)
            listOf("element", "property", "state", "oppositeState").forEach { f ->
                val r = buildPhysicalContradictionHandler(baseArgs(f to s(long)))
                r.errorKind() shouldBe "malformed_input"
                r.structured().toString() shouldNotContain long
            }
            val longReason = "Q".repeat(MAX_PHYSICAL_REASON_LENGTH + 1)
            listOf("stateBecause", "oppositeStateBecause").forEach { f ->
                val r = buildPhysicalContradictionHandler(baseArgs(f to s(longReason)))
                r.errorKind() shouldBe "malformed_input"
                r.structured().toString() shouldNotContain longReason
            }
            // boundary values are accepted
            buildPhysicalContradictionHandler(
                baseArgs(
                    "element" to s("a".repeat(MAX_PHYSICAL_TEXT_LENGTH)),
                    "stateBecause" to s("a".repeat(MAX_PHYSICAL_REASON_LENGTH)),
                ),
            ).isError.shouldBeNull()
        }

        "wrong JSON shapes yield malformed_input, never an exception" {
            val shapes: List<JsonElement> =
                listOf(JsonPrimitive(42), JsonPrimitive(true), JsonArray(emptyList()), JsonObject(emptyMap()))
            listOf("element", "property", "state", "oppositeState").forEach { f ->
                shapes.forEach { shape ->
                    buildPhysicalContradictionHandler(baseArgs(f to shape)).errorKind() shouldBe "malformed_input"
                }
                buildPhysicalContradictionHandler(baseArgs(f to JsonNull)).errorKind() shouldBe "malformed_input"
            }
        }

        "unknown separation lists the valid values and truncates a long raw value" {
            val r = buildPhysicalContradictionHandler(baseArgs("separation" to s("IN_ORBIT")))
            r.errorKind() shouldBe "malformed_input"
            val msg = r.structured()["message"]!!.jsonPrimitive.content
            listOf("IN_SPACE", "IN_TIME", "UPON_CONDITION", "ON_SYSTEM_LEVEL").forEach { msg shouldContain it }
            val long = "Y".repeat(150)
            val r2 = buildPhysicalContradictionHandler(baseArgs("separation" to s(long)))
            r2.structured()["message"]!!.jsonPrimitive.content shouldNotContain long
        }

        "equal states yield self_contradiction" {
            buildPhysicalContradictionHandler(baseArgs("oppositeState" to s("PRESENT "))).errorKind() shouldBe
                "self_contradiction"
        }

        "special characters are escaped in every string field and the snippet round-trips through the compiler" {
            val nasty = "a\$b \"q\" \\ x\ny\tz\rw"
            val cases =
                listOf(
                    baseArgs(),
                    baseArgs("element" to s("object")),
                    baseArgs("element" to s("Object"), "property" to s("\${total}")),
                    baseArgs(
                        "element" to s(nasty),
                        "property" to s(nasty + "1"),
                        "state" to s(nasty + "2"),
                        "oppositeState" to s(nasty + "3"),
                        "stateBecause" to s(nasty + "4"),
                        "oppositeStateBecause" to s(nasty + "5"),
                        "separation" to s("ON_SYSTEM_LEVEL"),
                    ),
                )
            cases.forEach { a ->
                val st = buildPhysicalContradictionHandler(a).structured()
                val snippet = st["kotlin"]!!.jsonPrimitive.content
                val outcome = evalSnippet(snippet)
                outcome.shouldBeInstanceOf<KtrizScriptOutcome.Success>()
                val model = outcome.returnValue
                model.shouldBeInstanceOf<dev.ktriz.physical.PhysicalContradiction>()
                model.element.name shouldBe a["element"]!!.jsonPrimitive.content
                model.property shouldBe a["property"]!!.jsonPrimitive.content
                model.state shouldBe a["state"]!!.jsonPrimitive.content
                model.oppositeState shouldBe a["oppositeState"]!!.jsonPrimitive.content
                model.stateBecause shouldBe a["stateBecause"]?.jsonPrimitive?.content
                model.oppositeStateBecause shouldBe a["oppositeStateBecause"]?.jsonPrimitive?.content
                model.separation?.name shouldBe a["separation"]?.jsonPrimitive?.content
            }
        }

        "both handlers write nothing to stdout" {
            val original = System.out
            val captured = java.io.ByteArrayOutputStream()
            System.setOut(java.io.PrintStream(captured, true, Charsets.UTF_8))
            try {
                buildPhysicalContradictionHandler(baseArgs())
                buildPhysicalContradictionHandler(baseArgs("oppositeState" to s("present")))
                listSeparationPrinciplesHandler(null)
            } finally {
                System.setOut(original)
            }
            captured.toString(Charsets.UTF_8) shouldBe ""
        }

        "list_separation_principles returns four entries and ignores unknown arguments" {
            val r = listSeparationPrinciplesHandler(buildJsonObject { put("bogus", "x") })
            r.isError.shouldBeNull()
            val st = r.structured()
            st["count"]!!.jsonPrimitive.int shouldBe 4
            st["separationPrinciples"]!!.jsonArray.map { it.jsonObject["symbol"]!!.jsonPrimitive.content } shouldBe
                listOf("IN_SPACE", "IN_TIME", "UPON_CONDITION", "ON_SYSTEM_LEVEL")
        }
    })
