package dev.ktriz.tests

import dev.ktriz.function.Component
import dev.ktriz.function.FunctionEdge
import dev.ktriz.function.FunctionModel
import dev.ktriz.function.FunctionQuality.EXCESSIVE
import dev.ktriz.function.FunctionQuality.HARMFUL
import dev.ktriz.function.FunctionQuality.INSUFFICIENT
import dev.ktriz.function.FunctionQuality.USEFUL
import dev.ktriz.function.functionModel
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

class FunctionModelTest :
    StringSpec({
        "functionModel builds a model with the declared components in first-seen order and their edges" {
            val fm =
                functionModel {
                    val engine = component("Engine")
                    val coolant = component("Coolant")
                    val block = component("Cylinder block")
                    useful(from = coolant, to = engine, verb = "cools")
                    harmful(from = engine, to = block, verb = "overheats")
                    insufficient(from = coolant, to = engine, verb = "circulates")
                }

            val engine = Component("Engine")
            val coolant = Component("Coolant")
            val block = Component("Cylinder block")

            fm.components shouldBe listOf(engine, coolant, block)
            fm.edges shouldBe
                listOf(
                    FunctionEdge(coolant, engine, USEFUL, "cools"),
                    FunctionEdge(engine, block, HARMFUL, "overheats"),
                    FunctionEdge(coolant, engine, INSUFFICIENT, "circulates"),
                )
        }

        "useful() records a USEFUL edge with the given from/to/verb" {
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    useful(from = a, to = b, verb = "drives")
                }

            fm.edges shouldBe listOf(FunctionEdge(Component("A"), Component("B"), USEFUL, "drives"))
        }

        "harmful() records a HARMFUL edge with the given from/to/verb" {
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    harmful(from = a, to = b, verb = "corrodes")
                }

            fm.edges shouldBe listOf(FunctionEdge(Component("A"), Component("B"), HARMFUL, "corrodes"))
        }

        "insufficient() records an INSUFFICIENT edge with the given from/to/verb" {
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    insufficient(from = a, to = b, verb = "lubricates")
                }

            fm.edges shouldBe listOf(FunctionEdge(Component("A"), Component("B"), INSUFFICIENT, "lubricates"))
        }

        "excessive() records an EXCESSIVE edge with the given from/to/verb" {
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    excessive(from = a, to = b, verb = "heats")
                }

            fm.edges shouldBe listOf(FunctionEdge(Component("A"), Component("B"), EXCESSIVE, "heats"))
        }

        "component() called twice with the same name returns the identical Component instance" {
            lateinit var a: Component
            lateinit var b: Component
            functionModel {
                a = component("X")
                b = component("X")
            }

            a shouldBeSameInstanceAs b
        }

        "component() dedup keeps components in first-declaration order even when edges reference them out of order" {
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    useful(from = b, to = a, verb = "feeds back to")
                }

            fm.components shouldBe listOf(Component("A"), Component("B"))
        }

        "an empty functionModel block yields an empty model, not an error" {
            val fm = functionModel {}

            fm.components.shouldBeEmpty()
            fm.edges.shouldBeEmpty()
        }

        "edges preserve call order across mixed quality helpers" {
            val fm =
                functionModel {
                    val a = component("A")
                    val b = component("B")
                    useful(from = a, to = b, verb = "v1")
                    excessive(from = a, to = b, verb = "v2")
                    harmful(from = a, to = b, verb = "v3")
                    insufficient(from = a, to = b, verb = "v4")
                }

            fm.edges.map { it.quality } shouldBe listOf(USEFUL, EXCESSIVE, HARMFUL, INSUFFICIENT)
        }

        "a self-loop (from == to) is accepted, not rejected" {
            val fm =
                functionModel {
                    val turbine = component("Turbine")
                    useful(from = turbine, to = turbine, verb = "wears")
                }

            val turbine = Component("Turbine")
            val edge = fm.edges.single()

            edge.from shouldBeSameInstanceAs edge.to
            fm.edges shouldContain FunctionEdge(turbine, turbine, USEFUL, "wears")
        }

        "a Component built outside component() is accepted by edge helpers but absent from FunctionModel.components" {
            val ghost = Component("Ghost")
            val fm =
                functionModel {
                    val engine = component("Engine")
                    useful(from = ghost, to = engine, verb = "haunts")
                }

            fm.edges.single().from shouldBe ghost
            fm.components shouldNotContain ghost
        }

        "the documented Hello-World example matches the README/KDoc snippet exactly" {
            val fm =
                functionModel {
                    val engine = component("Engine")
                    val coolant = component("Coolant")
                    val block = component("Cylinder block")
                    useful(from = coolant, to = engine, verb = "cools")
                    harmful(from = engine, to = block, verb = "overheats")
                    insufficient(from = coolant, to = engine, verb = "circulates")
                }

            val engine = Component("Engine")
            val coolant = Component("Coolant")
            val block = Component("Cylinder block")

            fm shouldBe
                FunctionModel(
                    components = listOf(engine, coolant, block),
                    edges =
                        listOf(
                            FunctionEdge(coolant, engine, USEFUL, "cools"),
                            FunctionEdge(engine, block, HARMFUL, "overheats"),
                            FunctionEdge(coolant, engine, INSUFFICIENT, "circulates"),
                        ),
                )
        }
    })
