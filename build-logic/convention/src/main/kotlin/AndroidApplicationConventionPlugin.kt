package moe.smoothie.androidide.themestore.convention

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.gradle.BaseExtension

//import com.google.samples.apps.nowinandroid.configureBadgingTasks
//import com.google.samples.apps.nowinandroid.configureGradleManagedDevices
import moe.smoothie.androidide.themestore.convention.configureKotlinAndroid
import moe.smoothie.androidide.themestore.convention.configurePrintApksTask

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.application")
            apply(plugin = "org.jetbrains.kotlin.android")
            apply(plugin = "themestore.android.lint")
            //apply(plugin = "com.dropbox.dependency-guard")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = 35
                @Suppress("UnstableApiUsage")
                testOptions.animationsDisabled = true
                //configureGradleManagedDevices(this)
            }
            extensions.configure<ApplicationAndroidComponentsExtension> {
                configurePrintApksTask(this)
                //configureBadgingTasks(extensions.getByType<BaseExtension>(), this)
            }
        }
    }
}