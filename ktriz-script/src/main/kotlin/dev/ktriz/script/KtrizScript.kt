package dev.ktriz.script

import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm

/**
 * kTRIZ script definition.
 *
 * Files with the extension `*.ktriz.kts` are compiled and evaluated using this definition.
 * The [defaultImports] make `dev.ktriz.core.*` (`EngineeringParameter`, `InventivePrinciple`,
 * `Contradiction`, `contradiction()`, `ContradictionMatrix`, `Contradiction.resolve()`) and
 * `dev.ktriz.function.*` (`functionModel { }`, `FunctionModel`, `Component`, `FunctionEdge`,
 * `FunctionQuality`) available without explicit imports.
 *
 * Minimal script example:
 * ```kotlin
 * // hello-world.ktriz.kts
 * val problem = contradiction(
 *     improving = EngineeringParameter.WEIGHT_OF_MOVING_OBJECT,
 *     worsening = EngineeringParameter.STRENGTH,
 * )
 * val principles = problem.resolve()
 * println("Contradiction: ${problem.improving.labelEn} vs. ${problem.worsening.labelEn}")
 * principles.forEach { p -> println("  #${p.id}  ${p.labelEn}") }
 * ```
 *
 * `import dev.ktriz.core.*` (or `import dev.ktriz.function.*`) in a script is *redundant, but
 * allowed* -- both packages are already in [defaultImports], kept here purely for
 * backwards-compatibility with the DSL-surface note's example script, which writes the import
 * explicitly.
 *
 * See [KtrizScriptHost] for how a script is actually run and for why it is deliberately not
 * sandboxed.
 */
@KotlinScript(
    displayName = "kTRIZ Script",
    fileExtension = "ktriz.kts",
    compilationConfiguration = KtrizScriptCompilationConfiguration::class,
)
abstract class KtrizScript

/**
 * Compilation configuration for `*.ktriz.kts` scripts.
 *
 * Uses [dependenciesFromCurrentContext] with `wholeClasspath = true` so the full classpath of
 * the calling JVM (which includes `ktriz-core` and this module) is available inside scripts
 * without explicit dependency declarations. This is the *trusted, in-process* path only -- see
 * [KtrizScriptHost]'s KDoc for why no curated/sandboxed classpath is used here.
 */
object KtrizScriptCompilationConfiguration : ScriptCompilationConfiguration({
    jvm {
        dependenciesFromCurrentContext(wholeClasspath = true)
    }
    defaultImports(
        // EngineeringParameter, InventivePrinciple, Contradiction, contradiction(),
        // ContradictionMatrix, ContradictionMatrixData, Contradiction.resolve()
        "dev.ktriz.core.*",
        // functionModel { }, FunctionModel, FunctionModelBuilder, Component,
        // FunctionEdge, FunctionQuality
        "dev.ktriz.function.*",
    )
})
