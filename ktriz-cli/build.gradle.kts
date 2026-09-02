plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("dev.ktriz.cli.MainKt")
    // Analog kUML/kSTEP: das installDist-/distTar-Executable heisst `ktriz`, nicht der
    // Gradle-Modulname `ktriz-cli` (Gradle's application-Plugin leitet applicationName sonst
    // aus dem Projektnamen ab). USAGE_TEXT und README.adoc (Abschnitt <<scripting>>)
    // dokumentieren durchgaengig `ktriz run ...` / `ktriz help` -- ohne dieses applicationName
    // heisst das gebaute Binary `ktriz-cli`, und ein Nutzer, der die vom Programm selbst
    // ausgegebene Usage-Zeile abtippt, bekaeme "command not found".
    applicationName = "ktriz"
    // Analog kUML/kSTEP: der spätere `ktriz`-Aufruf ist ein kurzlebiger One-Shot-Prozess,
    // dessen Kosten von Startup/Kompilierung dominiert werden -- Peak-Throughput ist irrelevant.
    applicationDefaultJvmArgs = listOf("-XX:TieredStopAtLevel=1")
}

dependencies {
    // `ktriz run` (M1 Welle 6): KtrizScriptHost/KtrizScriptOutcome. Zieht
    // kotlin-compiler-embeddable transitiv auf diesen runtimeClasspath (und damit in
    // installDists lib/) -- erwartet, kein Regress; siehe ktriz-scripts KDoc.
    implementation(project(":ktriz-script"))
    // Rendert das strukturierte Ergebnis-/Fehlerdokument von `--output json`.
    implementation(libs.kotlinx.serialization.json)

    // Projektweite Kotlin-Konvention (CLAUDE.md): Logging ausschliesslich ueber
    // kotlin-logging, kein println-basiertes Ad-hoc-Logging. slf4j-simple als
    // Runtime-Backend, sonst bleibt kotlin-logging ein stiller NOP-Logger. Ausnahme siehe
    // Kommentar in Main.kt: die eigentliche CLI-Ergebnisausgabe (USAGE_TEXT, Skript-Text-
    // /JSON-Resultat) bleibt bewusst plain println, nicht kotlin-logging.
    implementation(libs.kotlin.logging.jvm)
    runtimeOnly(libs.slf4j.simple)
}
