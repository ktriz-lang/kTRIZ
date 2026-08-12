package dev.ktriz.cli

/**
 * Placeholder entry point. kTRIZ M1 Welle 1 ships the module skeleton only -- there is no
 * command surface yet, on purpose (see the project note's "Bewusst nicht in M1"). Real
 * commands arrive in a later wave together with the `*.ktriz.kts` scripting host and the
 * MCP server; this `main` exists so the module, its `application` wiring and its
 * distribution tasks are exercised from day one instead of being introduced untested later.
 *
 * Prints to stdout and exits 0. Deliberately does not depend on `ktriz-core`.
 */
fun main(args: Array<String>) {
    println("kTRIZ CLI -- placeholder, no commands implemented yet.")
    println("kTRIZ is in early development (M1 Welle 1: core DSL skeleton only).")
    if (args.isNotEmpty()) {
        println("Ignored arguments: ${args.joinToString(separator = " ")}")
    }
}
