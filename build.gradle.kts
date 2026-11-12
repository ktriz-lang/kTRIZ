import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.vanniktech.publish) apply false
}

allprojects {
    group = "dev.ktriz"
    version = "0.4.0"
}

// Maven Central publishing (prepared, nothing published yet -- see docs/release.adoc).
// Whitelist, not blacklist: only these modules are published, so a new module can never be
// published by accident. ktriz-cli and ktriz-tests are deliberately absent.
val publishedModules: Map<String, String> =
    mapOf(
        "ktriz-core" to
            "kTRIZ core: type-safe Kotlin DSL for TRIZ (39 engineering parameters, 40 inventive principles, " +
            "classical contradiction matrix, function and substance-field models). Pre-release, API unstable.",
        "ktriz-render-kuml" to
            "kTRIZ SVG rendering of function models and substance-field models, using kUML's layout engine. " +
            "Pre-release, API unstable.",
        "ktriz-script" to
            "kTRIZ scripting host for *.ktriz.kts scripts (unsandboxed: do not run untrusted scripts). " +
            "Pre-release, API unstable.",
        "ktriz-mcp" to
            "kTRIZ MCP server library exposing the TRIZ DSL as tools over stdio. Pre-release, API unstable.",
    )
val ktrizPublishingConfiguredMarker = "ktriz.publishingConfigured"

fun Project.configureKtrizPublishing(pomDescription: String) {
    check(!extra.has(ktrizPublishingConfiguredMarker)) {
        "configureKtrizPublishing() called twice for '$path'"
    }
    extra[ktrizPublishingConfiguredMarker] = true
    apply(plugin = "com.vanniktech.maven.publish")
    configure<MavenPublishBaseExtension> {
        publishToMavenCentral(automaticRelease = true)
        // Sign only when a key is supplied (CI); local publishToMavenLocal works without one.
        if (providers.gradleProperty("signingInMemoryKey").isPresent) signAllPublications()
        coordinates(groupId = "dev.ktriz", artifactId = project.name, version = project.version.toString())
        configure(KotlinJvm(javadocJar = JavadocJar.Empty(), sourcesJar = SourcesJar.Sources()))
        pom {
            name.set(project.name)
            description.set(pomDescription)
            url.set("https://github.com/ktriz-lang/kTRIZ")
            licenses {
                license {
                    name.set("Apache-2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("ibetchvaia")
                    name.set("Irakli Betchvaia")
                    email.set("irakli@betchvaia.de")
                }
            }
            scm {
                url.set("https://github.com/ktriz-lang/kTRIZ")
                connection.set("scm:git:https://github.com/ktriz-lang/kTRIZ.git")
                developerConnection.set("scm:git:ssh://git@github.com/ktriz-lang/kTRIZ.git")
            }
        }
    }
}

subprojects {
    val pomDescription = publishedModules[name] ?: return@subprojects
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") { configureKtrizPublishing(pomDescription) }
}

// Typo guard: every whitelisted name must be a real subproject.
gradle.projectsEvaluated {
    val unknown = publishedModules.keys - subprojects.map { it.name }.toSet()
    check(unknown.isEmpty()) { "publishedModules names unknown subprojects: $unknown" }
}
