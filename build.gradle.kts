plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
}

spotless {
    // Explicit source roots: a "**" glob would also walk build/ while other tasks write to it.
    kotlin {
        target("gesture/src/**/*.kt", "hid/src/**/*.kt", "app/src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts", "gesture/*.gradle.kts", "hid/*.gradle.kts", "app/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

// detekt runs as its standalone CLI in an isolated configuration. This keeps detekt's own
// Kotlin compiler (2.0.21) off the build classpath and avoids depending on the detekt Gradle
// plugin's compatibility with Gradle 9.
val detektCli: Configuration by configurations.creating

dependencies {
    detektCli(libs.detekt.cli)
}

val detekt by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs detekt (without type resolution) on all Kotlin sources."
    val sourceDirs =
        listOf("gesture", "hid", "app")
            .flatMap { module ->
                listOf(
                    "src/main/kotlin",
                    "src/test/kotlin",
                    "src/testFixtures/kotlin",
                    "src/debug/kotlin",
                    "src/androidTest/kotlin",
                ).map { layout.projectDirectory.dir("$module/$it").asFile }
            }.filter { it.exists() }
    val config = layout.projectDirectory.file("config/detekt/detekt.yml")
    val report = layout.buildDirectory.file("reports/detekt/detekt.txt")
    inputs.files(sourceDirs).withPropertyName("sources")
    inputs.file(config).withPropertyName("config")
    outputs.file(report)
    classpath = detektCli
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    args(
        "--input",
        sourceDirs.joinToString(",") { it.absolutePath },
        "--config",
        config.asFile.absolutePath,
        "--build-upon-default-config",
        "--base-path",
        layout.projectDirectory.asFile.absolutePath,
        "--report",
        "txt:${report.get().asFile.absolutePath}",
    )
}
