package dev.ktriz.tests

import dev.ktriz.script.KtrizScriptHost
import dev.ktriz.script.KtrizScriptOutcome
import io.kotest.core.annotation.Isolate
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

// @Isolate: this file evaluates with captureStdout = true, swapping System.out for the
// duration of each eval -- see KtrizScriptHostTest's own @Isolate KDoc for the full reasoning.
@Isolate
class FunctionModelScriptRenderTest :
    StringSpec({
        "function-model-svg.ktriz.kts compiles, runs, and renders a plausible SVG document" {
            val outcome = evalFixture("function-model-svg.ktriz.kts", captureStdout = true)
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.Success>()
            outcome.returnTypeName.shouldNotBeNull()
            outcome.returnTypeName shouldContain "String"
            (outcome.returnValue as String) shouldContain "<svg"
            outcome.capturedStdout.shouldNotBeNull()
            outcome.capturedStdout!! shouldContain "<svg"
            outcome.capturedStdout!! shouldContain "Engine"
            outcome.capturedStdout!! shouldContain "Coolant"
        }

        // Security-Loop focus: the existing XML escaping in FunctionModelSvgRenderer.kt
        // (SvgEscape.kt) must apply through the script path exactly as it does through a
        // direct Kotlin call -- see FunctionModelSvgRendererTest's equivalent library-level
        // test. This proves evalSource() (and therefore also eval(path), the same
        // evalToOutcome path) does not open a second, unescaped rendering path.
        "a component name with angle brackets/ampersand rendered via a script is still escaped, not raw" {
            val outcome =
                KtrizScriptHost.evalSource(
                    """functionModel { val a = component("<script>&\"'"); val b = component("B"); useful(a, b, "x") }.renderSvg()""",
                )
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.Success>()
            val svg = outcome.returnValue as String
            svg shouldContain "&lt;script&gt;"
            svg.contains("<script>") shouldBe false
        }
    })

// Fixtures live under ktriz-tests/src/test/resources and are loaded via getResourceAsStream --
// never an absolute vault/repo path (CLAUDE.md's CI rule) -- so they travel with the test
// classpath in CI the same way they do locally.
private fun readFixtureText(name: String): String {
    val loader = Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()
    val stream =
        loader.getResourceAsStream(name)
            ?: error("Test fixture '$name' is missing from the classpath at ktriz-tests/src/test/resources/$name")
    return stream.reader(Charsets.UTF_8).use { it.readText() }
}

private fun evalFixture(
    name: String,
    captureStdout: Boolean = false,
): KtrizScriptOutcome =
    KtrizScriptHost.evalSource(
        readFixtureText(name),
        fileName = name,
        captureStdout = captureStdout,
    )
