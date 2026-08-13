plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":ktriz-core"))
    implementation(project(":ktriz-render-kuml"))
    // Nur damit CliPlaceholderTest den Platzhalter-Einstiegspunkt aufrufen kann.
    implementation(project(":ktriz-cli"))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
