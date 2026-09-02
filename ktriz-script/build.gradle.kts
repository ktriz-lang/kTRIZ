plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

// JDK 21, nicht 25: kTRIZ wird als Endnutzer-CLI verteilt (CLAUDE.md, JDK-Versions-Policy).
kotlin {
    jvmToolchain(21)
}

dependencies {
    // `api`, nicht `implementation`: ktriz-core-Typen muessen *innerhalb* von
    // *.ktriz.kts aufloesbar sein (dependenciesFromCurrentContext(wholeClasspath = true)
    // liest java.class.path; defaultImports referenzieren dev.ktriz.core.* und
    // dev.ktriz.function.*), nicht nur aus dem Quelltext dieses Moduls. Mit `implementation`
    // kompiliert dieses Modul selbst noch, aber ktriz-core waere fuer ein Skript, das ueber
    // wholeClasspath aufgeloest wird, nicht zuverlaessig sichtbar -- Skripte scheitern dann
    // mit "unresolved reference: EngineeringParameter", was wie ein Skriptfehler *aussieht*.
    api(project(":ktriz-core"))

    implementation(libs.kotlin.scripting.common)
    implementation(libs.kotlin.scripting.jvm)
    implementation(libs.kotlin.scripting.jvm.host)
    implementation(libs.kotlin.logging.jvm)
}
