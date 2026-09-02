plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    application
}

// JDK 21, not 25: kTRIZ is distributed to end users as a CLI (see CLAUDE.md,
// "JDK-Versions-Policy (Entscheidung 2026-07-23)") -- matches every other module's
// toolchain, no per-module JDK fragmentation.
kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("dev.ktriz.mcp.KtrizMcpServerKt")
    // Reiner Nachschlage-Workload (Enum-Ausgabe + Map-Lookup): Startup dominiert, Peak-Throughput
    // ist irrelevant -- gleiche Begruendung wie in ktriz-cli.
    applicationDefaultJvmArgs = listOf("-XX:TieredStopAtLevel=1")
}

// Gradle 9 Duplikat-Strategie: das MCP-SDK ist Kotlin Multiplatform und kann je nach
// Artefakt-Variante gleichnamige Ressourcen mehrfach in den distTar/distZip-Baum ziehen
// (siehe kUMLs Multi-OS-Pipeline-Tabelle in CLAUDE.md). EXCLUDE statt FAIL, weil ein
// zweiter identischer Ressourcen-Eintrag hier keine echte Mehrdeutigkeit ist.
distributions {
    main {
        contents {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }
    }
}

dependencies {
    implementation(project(":ktriz-core"))
    implementation(libs.mcp.kotlin.sdk.server)
    // Explizit deklariert statt transitiv ueber das MCP-SDK mitgenommen: dieses Modul benutzt
    // JsonObject/buildJsonObject und Job/runBlocking in *eigenem* Quelltext -- eine bewusste,
    // begruendete Abweichung vom kstep-mcp-Muster (das sich auf die Transitivitaet verlaesst).
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    // Projektweite Kotlin-Konvention (CLAUDE.md): Logging ausschliesslich ueber kotlin-logging.
    implementation(libs.kotlin.logging.jvm)
    runtimeOnly(libs.slf4j.simple)
}
