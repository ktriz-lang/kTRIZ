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
    implementation(project(":ktriz-mcp"))
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mcp.kotlin.sdk.server)
    testImplementation(libs.mcp.kotlin.sdk.client)
    testImplementation(libs.mcp.kotlin.sdk.testing) // ChannelTransport
    testImplementation(libs.kotlinx.serialization.json)
    testRuntimeOnly(libs.slf4j.simple) // fuer den Logging-Test
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
