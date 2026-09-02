package dev.ktriz.tests

import dev.ktriz.function.FunctionModel
import dev.ktriz.script.KtrizScriptHost
import dev.ktriz.script.KtrizScriptOutcome
import dev.ktriz.script.KtrizScriptOutcomeCodes
import io.kotest.core.annotation.Isolate
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import java.nio.file.Files

// @Isolate: several cases here swap System.out via KtrizScriptHost.eval(..., captureStdout =
// true). Kotest 5 runs specs sequentially by default, but this annotation makes that
// non-negotiable instead of an unstated assumption -- see the "stdout restored" case at the
// bottom, which is this file's own regression guard for the swap being cleaned up correctly.
@Isolate
class KtrizScriptHostTest :
    StringSpec({

        // ---- evalSource: compilation errors ----------------------------------------------

        "a syntactically incomplete script is a CompilationError whose first ERROR is on line 1" {
            val outcome = KtrizScriptHost.evalSource("val x = ")
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.CompilationError>()
            val firstError = outcome.diagnostics.first { it.severity == "ERROR" }
            firstError.line shouldBe 1
        }

        "an unresolved reference is a CompilationError mentioning 'Unresolved reference'" {
            val outcome = KtrizScriptHost.evalSource("totallyUndefined()")
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.CompilationError>()
            // Kotlin 2.4.10's actual diagnostic text is "Unresolved reference 'totallyUndefined'."
            // -- capitalized, no colon, quoted symbol -- not kotlinc CLI's older
            // "error: unresolved reference: X" rendering. Verified against a live compile
            // (2026-09-02); don't "fix" this back to the CLI's phrasing without re-checking.
            outcome.diagnostics.any { it.message.contains("Unresolved reference") } shouldBe true
        }

        "a type error is a CompilationError" {
            val outcome = KtrizScriptHost.evalSource("val p: Int = \"x\"")
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.CompilationError>()
        }

        // ---- evalSource: Unit / trivial success -------------------------------------------

        "an empty script is a Success with a null return value" {
            val outcome = KtrizScriptHost.evalSource("")
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.Success>()
            outcome.returnValue.shouldBeNull()
        }

        "a whitespace-only script is a Success with a null return value" {
            val outcome = KtrizScriptHost.evalSource("   \n\t \n")
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.Success>()
            outcome.returnValue.shouldBeNull()
        }

        "a script ending in a val declaration (Unit) is a Success with a null return value" {
            val outcome = KtrizScriptHost.evalSource("val x = 1")
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.Success>()
            outcome.returnValue.shouldBeNull()
        }

        "a script ending in an Int expression is a Success carrying that value and type" {
            val outcome = KtrizScriptHost.evalSource("41 + 1")
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.Success>()
            outcome.returnValue shouldBe 42
            outcome.returnTypeName.shouldNotBeNull()
            outcome.returnTypeName shouldContain "Int"
        }

        // ---- evalSource: runtime errors -----------------------------------------------------

        "a script that calls error(...) is a RuntimeError carrying that message" {
            val outcome = KtrizScriptHost.evalSource("""error("boom")""")
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.RuntimeError>()
            outcome.message shouldContain "boom"
        }

        "a self-contradicting Contradiction is a RuntimeError from the require() in its init block" {
            val outcome =
                KtrizScriptHost.evalSource(
                    "contradiction(improving = EngineeringParameter.STRENGTH, " +
                        "worsening = EngineeringParameter.STRENGTH)",
                )
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.RuntimeError>()
            outcome.exceptionClass shouldBe "java.lang.IllegalArgumentException"
            outcome.message shouldContain "cannot contradict itself"
        }

        // ---- defaultImports -----------------------------------------------------------------

        "dev.ktriz.core.* is a default import -- a script can use it with no explicit import" {
            val outcome =
                KtrizScriptHost.evalSource(
                    "contradiction(improving = EngineeringParameter.WEIGHT_OF_MOVING_OBJECT, " +
                        "worsening = EngineeringParameter.STRENGTH).resolve()",
                )
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.Success>()
        }

        "dev.ktriz.function.* is a default import -- functionModel { } needs no explicit import" {
            val outcome =
                KtrizScriptHost.evalSource(
                    """functionModel { val a = component("A"); val b = component("B"); useful(a, b, "acts") }""",
                )
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.Success>()
            outcome.returnValue.shouldBeInstanceOf<FunctionModel>()
        }

        "dev.ktriz.render.kuml.* is a default import -- FunctionModel.renderSvg() needs no explicit import" {
            val outcome =
                KtrizScriptHost.evalSource(
                    """functionModel { val a = component("A"); val b = component("B"); useful(a, b, "acts") }.renderSvg()""",
                )
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.Success>()
            outcome.returnValue.shouldBeInstanceOf<String>()
            (outcome.returnValue as String) shouldContain "<svg"
        }

        // ---- stdout capture -------------------------------------------------------------------

        "captureStdout = true captures the script's println output instead of the real stdout" {
            val outcome = KtrizScriptHost.evalSource("""println("hallo")""", captureStdout = true)
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.Success>()
            outcome.capturedStdout?.trim() shouldBe "hallo"
        }

        "captureStdout = false (the default) leaves capturedStdout null" {
            val outcome = KtrizScriptHost.evalSource("""println("hallo")""")
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.Success>()
            outcome.capturedStdout.shouldBeNull()
        }

        "System.out is restored to its original stream after a captureStdout = true eval" {
            val before = System.out
            KtrizScriptHost.evalSource("""println("x")""", captureStdout = true)
            System.out shouldBe before
        }

        // ---- eval(path): the security gate ---------------------------------------------------

        "eval(\"\") is a SourceRejected with SOURCE_BLANK_PATH" {
            val outcome = KtrizScriptHost.eval("")
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.SourceRejected>()
            outcome.code shouldBe KtrizScriptOutcomeCodes.SOURCE_BLANK_PATH
        }

        "eval of an https:// URL is a SourceRejected with SOURCE_REMOTE_URI, never a network call" {
            val outcome = KtrizScriptHost.eval("https://example.invalid/evil.ktriz.kts")
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.SourceRejected>()
            outcome.code shouldBe KtrizScriptOutcomeCodes.SOURCE_REMOTE_URI
        }

        "eval of a file:// URL is a SourceRejected with SOURCE_REMOTE_URI" {
            val outcome = KtrizScriptHost.eval("file:///tmp/x.ktriz.kts")
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.SourceRejected>()
            outcome.code shouldBe KtrizScriptOutcomeCodes.SOURCE_REMOTE_URI
        }

        "eval of a Windows drive-letter-shaped path is NOT rejected as a URI (regression guard)" {
            // A single letter followed by ':' (a Windows drive letter, e.g. "C:\scripts\x.kts")
            // is never in REJECTED_URI_SCHEMES -- every real scheme kTRIZ rejects (http, https,
            // file, ftp, ftps, jar, data) is two or more characters. On this (Linux) test host
            // the path itself doesn't exist, so the expected rejection is SOURCE_NOT_FOUND --
            // the point of this test is only that it is *not* SOURCE_REMOTE_URI.
            val outcome = KtrizScriptHost.eval("C:\\scripts\\hello.ktriz.kts")
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.SourceRejected>()
            outcome.code shouldBe KtrizScriptOutcomeCodes.SOURCE_NOT_FOUND
        }

        "eval of a relative colon-first-segment path is NOT rejected as a URI (regression guard)" {
            // Colons are legal filename characters on Linux/macOS. "ab:hello.ktriz.kts" has a
            // colon before its first '/' but "ab" is not in REJECTED_URI_SCHEMES, so this must
            // NOT be rejected as SOURCE_REMOTE_URI. This must be a path with no leading '/' --
            // the old, buggy heuristic was `Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]+:")` anchored at
            // the start of the string, so it only ever matched when the very first character
            // was a letter. An absolute path like "/tmp/.../ab:hello.ktriz.kts" starts with
            // '/' and was never rejected even under the old code -- it can't tell the old
            // heuristic apart from the current allowlist and is not a regression guard. Using
            // a bare relative path here means the file doesn't need to (and doesn't) exist, so
            // the expected outcome is SOURCE_NOT_FOUND -- under the old regex this path would
            // instead have come back as SOURCE_REMOTE_URI, which is exactly the distinction
            // this test exists to catch.
            val outcome = KtrizScriptHost.eval("ab:hello.ktriz.kts")
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.SourceRejected>()
            outcome.code shouldBe KtrizScriptOutcomeCodes.SOURCE_NOT_FOUND
        }

        "eval of an absolute path whose first segment has a colon that is not a real scheme evaluates the file" {
            // Complements the relative-path regression guard above: an absolute local file
            // whose name happens to contain a colon must still resolve and run normally.
            val dir = Files.createTempDirectory("ktriz-script-host-test")
            val file = dir.resolve("ab:hello.ktriz.kts").toFile()
            file.writeText("3 + 3")
            val outcome = KtrizScriptHost.eval(file.path)
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.Success>()
            outcome.returnValue shouldBe 6
        }

        "eval of an ftps:// or jar:/ or data: URI is a SourceRejected with SOURCE_REMOTE_URI" {
            listOf(
                "ftps://example.invalid/evil.ktriz.kts",
                "jar:file:/tmp/x.jar!/evil.ktriz.kts",
                "data:text/plain;base64,ZXZpbA==",
            ).forEach { uri ->
                val outcome = KtrizScriptHost.eval(uri)
                outcome.shouldBeInstanceOf<KtrizScriptOutcome.SourceRejected>()
                outcome.code shouldBe KtrizScriptOutcomeCodes.SOURCE_REMOTE_URI
            }
        }

        "eval of a nonexistent path is a SourceRejected with SOURCE_NOT_FOUND" {
            val dir = Files.createTempDirectory("ktriz-script-host-test")
            val missing = dir.resolve("nope.ktriz.kts")
            val outcome = KtrizScriptHost.eval(missing.toString())
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.SourceRejected>()
            outcome.code shouldBe KtrizScriptOutcomeCodes.SOURCE_NOT_FOUND
        }

        "eval of a directory is a SourceRejected with SOURCE_NOT_A_FILE" {
            val dir = Files.createTempDirectory("ktriz-script-host-test")
            val outcome = KtrizScriptHost.eval(dir.toString())
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.SourceRejected>()
            outcome.code shouldBe KtrizScriptOutcomeCodes.SOURCE_NOT_A_FILE
        }

        "eval of a file over the size limit is a SourceRejected with SOURCE_TOO_LARGE" {
            val dir = Files.createTempDirectory("ktriz-script-host-test")
            val big = dir.resolve("big.ktriz.kts").toFile()
            big.writeBytes(ByteArray((KtrizScriptHost.MAX_SCRIPT_BYTES + 1).toInt()))
            val outcome = KtrizScriptHost.eval(big.path)
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.SourceRejected>()
            outcome.code shouldBe KtrizScriptOutcomeCodes.SOURCE_TOO_LARGE
        }

        "eval of a valid temp file evaluates the script" {
            val dir = Files.createTempDirectory("ktriz-script-host-test")
            val file = dir.resolve("valid.ktriz.kts").toFile()
            file.writeText("1 + 1")
            val outcome = KtrizScriptHost.eval(file.path)
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.Success>()
            outcome.returnValue shouldBe 2
        }

        "eval of a path with '../' segments resolves to the canonicalized path and evaluates it" {
            val dir = Files.createTempDirectory("ktriz-script-host-test")
            val nested = dir.resolve("nested").toFile().apply { mkdir() }
            val file = dir.resolve("traversal-target.ktriz.kts").toFile()
            file.writeText("2 + 2")
            val traversalPath = File(nested, "../traversal-target.ktriz.kts").path
            val outcome = KtrizScriptHost.eval(traversalPath)
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.Success>()
            outcome.returnValue shouldBe 4
        }

        "a SourceRejected message for a nonexistent path names the canonicalized path, not the raw traversal string" {
            val dir = Files.createTempDirectory("ktriz-script-host-test")
            val nested = dir.resolve("nested").toFile().apply { mkdir() }
            val traversalPath = File(nested, "../does-not-exist.ktriz.kts").path
            val outcome = KtrizScriptHost.eval(traversalPath)
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.SourceRejected>()
            outcome.message shouldContain "does-not-exist.ktriz.kts"
            outcome.message.contains("..") shouldBe false
        }
    })
