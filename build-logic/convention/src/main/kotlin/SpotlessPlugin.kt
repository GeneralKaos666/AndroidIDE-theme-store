package moe.smoothie.androidide.themestore.convention

import com.diffplug.gradle.spotless.SpotlessExtension

import org.gradle.api.Plugin
import org.gradle.api.Project

import org.gradle.kotlin.dsl.configure

class SpotlessPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.diffplug.spotless")
            }
            extensions.configure<SpotlessExtension> {
                format("misc") {
                    formatExtension -> with(formatExtension) {
                        target("*.md", ".gitignore")
                        trimTrailingWhitespace()
                        endWithNewline()
                    }
                }
                kotlin { kotlinExtension -> with(kotlinExtension) {
                ktfmt(ktfmtVersion).kotlinlangStyle()
                trimTrailingWhitespace()
                endWithNewline()
                licenseHeaderFile(file("$rootDir/spotless/spotless.kt"))
            }
        }
        kotlinGradle { kotlinGradleExtension ->
            with(kotlinGradleExtension) {
                ktfmt(ktfmtVersion).kotlinlangStyle()
                trimTrailingWhitespace()
                endWithNewline()
                licenseHeaderFile(
                    file("$rootDir/spotless/spotless.kt"),
                    "(import|plugins|buildscript|dependencies|pluginManagement)",
                )
            }
        }
    }
}