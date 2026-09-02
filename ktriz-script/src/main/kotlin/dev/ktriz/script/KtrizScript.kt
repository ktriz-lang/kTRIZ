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
 * `Contradiction`, `contradiction()`, `ContradictionMatrix`, `Contradiction.resolve()`),
 * `dev.ktriz.function.*` (`functionModel { }`, `FunctionModel`, `Component`, `FunctionEdge`,
 * `FunctionQuality`), `dev.ktriz.sufield.*` (`suField { }`, `SuField`, `FieldType`,
 * `SuFieldQuality`, `StandardSolutionClass`), and `dev.ktriz.render.kuml.*`
 * (`FunctionModel.renderSvg()`, `SuField.renderSvg()`) available without explicit imports.
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
 *
 * Since `ktriz-script` now declares `api(project(":ktriz-render-kuml"))` (see
 * `ktriz-script/build.gradle.kts`), that module -- and its transitive dependencies
 * `kuml-layout-api`/`kuml-layout-elk` (which pull in ELK, EMF, and Guava; ~15 additional jars)
 * -- also ride along on `wholeClasspath`, so `FunctionModel.renderSvg()` is callable from a
 * script the same way `EngineeringParameter`/`functionModel { }` already were.
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
        // suField { }, SuField, SuFieldBuilder, FieldType, SuFieldQuality,
        // StandardSolutionClass -- verified collision-free against dev.ktriz.core.* and
        // dev.ktriz.function.*. Without this, suField { } would be the only DSL entry point in
        // the project that a script could not call unqualified.
        "dev.ktriz.sufield.*",
        // FunctionModel.renderSvg() / SuField.renderSvg() -- extension functions, so they need
        // this import to resolve inside a script even though ktriz-render-kuml is already on
        // the classpath (see this object's KDoc above).
        "dev.ktriz.render.kuml.*",
    )
})
