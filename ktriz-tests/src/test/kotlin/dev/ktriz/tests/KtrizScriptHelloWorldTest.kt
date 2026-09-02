package dev.ktriz.tests

import dev.ktriz.script.KtrizScriptHost
import dev.ktriz.script.KtrizScriptOutcome
import io.kotest.core.annotation.Isolate
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files

// @Isolate: hello-world.ktriz.kts and hello-world-no-imports.ktriz.kts are both run with
// captureStdout = true, swapping System.out for the duration of each eval -- see
// KtrizScriptHostTest's own @Isolate KDoc for the full reasoning.
@Isolate
class KtrizScriptHelloWorldTest :
    StringSpec({

        // The exact expected println output for the hello-world fixtures, as actually produced
        // by dev.ktriz.core.Contradiction/ContradictionMatrix for the WEIGHT_OF_MOVING_OBJECT
        // vs. STRENGTH contradiction (matrix cell 1x14 -> [28, 27, 18, 40]). Deliberately
        // *unpadded*: the fixture's println call interpolates labels of varying length with no
        // column alignment, so this is the literal, non-manually-aligned output -- not the
        // hand-aligned illustration in the "kTRIZ - DSL-Surface (Hello World)" design note.
        val expectedHelloWorldLines =
            listOf(
                "Widerspruch: Gewicht eines beweglichen Objekts ↑  vs.  Festigkeit ↓",
                "Empfohlene erfinderische Prinzipien:",
                "  #28  Ersetzen mechanischer Wirkprinzipien  (Mechanics substitution)",
                "  #27  Billige Kurzlebigkeit  (Cheap short-living objects)",
                "  #18  Mechanische Schwingungen  (Mechanical vibration)",
                "  #40  Verbundwerkstoffe  (Composite materials)",
            )

        "hello-world.ktriz.kts produces the exact expected output" {
            val outcome = evalFixture("hello-world.ktriz.kts", captureStdout = true)
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.Success>()
            outcome.capturedStdout.shouldNotBeNull()
            outcome.capturedStdout!!.trimEnd().lines() shouldBe expectedHelloWorldLines
        }

        "hello-world-no-imports.ktriz.kts produces identical output, proving defaultImports works" {
            val outcome = evalFixture("hello-world-no-imports.ktriz.kts", captureStdout = true)
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.Success>()
            outcome.capturedStdout.shouldNotBeNull()
            outcome.capturedStdout!!.trimEnd().lines() shouldBe expectedHelloWorldLines
        }

        "bogus-parameter.ktriz.kts fails to compile with a structured unresolved-reference diagnostic" {
            val outcome = evalFixture("bogus-parameter.ktriz.kts")
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.CompilationError>()
            val error =
                outcome.diagnostics.firstOrNull {
                    it.severity == "ERROR" &&
                        // Actual Kotlin 2.4.10 diagnostic text is "Unresolved reference
                        // 'STRUCTURAL_INTEGRITY'." -- see KtrizScriptHostTest's matching case
                        // for the verified-against-a-live-compile note.
                        it.message.contains("Unresolved reference") &&
                        it.message.contains("STRUCTURAL_INTEGRITY")
                }
            error.shouldNotBeNull()
            // Pinned against the fixture file's actual line 10 (`worsening =
            // EngineeringParameter.STRUCTURAL_INTEGRITY,`) -- see the fixture's own header
            // comment for the "don't guess, re-run and re-pin" instruction if it ever moves.
            error.line shouldBe 10
            error.column.shouldNotBeNull()
        }

        "self-contradiction.ktriz.kts compiles cleanly and throws at runtime from Contradiction.init" {
            val outcome = evalFixture("self-contradiction.ktriz.kts")
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.RuntimeError>()
            outcome.exceptionClass shouldBe "java.lang.IllegalArgumentException"
            outcome.message shouldContain "cannot contradict itself"
        }

        "bogus-parameter.ktriz.kts produces the identical CompilationError via KtrizScriptHost.eval(path)" {
            val fromClasspath = evalFixture("bogus-parameter.ktriz.kts")
            fromClasspath.shouldBeInstanceOf<KtrizScriptOutcome.CompilationError>()

            val dir = Files.createTempDirectory("ktriz-script-hello-world-test")
            val copy = dir.resolve("bogus-parameter.ktriz.kts").toFile()
            copy.writeText(readFixtureText("bogus-parameter.ktriz.kts"))
            val fromFile = KtrizScriptHost.eval(copy.path)
            fromFile.shouldBeInstanceOf<KtrizScriptOutcome.CompilationError>()

            fromFile.diagnostics.map { it.severity to it.message } shouldBe
                fromClasspath.diagnostics.map { it.severity to it.message }
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
