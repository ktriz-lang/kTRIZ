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
// duration of each eval -- see KtrizScriptHostTest's own @Isolate KDoc for the full reasoning,
// and FunctionModelScriptRenderTest for the same pattern applied to functionModel { }.
@Isolate
class SuFieldScriptRenderTest :
    StringSpec({
        "su-field-svg.ktriz.kts compiles, runs, and renders a plausible SVG document with no explicit imports" {
            val outcome = evalSuFieldFixture("su-field-svg.ktriz.kts", captureStdout = true)
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.Success>()
            outcome.returnTypeName.shouldNotBeNull()
            outcome.returnTypeName shouldContain "String"
            (outcome.returnValue as String) shouldContain "<svg"
            outcome.capturedStdout.shouldNotBeNull()
            outcome.capturedStdout!! shouldContain "<svg"
            outcome.capturedStdout!! shouldContain "Workpiece"
            outcome.capturedStdout!! shouldContain "Grinding wheel"
        }

        // Security-Loop focus: proves the script path (evalSource(), the same evalToOutcome
        // path evalFixture()/eval(path) also use) does not open a second, unescaped rendering
        // path for Su-Field -- mirrors FunctionModelScriptRenderTest's equivalent check.
        "a component name with angle brackets/ampersand rendered via a script is still escaped, not raw" {
            val outcome =
                KtrizScriptHost.evalSource(
                    """suField {
                        |    val part = component("<script>&\"'")
                        |    s1(part)
                        |    quality(SuFieldQuality.INCOMPLETE)
                        |}.renderSvg()
                    """.trimMargin(),
                )
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.Success>()
            val svg = outcome.returnValue as String
            svg shouldContain "&lt;script&gt;"
            svg.contains("<script>") shouldBe false
        }
    })

// Fixtures live under ktriz-tests/src/test/resources and are loaded via getResourceAsStream --
// never an absolute vault/repo path (CLAUDE.md's CI rule) -- so they travel with the test
// classpath in CI the same way they do locally. Mirrors FunctionModelScriptRenderTest's private
// helpers; kept file-local rather than shared, matching that file's own precedent.
private fun readSuFieldFixtureText(name: String): String {
    val loader = Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()
    val stream =
        loader.getResourceAsStream(name)
            ?: error("Test fixture '$name' is missing from the classpath at ktriz-tests/src/test/resources/$name")
    return stream.reader(Charsets.UTF_8).use { it.readText() }
}

private fun evalSuFieldFixture(
    name: String,
    captureStdout: Boolean = false,
): KtrizScriptOutcome =
    KtrizScriptHost.evalSource(
        readSuFieldFixtureText(name),
        fileName = name,
        captureStdout = captureStdout,
    )
