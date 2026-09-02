package dev.ktriz.tests

import dev.ktriz.core.EngineeringParameter
import dev.ktriz.core.InventivePrinciple
import dev.ktriz.mcp.tools.MAX_NAME_LENGTH
import dev.ktriz.mcp.tools.buildFunctionModelHandler
import dev.ktriz.mcp.tools.listEngineeringParametersHandler
import dev.ktriz.mcp.tools.listInventivePrinciplesHandler
import dev.ktriz.mcp.tools.resolveContradictionHandler
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotContain
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private fun CallToolResult.errorKind(): String? = structuredContent?.get("errorKind")?.jsonPrimitive?.content

private fun CallToolResult.structured(): JsonObject = structuredContent ?: error("no structuredContent on $this")

private fun args(vararg pairs: Pair<String, Any?>): JsonObject =
    buildJsonObject {
        for ((key, value) in pairs) {
            when (value) {
                null -> put(key, JsonNull)
                is Int -> put(key, value)
                is Double -> put(key, value)
                is Boolean -> put(key, value)
                is String -> put(key, value)
                is JsonElement -> put(key, value)
                else -> error("unsupported arg type: $value")
            }
        }
    }

class KtrizMcpToolHandlersTest :
    StringSpec({
        // -- list_engineering_parameters --------------------------------------------------

        "list_engineering_parameters returns exactly the 39 parameters with unique, complete ids" {
            val result = listEngineeringParametersHandler(null)
            result.isError.shouldBeNull()
            val structured = result.structured()
            structured["count"]!!.jsonPrimitive.int shouldBe 39
            val ids = structured["parameters"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.int }
            ids.toSet() shouldHaveSize 39
            ids.sorted() shouldContainExactly (1..39).toList()
        }

        "list_engineering_parameters' symbol field is exactly the Kotlin enum name" {
            val structured = listEngineeringParametersHandler(null).structured()
            structured["parameters"]!!.jsonArray.forAll { entry ->
                val obj = entry.jsonObject
                val id = obj["id"]!!.jsonPrimitive.int
                val symbol = obj["symbol"]!!.jsonPrimitive.content
                symbol shouldBe EngineeringParameter.ofId(id).name
            }
        }

        "list_engineering_parameters ignores unrecognized arguments instead of rejecting them" {
            val result = listEngineeringParametersHandler(args("bogus" to "value"))
            result.isError.shouldBeNull()
        }

        // -- list_inventive_principles ---------------------------------------------------

        "list_inventive_principles returns exactly the 40 principles with unique, complete ids" {
            val result = listInventivePrinciplesHandler(null)
            result.isError.shouldBeNull()
            val structured = result.structured()
            structured["count"]!!.jsonPrimitive.int shouldBe 40
            val ids = structured["principles"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.int }
            ids.toSet() shouldHaveSize 40
            ids.sorted() shouldContainExactly (1..40).toList()
        }

        "list_inventive_principles' symbol field is exactly the Kotlin enum name" {
            val structured = listInventivePrinciplesHandler(null).structured()
            structured["principles"]!!.jsonArray.forAll { entry ->
                val obj = entry.jsonObject
                val id = obj["id"]!!.jsonPrimitive.int
                val symbol = obj["symbol"]!!.jsonPrimitive.content
                symbol shouldBe InventivePrinciple.ofId(id).name
            }
        }

        // -- resolve_contradiction: happy path --------------------------------------------

        "resolve_contradiction with symbols returns the classical rank-ordered recommendation" {
            val result =
                resolveContradictionHandler(
                    args(
                        "improving" to "WEIGHT_OF_MOVING_OBJECT",
                        "worsening" to "STRENGTH",
                    ),
                )
            result.isError.shouldBeNull()
            val ids = result.structured()["principles"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.int }
            ids shouldContainExactly listOf(28, 27, 18, 40)
        }

        "resolve_contradiction with numeric ids matches the symbol lookup" {
            val result = resolveContradictionHandler(args("improving" to 1, "worsening" to 14))
            val ids = result.structured()["principles"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.int }
            ids shouldContainExactly listOf(28, 27, 18, 40)
        }

        "resolve_contradiction with numeric-string ids matches the symbol lookup" {
            val result = resolveContradictionHandler(args("improving" to "1", "worsening" to "14"))
            val ids = result.structured()["principles"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.int }
            ids shouldContainExactly listOf(28, 27, 18, 40)
        }

        "resolve_contradiction with English labels matches the symbol lookup" {
            val result =
                resolveContradictionHandler(
                    args("improving" to "Weight of moving object", "worsening" to "Strength"),
                )
            val ids = result.structured()["principles"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.int }
            ids shouldContainExactly listOf(28, 27, 18, 40)
        }

        "resolve_contradiction with German labels matches the symbol lookup" {
            val result =
                resolveContradictionHandler(
                    args("improving" to "Gewicht eines beweglichen Objekts", "worsening" to "Festigkeit"),
                )
            val ids = result.structured()["principles"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.int }
            ids shouldContainExactly listOf(28, 27, 18, 40)
        }

        "resolve_contradiction is case/whitespace/hyphen-insensitive" {
            val result =
                resolveContradictionHandler(
                    args("improving" to "  weight of moving-object ", "worsening" to "STRENGTH"),
                )
            result.isError.shouldBeNull()
            val ids = result.structured()["principles"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.int }
            ids shouldContainExactly listOf(28, 27, 18, 40)
        }

        "resolve_contradiction resolves an apostrophe label to its symbol" {
            val result =
                resolveContradictionHandler(
                    args("improving" to "Stability of the object's composition", "worsening" to "STRENGTH"),
                )
            result.isError.shouldBeNull()
            result
                .structured()["improving"]!!
                .jsonObject["symbol"]!!
                .jsonPrimitive.content shouldBe
                "STABILITY_OF_THE_OBJECTS_COMPOSITION"
        }

        "resolve_contradiction direction matters: swapping poles yields a different result" {
            val forward = resolveContradictionHandler(args("improving" to 1, "worsening" to 14))
            val backward = resolveContradictionHandler(args("improving" to 14, "worsening" to 1))
            val forwardIds =
                forward.structured()["principles"]!!.jsonArray.map {
                    it.jsonObject["id"]!!
                        .jsonPrimitive.int
                }
            val backwardIds =
                backward.structured()["principles"]!!.jsonArray.map {
                    it.jsonObject["id"]!!
                        .jsonPrimitive.int
                }
            forwardIds shouldNotBe backwardIds
        }

        "resolve_contradiction's kotlin field is a directly usable contradiction(...) snippet" {
            // The other snippet-producing tool covered by OF-5: unlike build_function_model's,
            // this snippet interpolates only EngineeringParameter enum symbols (always valid
            // Kotlin identifiers by construction), so there is no escaping bug to pin here -- but
            // the field itself had zero coverage before, so a future refactor could silently break
            // its shape without any test going red.
            val result =
                resolveContradictionHandler(
                    args(
                        "improving" to "WEIGHT_OF_MOVING_OBJECT",
                        "worsening" to "STRENGTH",
                    ),
                )
            result.isError.shouldBeNull()
            val kotlin = result.structured()["kotlin"]!!.jsonPrimitive.content
            kotlin shouldContain "contradiction("
            kotlin shouldContain "improving = EngineeringParameter.WEIGHT_OF_MOVING_OBJECT"
            kotlin shouldContain "worsening = EngineeringParameter.STRENGTH"
            kotlin.trim() shouldEndWith ")"
        }

        "resolve_contradiction on an unpopulated cell is a valid empty result, not an error" {
            val result = resolveContradictionHandler(args("improving" to 1, "worsening" to 2))
            result.isError.shouldBeNull()
            val structured = result.structured()
            structured["empty"]!!.jsonPrimitive.boolean shouldBe true
            structured["principleCount"]!!.jsonPrimitive.int shouldBe 0
            structured["principles"]!!.jsonArray.shouldBeEmpty()
        }

        // -- resolve_contradiction: error paths --------------------------------------------

        "resolve_contradiction with an invented symbol returns unknown_parameter naming the field and hint" {
            val result =
                resolveContradictionHandler(
                    args(
                        "improving" to "STRENGTH",
                        "worsening" to "STRUCTURAL_INTEGRITY",
                    ),
                )
            result.isError shouldBe true
            result.errorKind() shouldBe "unknown_parameter"
            val errorEntry =
                result
                    .structured()["errors"]!!
                    .jsonArray
                    .single()
                    .jsonObject
            errorEntry["field"]!!.jsonPrimitive.content shouldBe "worsening"
            errorEntry["value"]!!.jsonPrimitive.content shouldBe "STRUCTURAL_INTEGRITY"
            errorEntry["hint"]!!.jsonPrimitive.content shouldContain "list_engineering_parameters"
            (errorEntry["suggestions"]!!.jsonArray.size <= 5) shouldBe true
        }

        "resolve_contradiction with a near-miss typo of a real symbol suggests that symbol" {
            // "STRENGHT" is STRENGTH with its last two letters transposed (Levenshtein distance 2).
            val result = resolveContradictionHandler(args("improving" to "STRENGHT", "worsening" to "SPEED"))
            result.isError shouldBe true
            result.errorKind() shouldBe "unknown_parameter"
            val errorEntry =
                result
                    .structured()["errors"]!!
                    .jsonArray
                    .single()
                    .jsonObject
            val suggestions = errorEntry["suggestions"]!!.jsonArray
            suggestions.shouldNotBeEmpty()
            (suggestions.size <= 5) shouldBe true
            suggestions.map { it.jsonObject["symbol"]!!.jsonPrimitive.content } shouldContain "STRENGTH"
        }

        "resolve_contradiction with an out-of-range id returns unknown_parameter for every boundary case" {
            listOf(0, 40, 99, -1).forAll { badId ->
                val result = resolveContradictionHandler(args("improving" to badId, "worsening" to 14))
                result.isError shouldBe true
                result.errorKind() shouldBe "unknown_parameter"
            }
        }

        "resolve_contradiction rejects a self-contradiction using ktriz-core's own message" {
            val result = resolveContradictionHandler(args("improving" to "STRENGTH", "worsening" to "STRENGTH"))
            result.isError shouldBe true
            result.errorKind() shouldBe "self_contradiction"
            val text = (result.content.first() as TextContent).text
            text shouldContain "cannot contradict itself"
            text shouldContain "Strength"
        }

        "resolve_contradiction with a missing field returns malformed_input naming the field" {
            val result = resolveContradictionHandler(args("improving" to "STRENGTH"))
            result.isError shouldBe true
            result.errorKind() shouldBe "malformed_input"
            val errorEntry =
                result
                    .structured()["errors"]!!
                    .jsonArray
                    .single()
                    .jsonObject
            errorEntry["field"]!!.jsonPrimitive.content shouldBe "worsening"
        }

        "resolve_contradiction rejects every wrong JSON shape as malformed_input, never throwing" {
            val badValues: List<Any?> =
                listOf(true, JsonNull, buildJsonArray { add(1) }, buildJsonObject { put("x", 1) })
            badValues.forAll { bad ->
                val callArgs: JsonObject =
                    when (bad) {
                        is JsonNull -> args("improving" to "STRENGTH", "worsening" to null)
                        is JsonArray -> args("improving" to "STRENGTH", "worsening" to bad)
                        is JsonObject -> args("improving" to "STRENGTH", "worsening" to bad)
                        is Boolean -> args("improving" to "STRENGTH", "worsening" to bad)
                        else -> error("unreachable")
                    }
                val result = resolveContradictionHandler(callArgs)
                result.isError shouldBe true
                result.errorKind() shouldBe "malformed_input"
            }
        }

        "resolve_contradiction rejects an oversized string field, without echoing it back in full" {
            val huge = "x".repeat(600)
            val result = resolveContradictionHandler(args("improving" to "STRENGTH", "worsening" to huge))
            result.isError shouldBe true
            result.errorKind() shouldBe "malformed_input"
            val text = (result.content.first() as TextContent).text
            text shouldNotContain huge
        }

        "resolve_contradiction rejects a non-integer number as malformed_input" {
            val result =
                resolveContradictionHandler(
                    buildJsonObject {
                        put("improving", 3.7)
                        put("worsening", "STRENGTH")
                    },
                )
            result.isError shouldBe true
            result.errorKind() shouldBe "malformed_input"
        }

        "resolve_contradiction never throws across a sweep of malformed inputs" {
            val calls: List<() -> CallToolResult> =
                listOf(
                    { resolveContradictionHandler(null) },
                    { resolveContradictionHandler(buildJsonObject { }) },
                    { resolveContradictionHandler(args("improving" to true, "worsening" to "STRENGTH")) },
                    { resolveContradictionHandler(args("improving" to "STRENGTH", "worsening" to 0)) },
                    { resolveContradictionHandler(args("improving" to "nope", "worsening" to "nope-either")) },
                )
            calls.forAll { call ->
                val result = call()
                result.isError shouldBe true
            }
        }

        // -- build_function_model ----------------------------------------------------------

        "build_function_model builds a normalized model with all four qualities" {
            val edges =
                buildJsonArray {
                    add(edgeJson("Coolant", "Engine", "useful", "cools"))
                    add(edgeJson("Engine", "Block", "harmful", "overheats"))
                    add(edgeJson("Coolant", "Engine", "insufficient", "circulates"))
                    add(edgeJson("Engine", "Block", "EXCESSIVE", "vibrates"))
                }
            val result =
                buildFunctionModelHandler(
                    buildJsonObject {
                        put(
                            "components",
                            buildJsonArray {
                                add("Coolant")
                                add("Engine")
                                add("Block")
                            },
                        )
                        put("edges", edges)
                    },
                )
            result.isError.shouldBeNull()
            val structured = result.structured()
            val componentNames =
                structured["components"]!!.jsonArray.map {
                    it.jsonObject["name"]!!
                        .jsonPrimitive.content
                }
            componentNames shouldContainExactly listOf("Coolant", "Engine", "Block")
            structured["edges"]!!.jsonArray shouldHaveSize 4
        }

        "build_function_model auto-declares an edge endpoint absent from components" {
            val result =
                buildFunctionModelHandler(
                    buildJsonObject {
                        put("edges", buildJsonArray { add(edgeJson("A", "B", "useful", "drives")) })
                    },
                )
            result.isError.shouldBeNull()
            val componentNames =
                result.structured()["components"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
            componentNames shouldContainExactly listOf("A", "B")
        }

        "build_function_model dedups a component named twice, keeping first-seen order" {
            val result =
                buildFunctionModelHandler(
                    buildJsonObject {
                        put(
                            "components",
                            buildJsonArray {
                                add("A")
                                add("B")
                                add("A")
                            },
                        )
                    },
                )
            val componentNames =
                result.structured()["components"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
            componentNames shouldContainExactly listOf("A", "B")
        }

        "build_function_model rejects an invalid quality, listing the valid values" {
            val result =
                buildFunctionModelHandler(
                    buildJsonObject {
                        put("edges", buildJsonArray { add(edgeJson("A", "B", "MAGICAL", "drives")) })
                    },
                )
            result.isError shouldBe true
            result.errorKind() shouldBe "malformed_input"
            val text = (result.content.first() as TextContent).text
            listOf("USEFUL", "HARMFUL", "INSUFFICIENT", "EXCESSIVE").forAll { text shouldContain it }
        }

        "build_function_model rejects more than MAX_COMPONENTS components" {
            val tooMany = buildJsonArray { repeat(201) { add("C$it") } }
            val result = buildFunctionModelHandler(buildJsonObject { put("components", tooMany) })
            result.isError shouldBe true
            result.errorKind() shouldBe "malformed_input"
        }

        "build_function_model rejects more than MAX_EDGES edges" {
            val tooMany = buildJsonArray { repeat(501) { add(edgeJson("A", "B", "useful", "drives")) } }
            val result = buildFunctionModelHandler(buildJsonObject { put("edges", tooMany) })
            result.isError shouldBe true
            result.errorKind() shouldBe "malformed_input"
        }

        "build_function_model accepts an empty model as valid, not an error" {
            val result = buildFunctionModelHandler(buildJsonObject { })
            result.isError.shouldBeNull()
            result.structured()["components"]!!.jsonArray.shouldBeEmpty()
            result.structured()["edges"]!!.jsonArray.shouldBeEmpty()
        }

        // -- build_function_model: the `kotlin` field itself (OF-2, OF-3) --------------------

        "build_function_model's kotlin field is directly compilable for a component name containing a dollar sign" {
            // OF-2: an un-escaped `$` in a generated string literal turns into a Kotlin string
            // template -- either a compile error ("Unresolved reference") or, worse, a silent
            // wrong-value interpolation. This is exactly the tool's own selling point breaking.
            val result =
                buildFunctionModelHandler(
                    buildJsonObject { put("components", buildJsonArray { add("Cost \$total") }) },
                )
            result.isError.shouldBeNull()
            val kotlin = result.structured()["kotlin"]!!.jsonPrimitive.content
            kotlin shouldContain "component(\"Cost \\\$total\")"
            kotlin shouldNotContain "component(\"Cost \$total\")"
        }

        "build_function_model's kotlin field escapes an embedded newline, not a raw line break" {
            // OF-2: a raw newline inside a string literal does not compile ("unterminated
            // literal"); the previous implementation only escaped backslash and double-quote.
            val result =
                buildFunctionModelHandler(
                    buildJsonObject { put("components", buildJsonArray { add("Line1\nLine2") }) },
                )
            result.isError.shouldBeNull()
            val kotlin = result.structured()["kotlin"]!!.jsonPrimitive.content
            kotlin shouldContain "component(\"Line1\\nLine2\")"
            // Every generated identifier/component line is exactly one line; only the surrounding
            // structural newlines (between "functionModel {", the component lines, and "}") remain.
            kotlin.lines() shouldHaveSize 3
        }

        "build_function_model's kotlin field generates a non-keyword identifier for a component named 'Object'" {
            // OF-3: `object` is a Kotlin hard keyword -- `val object = component(...)` does not
            // parse ("Expecting property name"), yet "Object" is an everyday name in classical
            // TRIZ function analysis (the passive party in an object/tool relationship).
            val result =
                buildFunctionModelHandler(
                    buildJsonObject { put("components", buildJsonArray { add("Object") }) },
                )
            result.isError.shouldBeNull()
            val kotlin = result.structured()["kotlin"]!!.jsonPrimitive.content
            kotlin shouldNotContain "val object ="
            kotlin shouldContain "val object_ = component(\"Object\")"
        }

        "build_function_model's kotlin field round-trips a full model into a plausible functionModel snippet" {
            val result =
                buildFunctionModelHandler(
                    buildJsonObject {
                        put(
                            "edges",
                            buildJsonArray { add(edgeJson("Coolant pump", "Engine", "useful", "cools")) },
                        )
                    },
                )
            result.isError.shouldBeNull()
            val kotlin = result.structured()["kotlin"]!!.jsonPrimitive.content
            kotlin shouldContain "functionModel {"
            kotlin shouldContain "val coolantPump = component(\"Coolant pump\")"
            kotlin shouldContain "val engine = component(\"Engine\")"
            kotlin shouldContain "useful(from = coolantPump, to = engine, verb = \"cools\")"
            kotlin.trim() shouldEndWith "}"
        }

        // -- build_function_model: additional malformed_input paths (OF-5 test-coverage gap) ---

        "build_function_model rejects a component name over MAX_NAME_LENGTH" {
            val tooLong = "x".repeat(MAX_NAME_LENGTH + 1)
            val result =
                buildFunctionModelHandler(buildJsonObject { put("components", buildJsonArray { add(tooLong) }) })
            result.isError shouldBe true
            result.errorKind() shouldBe "malformed_input"
        }

        "build_function_model rejects an edge field over MAX_NAME_LENGTH" {
            val tooLong = "x".repeat(MAX_NAME_LENGTH + 1)
            val result =
                buildFunctionModelHandler(
                    buildJsonObject {
                        put(
                            "edges",
                            buildJsonArray { add(edgeJson(tooLong, "B", "useful", "drives")) },
                        )
                    },
                )
            result.isError shouldBe true
            result.errorKind() shouldBe "malformed_input"
        }

        "build_function_model rejects 'edges' when it is not an array" {
            val result = buildFunctionModelHandler(buildJsonObject { put("edges", "not-an-array") })
            result.isError shouldBe true
            result.errorKind() shouldBe "malformed_input"
        }

        "build_function_model rejects an edges entry that is not an object" {
            val result =
                buildFunctionModelHandler(buildJsonObject { put("edges", buildJsonArray { add("not-an-object") }) })
            result.isError shouldBe true
            result.errorKind() shouldBe "malformed_input"
        }

        "build_function_model rejects an edge object missing 'from'/'to'/'verb'" {
            val incomplete = buildJsonObject { put("quality", "useful") }
            val result = buildFunctionModelHandler(buildJsonObject { put("edges", buildJsonArray { add(incomplete) }) })
            result.isError shouldBe true
            result.errorKind() shouldBe "malformed_input"
        }

        "build_function_model rejects a blank 'from'/'to'/'verb' instead of building a meaningless edge" {
            // OF-6: asNonBlankString previously only checked "is this a JSON string", not blank --
            // an all-whitespace endpoint or verb silently produced a nonsensical but "valid" model.
            val blankEdge = edgeJson("", "  ", "useful", "")
            val result = buildFunctionModelHandler(buildJsonObject { put("edges", buildJsonArray { add(blankEdge) }) })
            result.isError shouldBe true
            result.errorKind() shouldBe "malformed_input"
        }

        "build_function_model rejects a blank/whitespace-only component name instead of building a nameless component" {
            // OF-6 follow-up: the 'components' path validated string-ness and MAX_NAME_LENGTH but,
            // unlike 'edges', never rejected blank -- so {"components":["", "   "]} previously
            // built a "valid" model with two nameless components, while the exact same strings
            // arriving via edges[].from/to were already rejected as malformed_input.
            val result =
                buildFunctionModelHandler(
                    buildJsonObject {
                        put(
                            "components",
                            buildJsonArray {
                                add("")
                                add("   ")
                            },
                        )
                    },
                )
            result.isError shouldBe true
            result.errorKind() shouldBe "malformed_input"
        }

        "build_function_model accepts a self-loop" {
            val result =
                buildFunctionModelHandler(
                    buildJsonObject {
                        put("edges", buildJsonArray { add(edgeJson("Bearing", "Bearing", "harmful", "overheats")) })
                    },
                )
            result.isError.shouldBeNull()
            val componentNames =
                result.structured()["components"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
            componentNames shouldContainExactly listOf("Bearing")
        }
    })

private fun edgeJson(
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
