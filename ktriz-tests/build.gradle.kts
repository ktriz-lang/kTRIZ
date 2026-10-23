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
    // Fuer CliMainTest (resolveCommand/USAGE_TEXT) und die ktriz-cli-installDist-Verifikation.
    implementation(project(":ktriz-cli"))
    implementation(project(":ktriz-mcp"))
    // Fuer KtrizScriptHostTest/KtrizScriptHelloWorldTest.
    implementation(project(":ktriz-script"))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mcp.kotlin.sdk.server)
    testImplementation(libs.mcp.kotlin.sdk.client)
    testImplementation(libs.mcp.kotlin.sdk.testing) // ChannelTransport
    testImplementation(libs.kotlinx.serialization.json)
    testRuntimeOnly(libs.slf4j.simple) // fuer den Logging-Test
}

// The calibration CSV lives at the repository root (single source); tests run with the module
// directory as working dir, so the absolute path is handed in as a system property.
val calibrationCsv = rootProject.layout.projectDirectory.file("benchmark/calibration-set.csv")

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    inputs.file(calibrationCsv).withPropertyName("calibrationCsv")
    systemProperty("ktriz.calibration.csv", calibrationCsv.asFile.absolutePath)
    systemProperty("java.awt.headless", "true")
    systemProperty("ktriz.expected.version", project.version.toString())
}
