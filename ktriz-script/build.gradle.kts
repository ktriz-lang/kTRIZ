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

    // `api`, not `implementation`, for the same reason as `ktriz-core` above:
    // `FunctionModel.renderSvg()` (this module's public symbol) must be resolvable on
    // *consumers'* compile classpath (ktriz-script itself references it via defaultImports
    // below), and dependenciesFromCurrentContext(wholeClasspath = true) needs it reachable
    // from ktriz-cli's runtime classpath. ktriz-render-kuml itself declares kuml-layout-api/
    // kuml-layout-elk as `implementation` (those types aren't part of renderSvg()'s public
    // signature), so this single `api` edge is enough for ELK/EMF/Guava to also land
    // transitively on ktriz-cli's runtime classpath -- verified via
    // `./gradlew :ktriz-cli:dependencies --configuration runtimeClasspath`.
    api(project(":ktriz-render-kuml"))

    implementation(libs.kotlin.scripting.common)
    implementation(libs.kotlin.scripting.jvm)
    implementation(libs.kotlin.scripting.jvm.host)
    implementation(libs.kotlin.logging.jvm)
}
