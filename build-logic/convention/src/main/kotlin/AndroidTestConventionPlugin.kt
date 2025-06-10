import com.android.build.gradle.TestExtension

import moe.smoothie.androidide.themestore.configureGradleManagedDevices
import moe.smoothie.androidide.themestore.configureKotlinAndroid

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.test")
                apply("org.jetbrains.kotlin.android")
            }

            extensions.configure<TestExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = 35
                configureGradleManagedDevices(this)
            }
        }
    }
}