package dev.ktriz.tests

import dev.ktriz.evolution.EvolutionLaw
import dev.ktriz.evolution.EvolutionLawGroup
import dev.ktriz.mcp.tools.listEvolutionLawsHandler
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.annotation.Isolate
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// @Isolate: one test swaps System.out.
@Isolate
class EvolutionLawTest :
    StringSpec({
        "exactly eight entries with gap-free unique ids 1..8" {
            EvolutionLaw.entries.map { it.id } shouldContainExactly (1..8).toList()
        }

        "the name set is pinned against silent extension" {
            EvolutionLaw.entries.map { it.name } shouldContainExactly
                listOf(
                    "COMPLETENESS_OF_PARTS",
                    "ENERGY_CONDUCTIVITY",
                    "HARMONIZATION_OF_RHYTHMS",
                    "INCREASING_IDEALITY",
                    "UNEVEN_DEVELOPMENT_OF_PARTS",
                    "TRANSITION_TO_SUPERSYSTEM",
                    "TRANSITION_TO_MICRO_LEVEL",
                    "INCREASING_SUBSTANCE_FIELD_INVOLVEMENT",
                )
        }

        "grouping is 3/3/2: statics 1-3, kinematics 4-6, dynamics 7-8" {
            fun ids(g: EvolutionLawGroup) = EvolutionLaw.entries.filter { it.group == g }.map { it.id }
            ids(EvolutionLawGroup.STATICS) shouldContainExactly listOf(1, 2, 3)
            ids(EvolutionLawGroup.KINEMATICS) shouldContainExactly listOf(4, 5, 6)
            ids(EvolutionLawGroup.DYNAMICS) shouldContainExactly listOf(7, 8)
        }

        "labels and descriptions are non-blank, distinct and short" {
            EvolutionLaw.entries.forEach {
                it.labelEn.isNotBlank() shouldBe true
                it.description.isNotBlank() shouldBe true
                (it.labelEn == it.description) shouldBe false
                (it.description.length <= 300) shouldBe true
                (it.description.count { c -> c == '.' } <= 2) shouldBe true
            }
        }

        "ofId round-trips and rejects 0 and 9" {
            EvolutionLaw.entries.forEach { EvolutionLaw.ofId(it.id) shouldBe it }
            shouldThrow<IllegalStateException> { EvolutionLaw.ofId(0) }
            shouldThrow<IllegalStateException> { EvolutionLaw.ofId(9) }
        }

        "list_evolution_laws returns eight ordered entries with all fields and ignores unknown arguments" {
            val r = listEvolutionLawsHandler(buildJsonObject { put("bogus", "x") })
            r.isError.shouldBeNull()
            val st: JsonObject = r.structuredContent!!
            st["count"]!!.jsonPrimitive.int shouldBe 8
            val arr = st["evolutionLaws"]!!.jsonArray.map { it.jsonObject }
            arr.map { it["id"]!!.jsonPrimitive.int } shouldContainExactly (1..8).toList()
            arr.forEach {
                it.keys shouldBe setOf("id", "symbol", "labelEn", "group", "description")
                it["symbol"]!!.jsonPrimitive.content shouldBe EvolutionLaw.ofId(it["id"]!!.jsonPrimitive.int).name
            }
            st["scope"]!!.jsonPrimitive.content shouldBe "classical_laws_only"
            st["note"]!!.jsonPrimitive.content shouldContain "No evolution lines"
        }

        "list_evolution_laws writes nothing to stdout" {
            val original = System.out
            val captured = java.io.ByteArrayOutputStream()
            System.setOut(java.io.PrintStream(captured, true, Charsets.UTF_8))
            try {
                listEvolutionLawsHandler(null)
            } finally {
                System.setOut(original)
            }
            captured.toString(Charsets.UTF_8) shouldBe ""
        }
    })
