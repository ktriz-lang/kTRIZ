pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "kTRIZ"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    "ktriz-core",
    "ktriz-cli",
    "ktriz-render-kuml",
    "ktriz-tests",
)
