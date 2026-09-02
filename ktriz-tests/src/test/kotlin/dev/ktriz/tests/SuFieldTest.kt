package dev.ktriz.tests

import dev.ktriz.function.Component
import dev.ktriz.function.functionModel
import dev.ktriz.sufield.FieldType
import dev.ktriz.sufield.FieldType.MECHANICAL
import dev.ktriz.sufield.SuField
import dev.ktriz.sufield.SuFieldQuality
import dev.ktriz.sufield.SuFieldQuality.COMPLETE
import dev.ktriz.sufield.SuFieldQuality.EXCESSIVE
import dev.ktriz.sufield.SuFieldQuality.HARMFUL
import dev.ktriz.sufield.SuFieldQuality.INCOMPLETE
import dev.ktriz.sufield.SuFieldQuality.INSUFFICIENT
import dev.ktriz.sufield.suField
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

class SuFieldTest :
    StringSpec({
        val workpiece = Component("Workpiece")
        val tool = Component("Grinding wheel")

        "COMPLETE with s2 and field set is a valid SuField" {
            val sf = SuField(s1 = workpiece, s2 = tool, field = MECHANICAL, quality = COMPLETE)

            sf.s1 shouldBe workpiece
            sf.s2 shouldBe tool
            sf.field shouldBe MECHANICAL
            sf.quality shouldBe COMPLETE
        }

        "INCOMPLETE with s2 null (field set) is valid" {
            val sf = SuField(s1 = workpiece, s2 = null, field = MECHANICAL, quality = INCOMPLETE)

            sf.s2 shouldBe null
            sf.field shouldBe MECHANICAL
        }

        "INCOMPLETE with field null (s2 set) is valid" {
            val sf = SuField(s1 = workpiece, s2 = tool, field = null, quality = INCOMPLETE)

            sf.s2 shouldBe tool
            sf.field shouldBe null
        }

        "INCOMPLETE with both s2 and field null is valid" {
            val sf = SuField(s1 = workpiece, s2 = null, field = null, quality = INCOMPLETE)

            sf.s2 shouldBe null
            sf.field shouldBe null
        }

        "INSUFFICIENT/EXCESSIVE/HARMFUL with s2 and field set are each valid" {
            listOf(INSUFFICIENT, EXCESSIVE, HARMFUL).forAll { quality ->
                val sf = SuField(s1 = workpiece, s2 = tool, field = MECHANICAL, quality = quality)

                sf.quality shouldBe quality
                sf.s2 shouldBe tool
                sf.field shouldBe MECHANICAL
            }
        }

        "COMPLETE with s2 null is rejected, message names the quality" {
            val exception =
                shouldThrow<IllegalArgumentException> {
                    SuField(s1 = workpiece, s2 = null, field = MECHANICAL, quality = COMPLETE)
                }

            exception.message shouldContain "COMPLETE"
        }

        "COMPLETE with field null is rejected" {
            shouldThrow<IllegalArgumentException> {
                SuField(s1 = workpiece, s2 = tool, field = null, quality = COMPLETE)
            }
        }

        "INSUFFICIENT/EXCESSIVE/HARMFUL each reject a missing s2" {
            listOf(INSUFFICIENT, EXCESSIVE, HARMFUL).forAll { quality ->
                shouldThrow<IllegalArgumentException> {
                    SuField(s1 = workpiece, s2 = null, field = MECHANICAL, quality = quality)
                }
            }
        }

        "INSUFFICIENT/EXCESSIVE/HARMFUL each reject a missing field" {
            listOf(INSUFFICIENT, EXCESSIVE, HARMFUL).forAll { quality ->
                shouldThrow<IllegalArgumentException> {
                    SuField(s1 = workpiece, s2 = tool, field = null, quality = quality)
                }
            }
        }

        "INCOMPLETE with both s2 and field set is rejected as a structural contradiction" {
            val exception =
                shouldThrow<IllegalArgumentException> {
                    SuField(s1 = workpiece, s2 = tool, field = MECHANICAL, quality = INCOMPLETE)
                }

            exception.message shouldContain "INCOMPLETE"
        }

        "copy() cannot smuggle in an invalid quality/s2/field combination" {
            val sf = SuField(s1 = workpiece, s2 = tool, field = MECHANICAL, quality = COMPLETE)

            shouldThrow<IllegalArgumentException> {
                sf.copy(s2 = null)
            }
        }

        "a Component built outside any builder is accepted directly by the constructor" {
            val ghost = Component("Ghost")
            val sf = SuField(s1 = ghost, s2 = null, field = null, quality = INCOMPLETE)

            sf.s1 shouldBe ghost
        }

        "suField { } DSL builds the expected SuField" {
            val sf =
                suField {
                    val w = component("Workpiece")
                    val t = component("Grinding wheel")
                    s1(w)
                    s2(t)
                    field(MECHANICAL)
                    quality(INSUFFICIENT)
                }

            sf shouldBe SuField(s1 = workpiece, s2 = tool, field = MECHANICAL, quality = INSUFFICIENT)
        }

        "component() called twice with the same name returns the identical Component instance" {
            lateinit var a: Component
            lateinit var b: Component
            suField {
                a = component("X")
                b = component("X")
                s1(a)
                quality(INCOMPLETE)
            }

            a shouldBeSameInstanceAs b
        }

        "SuFieldBuilder.component() is independent from FunctionModelBuilder.component()" {
            lateinit var fromSuField: Component
            suField {
                fromSuField = component("Shared")
                s1(fromSuField)
                quality(INCOMPLETE)
            }

            lateinit var fromFunctionModel: Component
            functionModel {
                fromFunctionModel = component("Shared")
            }

            fromSuField shouldBe fromFunctionModel
            fromSuField shouldNotBeSameInstanceAs fromFunctionModel
        }

        "suField { } without s1(...) throws, message names s1" {
            val exception =
                shouldThrow<IllegalStateException> {
                    suField {
                        quality(INCOMPLETE)
                    }
                }

            exception.message shouldContain "s1"
        }

        "suField { } without quality(...) throws, message names quality" {
            val exception =
                shouldThrow<IllegalStateException> {
                    suField {
                        s1(Component("Workpiece"))
                    }
                }

            exception.message shouldContain "quality"
        }

        "the last call to s1()/s2()/field()/quality() wins on repeated calls" {
            val first = Component("First")
            val second = Component("Second")

            val sf =
                suField {
                    s1(first)
                    s1(second)
                    s2(first)
                    s2(second)
                    field(FieldType.MECHANICAL)
                    field(FieldType.THERMAL)
                    quality(INCOMPLETE)
                    quality(COMPLETE)
                }

            sf.s1 shouldBe second
            sf.s2 shouldBe second
            sf.field shouldBe FieldType.THERMAL
            sf.quality shouldBe COMPLETE
        }

        "independent suField { } calls do not leak builder state into one another" {
            val a =
                suField {
                    s1(workpiece)
                    quality(INCOMPLETE)
                }
            val b =
                suField {
                    s1(tool)
                    quality(INCOMPLETE)
                }

            a.s1 shouldNotBe b.s1
        }

        "the documented Su-Field example matches the README/KDoc snippet exactly" {
            val sf =
                suField {
                    val w = component("Workpiece")
                    val t = component("Grinding wheel")
                    s1(w)
                    s2(t)
                    field(MECHANICAL)
                    quality(SuFieldQuality.INSUFFICIENT)
                }

            sf shouldBe
                SuField(
                    s1 = Component("Workpiece"),
                    s2 = Component("Grinding wheel"),
                    field = MECHANICAL,
                    quality = INSUFFICIENT,
                )
        }
    })
