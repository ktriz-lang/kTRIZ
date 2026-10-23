package dev.ktriz.tests

import dev.ktriz.script.KtrizScriptHost
import dev.ktriz.script.KtrizScriptOutcome
import io.kotest.core.annotation.Isolate
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

// @Isolate: evaluates with captureStdout = true, which swaps System.out (see SuFieldScriptRenderTest).
@Isolate
class PhysicalContradictionScriptTest :
    StringSpec({
        "physical-contradiction.ktriz.kts runs with no explicit imports" {
            val name = "physical-contradiction.ktriz.kts"
            val loader = Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()
            val text =
                (loader.getResourceAsStream(name) ?: error("fixture $name missing"))
                    .reader(Charsets.UTF_8)
                    .use { it.readText() }
            val outcome = KtrizScriptHost.evalSource(text, fileName = name, captureStdout = true)
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.Success>()
            val out = outcome.capturedStdout.shouldNotBeNull()
            out shouldContain "Element: Landing gear"
            out shouldContain "Property: Presence"
            out shouldContain "State: present"
            out shouldContain "Opposite state: absent"
            out shouldContain "Separation: Separation in time / Trennung in der Zeit"
        }

        "equal states in a script give a RuntimeError KTRIZ-S-002 path" {
            val outcome =
                KtrizScriptHost.evalSource(
                    """physicalContradiction {
                        |    element("E"); property("P"); state("hot"); oppositeState("HOT")
                        |}
                    """.trimMargin(),
                )
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.RuntimeError>()
            outcome.message shouldContain "different"
        }

        "an invented separation principle does not compile" {
            val outcome =
                KtrizScriptHost.evalSource(
                    """physicalContradiction {
                        |    element("E"); property("P"); state("a"); oppositeState("b")
                        |    separation(SeparationPrinciple.IN_ORBIT)
                        |}
                    """.trimMargin(),
                )
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.CompilationError>()
        }

        "state() inside a nested suField block is rejected by the DslMarker" {
            val outcome =
                KtrizScriptHost.evalSource(
                    """physicalContradiction {
                        |    element("E"); property("P"); state("a"); oppositeState("b")
                        |    suField { s1(component("X")); state("oops"); quality(SuFieldQuality.INCOMPLETE) }
                        |}
                    """.trimMargin(),
                )
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.CompilationError>()
        }
    })
