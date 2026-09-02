package dev.ktriz.tests

import dev.ktriz.cli.CliCommand
import dev.ktriz.cli.USAGE_TEXT
import dev.ktriz.cli.errorJson
import dev.ktriz.cli.errorText
import dev.ktriz.cli.exitCodeFor
import dev.ktriz.cli.resolveCommand
import dev.ktriz.cli.successJson
import dev.ktriz.script.KtrizScriptOutcome
import dev.ktriz.script.KtrizScriptOutcomeCodes
import dev.ktriz.script.ScriptDiagnosticView
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

// resolveCommand only -- never main() itself, since main()'s error path calls exitProcess, which
// would tear down this whole test JVM (and every other test running in it) rather than just
// failing one assertion. See KtrizScriptHelloWorldTest for actual end-to-end script-running
// coverage via KtrizScriptHost, and the manual `./ktriz-cli/build/install/...` invocations in
// this wave's plan for a real process-boundary check of main() itself.
class CliMainTest :
    StringSpec({
        "no arguments resolves to ShowUsage with exit code 0" {
            resolveCommand(emptyArray()) shouldBe CliCommand.ShowUsage(0)
        }

        "the \"mcp\" argument resolves to StartMcpServer" {
            resolveCommand(arrayOf("mcp")) shouldBe CliCommand.StartMcpServer
        }

        "\"mcp\" with a trailing extra argument is rejected, not silently accepted" {
            resolveCommand(arrayOf("mcp", "extra")) shouldBe CliCommand.ShowUsage(1)
        }

        "the \"help\" argument resolves to ShowUsage with exit code 0" {
            resolveCommand(arrayOf("help")) shouldBe CliCommand.ShowUsage(0)
        }

        "the \"--help\" argument resolves to ShowUsage with exit code 0" {
            resolveCommand(arrayOf("--help")) shouldBe CliCommand.ShowUsage(0)
        }

        "an unknown subcommand resolves to ShowUsage with exit code 1" {
            resolveCommand(arrayOf("bogus")) shouldBe CliCommand.ShowUsage(1)
        }

        "\"run <path>\" with no flags resolves to Run with jsonOutput false" {
            resolveCommand(arrayOf("run", "a.ktriz.kts")) shouldBe
                CliCommand.Run(scriptPath = "a.ktriz.kts", jsonOutput = false)
        }

        "\"run <path> --output json\" resolves to Run with jsonOutput true" {
            resolveCommand(arrayOf("run", "a.ktriz.kts", "--output", "json")) shouldBe
                CliCommand.Run(scriptPath = "a.ktriz.kts", jsonOutput = true)
        }

        "\"run --output json <path>\" (flag before the path) resolves the same as flag after" {
            resolveCommand(arrayOf("run", "--output", "json", "a.ktriz.kts")) shouldBe
                CliCommand.Run(scriptPath = "a.ktriz.kts", jsonOutput = true)
        }

        "\"run\" with no script path resolves to ShowUsage with exit code 1" {
            resolveCommand(arrayOf("run")) shouldBe CliCommand.ShowUsage(1)
        }

        "\"run\" with only flags and no script path resolves to ShowUsage with exit code 1" {
            resolveCommand(arrayOf("run", "--output", "json")) shouldBe CliCommand.ShowUsage(1)
        }

        "\"run <path> --output\" with no value resolves to ShowUsage with exit code 1" {
            resolveCommand(arrayOf("run", "a.ktriz.kts", "--output")) shouldBe CliCommand.ShowUsage(1)
        }

        "\"run <path> --output xml\" (an unknown --output value) resolves to ShowUsage with exit code 1" {
            resolveCommand(arrayOf("run", "a.ktriz.kts", "--output", "xml")) shouldBe CliCommand.ShowUsage(1)
        }

        "\"run <path> --bogus\" (an unknown flag) resolves to ShowUsage with exit code 1" {
            resolveCommand(arrayOf("run", "a.ktriz.kts", "--bogus")) shouldBe CliCommand.ShowUsage(1)
        }

        "\"run\" with two positional script paths resolves to ShowUsage with exit code 1" {
            resolveCommand(arrayOf("run", "a.ktriz.kts", "b.ktriz.kts")) shouldBe CliCommand.ShowUsage(1)
        }

        "\"help extra\" (help with a trailing extra argument) resolves to ShowUsage with exit code 1" {
            resolveCommand(arrayOf("help", "extra")) shouldBe CliCommand.ShowUsage(1)
        }

        "a very long garbage argument does not throw and resolves to ShowUsage with exit code 1" {
            resolveCommand(arrayOf("x".repeat(50_000))) shouldBe CliCommand.ShowUsage(1)
        }

        "an argument with control/unusual characters does not throw and resolves to ShowUsage with exit code 1" {
            val weird = "\u0000\n\t\uFFFF"
            resolveCommand(arrayOf(weird)) shouldBe CliCommand.ShowUsage(1)
        }

        "a single empty-string argument does not throw and resolves to ShowUsage with exit code 1" {
            resolveCommand(arrayOf("")) shouldBe CliCommand.ShowUsage(1)
        }

        "USAGE_TEXT documents the mcp subcommand" {
            USAGE_TEXT shouldContain "ktriz mcp"
        }

        "USAGE_TEXT documents the run subcommand" {
            USAGE_TEXT shouldContain "ktriz run"
        }

        "USAGE_TEXT documents the help subcommand" {
            USAGE_TEXT shouldContain "ktriz help"
        }

        "USAGE_TEXT documents the absence of a sandbox" {
            USAGE_TEXT shouldContain "no sandbox"
        }

        // ---- successJson/errorJson/errorText/exitCodeFor: the `ktriz run` result contract ---
        // Pinned against README.adoc's <<scripting>> section (the machine-readable
        // generate-compile-repair contract a consumer parses `--output json` for) and against
        // the exit-code half of that same contract -- Success is exit 0, every other
        // KtrizScriptOutcome variant is exit 1. A field rename, a dropped field, or an exit
        // code drifting off this mapping breaks this JSON.toString() equality even though
        // every type still compiles and `./gradlew check` stays green otherwise.

        "successJson renders status/returnType/stdout for a Success with a return value, and exitCodeFor is 0" {
            val outcome =
                KtrizScriptOutcome.Success(
                    returnValue = 42,
                    returnTypeName = "kotlin.Int",
                    capturedStdout = "hi",
                )
            successJson(outcome).toString() shouldBe """{"status":"success","returnType":"kotlin.Int","stdout":"hi"}"""
            exitCodeFor(outcome) shouldBe 0
        }

        "successJson renders a null returnType and an empty stdout for a Unit Success with no captured output" {
            val outcome = KtrizScriptOutcome.Success(returnValue = null, returnTypeName = null, capturedStdout = null)
            successJson(outcome).toString() shouldBe """{"status":"success","returnType":null,"stdout":""}"""
            exitCodeFor(outcome) shouldBe 0
        }

        "errorJson/errorText render a CompilationError with a located diagnostic, code KTRIZ-S-001, exit 1" {
            val outcome =
                KtrizScriptOutcome.CompilationError(
                    listOf(
                        ScriptDiagnosticView(
                            severity = "ERROR",
                            message = "Unresolved reference 'x'.",
                            line = 10,
                            column = 38,
                        ),
                    ),
                )
            errorJson(outcome).toString() shouldBe
                """{"status":"error","errorKind":"compilation_error","code":"KTRIZ-S-001",""" +
                """"diagnostics":[{"severity":"ERROR","message":"Unresolved reference 'x'.","line":10,"column":38}]}"""
            errorText(outcome) shouldBe
                "Script failed to compile:\n  [ERROR] (line 10, column 38) Unresolved reference 'x'."
            exitCodeFor(outcome) shouldBe 1
        }

        "errorJson/errorText render a CompilationError diagnostic with no location, null line/column, no '(line)'" {
            val outcome =
                KtrizScriptOutcome.CompilationError(
                    listOf(ScriptDiagnosticView("WARNING", "unused variable", null, null)),
                )
            errorJson(outcome).toString() shouldBe
                """{"status":"error","errorKind":"compilation_error","code":"KTRIZ-S-001",""" +
                """"diagnostics":[{"severity":"WARNING","message":"unused variable","line":null,"column":null}]}"""
            errorText(outcome) shouldBe "Script failed to compile:\n  [WARNING] unused variable"
            exitCodeFor(outcome) shouldBe 1
        }

        "errorJson/errorText render a RuntimeError with code KTRIZ-S-002 and captured stdout, exit 1" {
            val outcome = KtrizScriptOutcome.RuntimeError("boom", "java.lang.IllegalStateException", "output-so-far")
            errorJson(outcome).toString() shouldBe
                """{"status":"error","errorKind":"runtime_error","code":"KTRIZ-S-002",""" +
                """"message":"boom","exceptionClass":"java.lang.IllegalStateException","stdout":"output-so-far"}"""
            errorText(outcome) shouldBe "Script threw at runtime (java.lang.IllegalStateException): boom"
            exitCodeFor(outcome) shouldBe 1
        }

        "errorJson renders an empty stdout for a RuntimeError with no captured output" {
            val outcome = KtrizScriptOutcome.RuntimeError("boom", "java.lang.IllegalStateException", null)
            errorJson(outcome).toString() shouldBe
                """{"status":"error","errorKind":"runtime_error","code":"KTRIZ-S-002",""" +
                """"message":"boom","exceptionClass":"java.lang.IllegalStateException","stdout":""}"""
        }

        "errorJson/errorText render a NotEvaluated with code KTRIZ-S-003, exit 1" {
            val outcome = KtrizScriptOutcome.NotEvaluated("script did not evaluate to a result")
            errorJson(outcome).toString() shouldBe
                """{"status":"error","errorKind":"not_evaluated","code":"KTRIZ-S-003",""" +
                """"message":"script did not evaluate to a result"}"""
            errorText(outcome) shouldBe "Script did not evaluate: script did not evaluate to a result"
            exitCodeFor(outcome) shouldBe 1
        }

        "errorJson/errorText render a SourceRejected with its own code (e.g. SOURCE_REMOTE_URI), exit 1" {
            val outcome =
                KtrizScriptOutcome.SourceRejected(
                    KtrizScriptOutcomeCodes.SOURCE_REMOTE_URI,
                    "script path must be a local file path, not a URI: 'https://x'",
                )
            errorJson(outcome).toString() shouldBe
                """{"status":"error","errorKind":"source_rejected","code":"KTRIZ-S-011",""" +
                """"message":"script path must be a local file path, not a URI: 'https://x'"}"""
            errorText(outcome) shouldBe
                "Script rejected (KTRIZ-S-011): script path must be a local file path, not a URI: 'https://x'"
            exitCodeFor(outcome) shouldBe 1
        }
    })
