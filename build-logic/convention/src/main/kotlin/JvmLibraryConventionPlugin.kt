package moe.smoothie.androidide.themestore.convention

import moe.smoothie.androidide.themestore.convention..configureKotlinJvm
import moe.smoothie.androidide.themestore.convention..libs

import org.gradle.api.Plugin
import org.gradle.api.Project

import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "org.jetbrains.kotlin.jvm")
            apply(plugin = "themestore.android.lint")

            configureKotlinJvm()
            dependencies {
                "testImplementation"(libs.findLibrary("kotlin.test").get())
            }
        }
    }
}