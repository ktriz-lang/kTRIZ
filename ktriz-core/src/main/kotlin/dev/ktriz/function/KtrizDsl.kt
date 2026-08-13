package dev.ktriz.function

/**
 * Scopes builder receivers so nested `component`/`useful`/`harmful`/`insufficient`/
 * `excessive` calls cannot accidentally resolve against an outer builder -- the standard
 * `@DslMarker` pattern.
 *
 * Lives here, next to its first (and so far only) user [FunctionModelBuilder], rather than
 * in `dev.ktriz.core`. If a second builder-style DSL appears in a later wave (the planned
 * `*.ktriz.kts` scripting host is the likely candidate), promote this annotation to a shared
 * package instead of letting a second, competing `@DslMarker` spring up next to it.
 */
@DslMarker
annotation class KtrizDsl
