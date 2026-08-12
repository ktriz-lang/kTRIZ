plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("dev.ktriz.cli.MainKt")
    // Analog kUML/kSTEP: der spätere `ktriz`-Aufruf ist ein kurzlebiger One-Shot-Prozess,
    // dessen Kosten von Startup/Kompilierung dominiert werden -- Peak-Throughput ist irrelevant.
    applicationDefaultJvmArgs = listOf("-XX:TieredStopAtLevel=1")
}

dependencies {
    // Projektweite Kotlin-Konvention (CLAUDE.md): Logging ausschliesslich ueber
    // kotlin-logging, kein println-basiertes Ad-hoc-Logging. slf4j-simple als
    // Runtime-Backend, sonst bleibt kotlin-logging ein stiller NOP-Logger.
    implementation(libs.kotlin.logging.jvm)
    runtimeOnly(libs.slf4j.simple)
}
