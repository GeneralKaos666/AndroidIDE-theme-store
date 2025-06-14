package moe.smoothie.androidide.themestore.convention

import com.android.build.api.dsl.LibraryExtension

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

import moe.smoothie.androidide.themestore.MinSdk
import moe.smoothie.androidide.themestore.TargetSdk
import moe.smoothie.androidide.themestore.configureKotlin

class LibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
                apply("org.jetbrains.kotlin.android")
            }

            extensions.configure<LibraryExtension> {
                compileSdk = TargetSdk
                defaultConfig {
                    minSdk = MinSdk
                }
                configureKotlin(this)
            }
        }
    }
}