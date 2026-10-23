package dev.ktriz.tests

import dev.ktriz.evolution.EvolutionLaw
import dev.ktriz.script.KtrizScriptHost
import dev.ktriz.script.KtrizScriptOutcome
import io.kotest.core.annotation.Isolate
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

// @Isolate: evaluates with captureStdout = true, which swaps System.out.
@Isolate
class EvolutionLawScriptTest :
    StringSpec({
        "evolution-laws.ktriz.kts runs with no explicit imports" {
            val name = "evolution-laws.ktriz.kts"
            val loader = Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()
            val text =
                (loader.getResourceAsStream(name) ?: error("fixture $name missing"))
                    .reader(Charsets.UTF_8)
                    .use { it.readText() }
            val outcome = KtrizScriptHost.evalSource(text, fileName = name, captureStdout = true)
            outcome.shouldBeInstanceOf<KtrizScriptOutcome.Success>()
            val out = outcome.capturedStdout.shouldNotBeNull()
            EvolutionLaw.entries.forEach { out shouldContain it.labelEn }
            out shouldContain "STATICS"
            out shouldContain "KINEMATICS"
            out shouldContain "DYNAMICS"
        }
    })
