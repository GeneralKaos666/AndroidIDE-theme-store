package moe.smoothie.androidide.themestore.convention

import io.gitlab.arturbosch.detekt.extensions.DetektExtension

import org.gradle.api.Project

/**
 * Configures the detekt plugin.
 */
internal fun Project.configureDetekt(extension: DetektExtension) {
    extension.apply {
        config = files("$rootDir/config/detekt/config.yml")
        parallel = true
        buildUponDefaultConfig = true
        autoCorrect = true
    }
}