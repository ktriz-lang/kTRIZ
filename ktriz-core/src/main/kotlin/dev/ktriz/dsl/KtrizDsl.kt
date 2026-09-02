package dev.ktriz.dsl

/**
 * Scopes builder receivers so nested DSL calls (`component`/`useful`/`harmful`/
 * `insufficient`/`excessive`/`s1`/`s2`/`field`/`quality`, ...) cannot accidentally resolve
 * against an outer builder -- the standard `@DslMarker` pattern.
 *
 * Shared infrastructure, not a TRIZ domain concept -- lives in its own `dev.ktriz.dsl`
 * package rather than in `dev.ktriz.core` (the TRIZ parameter/principle/matrix domain
 * package) or next to any single builder. Used by [dev.ktriz.function.FunctionModelBuilder]
 * and [dev.ktriz.sufield.SuFieldBuilder]. Originally lived next to
 * `FunctionModelBuilder` as its first (and at the time only) user, and was promoted here
 * once a second builder-style DSL (`suField { }`) appeared, exactly as its original KDoc
 * anticipated. If a third builder-style DSL appears (the planned ARIZ-workflow DSL is the
 * likely candidate), it belongs here too rather than minting a competing `@DslMarker`.
 */
@DslMarker
annotation class KtrizDsl
