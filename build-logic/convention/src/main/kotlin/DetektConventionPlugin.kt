//package moe.smoothie.androidide.themestore.convention

//import io.gitlab.arturbosch.detekt.extensions.DetektExtension

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.configure

/**
 * Configures the detekt plugin
 */
 class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("io.gitlab.arturbosch.detekt")
                val extension = extensions.getByType<DetektExtension>()
                  configureDetekt(extension)
            
                val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
                dependencies {
                    "detektPlugins"(libs.findLibrary("detekt-formatting").get())
                }
            }
        }
    }
}

internal fun Project.configureDetekt(extension: DetektExtension) {
    extension.apply {
        config = files("$rootDir/config/detekt/config.yml")
        parallel = true
        buildUponDefaultConfig = true
        autoCorrect = true
    }
}