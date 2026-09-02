package dev.ktriz.cli

import dev.ktriz.script.KtrizScriptHost
import dev.ktriz.script.KtrizScriptOutcome
import dev.ktriz.script.KtrizScriptOutcomeCodes
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.system.exitProcess

// Deliberately plain println, not kotlin-logging, for USAGE_TEXT and every piece of `ktriz
// run`'s own result rendering (errorText/errorJson/the success path below): this is the CLI's
// own stdout output -- the thing the user invoked the tool to see -- not diagnostic logging.
// A kotlin-logging call would go through a logger backend (slf4j-simple here) that prefixes
// every line with a level/timestamp and writes to stderr, which would (a) make `--output json`
// unparsable for a machine consumer expecting exactly one JSON document on stdout and (b)
// misrepresent a command's own result as a log line. See ktriz-script's KtrizScriptHost for the
// one place this wave *does* log diagnostically (an unexpected host-level failure) -- mirrors
// kSTEP's kstep-cli, which draws the identical line for its `kstep export` command.
const val USAGE_TEXT: String =
    """kTRIZ CLI

Usage:
  ktriz run <script.ktriz.kts> [--output json]   Compile and run a *.ktriz.kts script
  ktriz help                                     Show this message

A *.ktriz.kts script is arbitrary Kotlin and runs with the full rights of the
invoking user. There is no sandbox. Only run scripts you trust, the same way
you would a build.gradle.kts.
"""

sealed interface CliCommand {
    data class Run(
        val scriptPath: String,
        val jsonOutput: Boolean,
    ) : CliCommand

    data class ShowUsage(
        val exitCode: Int,
    ) : CliCommand
}

// Pure Array<String> -> CliCommand mapping, deliberately free of I/O and exitProcess side
// effects, so it's directly testable from ktriz-tests without forking a subprocess (see
// CliMainTest -- calling main() itself in-process is unsafe because its error path calls
// exitProcess, which would kill the whole test JVM).
fun resolveCommand(args: Array<String>): CliCommand =
    when {
        args.isEmpty() -> CliCommand.ShowUsage(exitCode = 0)
        args.size == 1 && (args[0] == "help" || args[0] == "--help") -> CliCommand.ShowUsage(exitCode = 0)
        args[0] == "run" -> resolveRunCommand(args.drop(1))
        else -> CliCommand.ShowUsage(exitCode = 1)
    }

// Small hand-rolled flag loop -- deliberately no argument-parsing library (matches kSTEP's
// kstep-cli stance at this scope). Any malformed flag combination (no script path, `--output`
// with no value or a value other than "json", an unknown flag, or more than one positional
// argument) resolves to ShowUsage(1), never a partially-filled CliCommand.Run.
private fun resolveRunCommand(rest: List<String>): CliCommand {
    var scriptPath: String? = null
    var jsonOutput = false
    var i = 0
    while (i < rest.size) {
        when (rest[i]) {
            "--output" -> {
                val value = rest.getOrNull(i + 1) ?: return CliCommand.ShowUsage(1)
                if (value != "json") return CliCommand.ShowUsage(1)
                jsonOutput = true
                i += 2
            }
            else -> {
                val arg = rest[i]
                if (arg.startsWith("--") || scriptPath != null) return CliCommand.ShowUsage(1)
                scriptPath = arg
                i += 1
            }
        }
    }
    val resolvedScriptPath = scriptPath ?: return CliCommand.ShowUsage(1)
    return CliCommand.Run(scriptPath = resolvedScriptPath, jsonOutput = jsonOutput)
}

fun main(args: Array<String>) {
    // kotlin-logging prints a one-time "kotlin-logging: initializing... active logger factory:
    // ..." banner to stdout (not stderr) the first time any KotlinLogging.logger{} is touched
    // anywhere in the JVM -- here, that's ktriz-script's KtrizScriptHost the first time `run`
    // calls eval(). Left enabled, that banner lands on the same stdout stream this command's
    // own `--output json` document uses, breaking "exactly one JSON document on stdout" for a
    // machine consumer (caught by running `ktriz run ... --output json` for real during this
    // wave's verification, not by a unit test -- CliMainTest never invokes main() itself, see
    // its own KDoc). Must be set before anything can log, so it's the first line of main().
    KotlinLoggingConfiguration.logStartupMessage = false
    when (val command = resolveCommand(args)) {
        is CliCommand.Run -> runScript(command)
        is CliCommand.ShowUsage -> {
            // Success path (`ktriz help`, exitCode 0) is what the user explicitly asked to see
            // -- that's the CLI's own result output, so it belongs on stdout like everything
            // else in runScript() below. The error path (a malformed invocation, exitCode 1) is
            // different: it is diagnostic ("you got the invocation wrong"), not the requested
            // result, and a caller that passed --output json expects at most one JSON document
            // on stdout (see runScript's KDoc and README's <<scripting>> contract) -- a plain-
            // text usage banner on stdout ahead of that would break any such consumer's parser.
            // stderr is exempt from that contract, so the banner goes there whenever this is an
            // error, not a requested help screen.
            if (command.exitCode != 0) {
                System.err.println(USAGE_TEXT)
                exitProcess(command.exitCode)
            } else {
                println(USAGE_TEXT)
            }
        }
    }
}

// `captureStdout = command.jsonOutput` is load-bearing, not incidental: without it, a script's
// own println output would land on the real stdout ahead of the JSON document this function
// prints, and a machine consumer parsing `--output json` would not get valid JSON. In text
// mode, capture stays off on purpose -- the script's own output streams directly to stdout as
// it runs, matching `kotlinc -script` behaviour.
private fun runScript(command: CliCommand.Run) {
    val outcome = KtrizScriptHost.eval(command.scriptPath, captureStdout = command.jsonOutput)
    when (outcome) {
        is KtrizScriptOutcome.Success -> {
            if (command.jsonOutput) println(successJson(outcome).toString())
            // Text mode: the script has already printed its own output directly to stdout as
            // it ran -- nothing further to print here. Exit 0 (fall-through).
        }
        else -> {
            if (command.jsonOutput) {
                println(errorJson(outcome).toString())
            } else {
                println(errorText(outcome))
            }
        }
    }
    val exitCode = exitCodeFor(outcome)
    if (exitCode != 0) exitProcess(exitCode)
}

// The exit-code half of `ktriz run`'s contract (README's <<scripting>> section): Success is
// exit 0, every other KtrizScriptOutcome variant -- including SourceRejected, the security
// gate's own outcome -- is exit 1. Pulled out of runScript so CliMainTest can pin it against
// every KtrizScriptOutcome variant directly, without going through main()'s exitProcess.
// Public for the same reason resolveCommand above is public, not internal: ktriz-tests is a
// separate Gradle module, and this project's kotlin("jvm") setup (no Kotlin Multiplatform
// source-set graph) has no supported way to grant a sibling module friend-module access to
// `internal` declarations -- there is no public `friendPaths` on the compile task in this
// Kotlin Gradle Plugin version (verified against 2.4.10's actual API surface, not assumed).
fun exitCodeFor(outcome: KtrizScriptOutcome): Int =
    when (outcome) {
        is KtrizScriptOutcome.Success -> 0
        is KtrizScriptOutcome.CompilationError,
        is KtrizScriptOutcome.RuntimeError,
        is KtrizScriptOutcome.NotEvaluated,
        is KtrizScriptOutcome.SourceRejected,
        -> 1
    }

// Exhaustive `when` with no `else` branch on purpose, both here and in errorJson/successJson's
// callers below: a future KtrizScriptOutcome case must fail the compile here, not silently fall
// through and be treated as an unhandled success. Public, not private: see exitCodeFor's KDoc
// above for why (CliMainTest, in ktriz-tests, pins this against the documented text-mode and
// `--output json` contract for every KtrizScriptOutcome variant).
fun errorText(outcome: KtrizScriptOutcome): String =
    when (outcome) {
        is KtrizScriptOutcome.Success -> error("errorText called with a Success outcome")
        is KtrizScriptOutcome.CompilationError ->
            buildString {
                appendLine("Script failed to compile:")
                outcome.diagnostics.forEach { d ->
                    val location =
                        if (d.line != null) {
                            " (line ${d.line}${d.column?.let { ", column $it" } ?: ""})"
                        } else {
                            ""
                        }
                    appendLine("  [${d.severity}]$location ${d.message}")
                }
            }.trimEnd()
        is KtrizScriptOutcome.RuntimeError ->
            "Script threw at runtime (${outcome.exceptionClass}): ${outcome.message}"
        is KtrizScriptOutcome.NotEvaluated -> "Script did not evaluate: ${outcome.message}"
        is KtrizScriptOutcome.SourceRejected -> "Script rejected (${outcome.code}): ${outcome.message}"
    }

fun successJson(outcome: KtrizScriptOutcome.Success): JsonObject =
    buildJsonObject {
        put("status", "success")
        put("returnType", outcome.returnTypeName)
        put("stdout", outcome.capturedStdout ?: "")
    }

fun errorJson(outcome: KtrizScriptOutcome): JsonObject =
    when (outcome) {
        is KtrizScriptOutcome.Success -> error("errorJson called with a Success outcome")
        is KtrizScriptOutcome.CompilationError ->
            buildJsonObject {
                put("status", "error")
                put("errorKind", "compilation_error")
                put("code", KtrizScriptOutcomeCodes.COMPILATION_ERROR)
                putJsonArray("diagnostics") {
                    outcome.diagnostics.forEach { d ->
                        add(
                            buildJsonObject {
                                put("severity", d.severity)
                                put("message", d.message)
                                put("line", d.line)
                                put("column", d.column)
                            },
                        )
                    }
                }
            }
        is KtrizScriptOutcome.RuntimeError ->
            buildJsonObject {
                put("status", "error")
                put("errorKind", "runtime_error")
                put("code", KtrizScriptOutcomeCodes.RUNTIME_ERROR)
                put("message", outcome.message)
                put("exceptionClass", outcome.exceptionClass)
                put("stdout", outcome.capturedStdout ?: "")
            }
        is KtrizScriptOutcome.NotEvaluated ->
            buildJsonObject {
                put("status", "error")
                put("errorKind", "not_evaluated")
                put("code", KtrizScriptOutcomeCodes.NOT_EVALUATED)
                put("message", outcome.message)
            }
        is KtrizScriptOutcome.SourceRejected ->
            buildJsonObject {
                put("status", "error")
                put("errorKind", "source_rejected")
                put("code", outcome.code)
                put("message", outcome.message)
            }
    }
