plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
}

// JDK 21, not 25: this is a library module of kTRIZ, which is distributed to end users
// as a CLI (see CLAUDE.md, "JDK-Versions-Policy (Entscheidung 2026-07-23)") -- matches
// ktriz-core's toolchain, no per-module JDK fragmentation.
kotlin {
    jvmToolchain(21)
}

dependencies {
    // FunctionModel is part of this module's own public API surface
    // (FunctionModel.renderSvg()'s receiver type), so `api`, not `implementation`.
    api(project(":ktriz-core"))

    // kUML consumed strictly as a layout library (kTRIZ-ADR-0002, "Update 2026-08-13") --
    // never kuml-io-svg or any kUML metamodel module. Neither kuml-layout-api's nor
    // kuml-layout-elk's types (LayoutGraph, LayoutResult, ElkLayoutEngine, ...) appear in
    // this module's own public API (renderSvg() returns String), so both stay
    // `implementation`, not `api`. kuml-layout-api is Kotlin Multiplatform (jvm/js/wasmJs);
    // Gradle Module Metadata resolves its -jvm variant automatically for this JVM module.
    implementation(libs.kuml.layout.api)
    implementation(libs.kuml.layout.elk)
}
