plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

// JDK 21, nicht 25: kTRIZ wird (wie kUML und kSTEP) als Endnutzer-CLI verteilt.
// Siehe CLAUDE.md, "JDK-Versions-Policy (Entscheidung 2026-07-23)".
kotlin {
    jvmToolchain(21)
}
