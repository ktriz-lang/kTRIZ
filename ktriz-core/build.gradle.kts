plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

// JDK 21, nicht 25: kTRIZ wird (wie kUML und kSTEP) als Endnutzer-CLI verteilt.
// Siehe CLAUDE.md, "JDK-Versions-Policy (Entscheidung 2026-07-23)".
kotlin {
    jvmToolchain(21)
}

dependencies {
    // Projektweite Kotlin-Konvention (CLAUDE.md): Logging ausschliesslich ueber kotlin-logging.
    // Nur die Fassade, kein Backend -- ktriz-core ist eine Bibliothek; die lauffaehigen Module
    // (ktriz-cli, ktriz-mcp, ktriz-script) bringen ein SLF4J-Backend selbst mit.
    implementation(libs.kotlin.logging.jvm)
}
