package moe.smoothie.androidide.themestore

import io.gitlab.arturbosch.detekt.extensions.DetektExtension

import org.gradle.api.Project

/**
 * Configures the detekt plugin.
 */
internal fun Project.configureDetekt(extension: DetektExtension) {
    extension.apply {
        config = files("$rootDir/app/config/detekt/config.yml")
        parallel = true
        buildUponDefaultConfig = true
        autoCorrect = true
    }
}