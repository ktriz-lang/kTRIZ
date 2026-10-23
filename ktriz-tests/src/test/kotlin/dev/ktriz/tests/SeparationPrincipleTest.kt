package dev.ktriz.tests

import dev.ktriz.mcp.tools.listSeparationPrinciplesHandler
import dev.ktriz.physical.SeparationPrinciple
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SeparationPrincipleTest :
    StringSpec({
        "exactly four entries with gap-free unique ids 1..4" {
            SeparationPrinciple.entries.map { it.id } shouldContainExactly listOf(1, 2, 3, 4)
        }

        "the name set is pinned against silent extension" {
            SeparationPrinciple.entries.map { it.name } shouldContainExactly
                listOf("IN_SPACE", "IN_TIME", "UPON_CONDITION", "ON_SYSTEM_LEVEL")
        }

        "labels are not blank" {
            SeparationPrinciple.entries.forEach {
                it.labelEn.isNotBlank() shouldBe true
                it.labelDe.isNotBlank() shouldBe true
            }
        }

        "ofId round-trips and rejects ids outside 1..4" {
            SeparationPrinciple.entries.forEach { SeparationPrinciple.ofId(it.id) shouldBe it }
            shouldThrow<IllegalStateException> { SeparationPrinciple.ofId(0) }
            shouldThrow<IllegalStateException> { SeparationPrinciple.ofId(5) }
        }

        "list_separation_principles reports that no inventive-principle mapping is bundled" {
            val structured = listSeparationPrinciplesHandler(null).structuredContent!!
            structured["inventivePrincipleMapping"]!!.jsonPrimitive.content shouldBe "none"
            structured["separationPrinciples"]!!.jsonArray.forEach {
                it.jsonObject.containsKey("inventivePrinciples") shouldBe false
            }
        }
    })
