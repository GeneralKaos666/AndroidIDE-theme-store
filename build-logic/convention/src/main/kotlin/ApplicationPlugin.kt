import com.android.build.api.dsl.ApplicationExtension

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

import moe.smoothie.androidide.themestore.MinSdk
import moe.smoothie.androidide.themestore.TargetSdk
import moe.smoothie.androidide.themestore.configureKotlin

class ApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("org.jetbrains.kotlin.android")
                apply("com.google.gms.google-services")
                apply("com.google.firebase.firebase-perf")
                apply("com.google.firebase.crashlytics")
            }

            extensions.configure<ApplicationExtension> {
                defaultConfig {
                    targetSdk = TargetSdk
                    compileSdk = TargetSdk
                    minSdk = MinSdk

                    multiDexEnabled = true
                    vectorDrawables.useSupportLibrary = true
                }

                compileOptions {
                    isCoreLibraryDesugaringEnabled = true
                }

                buildTypes {
                    getByName("release") {
                        isMinifyEnabled = true
                        isShrinkResources = true
                    }
                    getByName("debug") {
                        isMinifyEnabled = false
                        isShrinkResources = false
                    }
                }

                configureKotlin(this)
            }


            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                val bom = libs.findLibrary("firebase-bom").get()
                add("implementation", platform(bom))
                add("coreLibraryDesugaring", libs.findLibrary("android.desugarJdkLibs").get())
                "implementation"(libs.findLibrary("firebase.analytics").get())
                "implementation"(libs.findLibrary("firebase.performance").get())
                "implementation"(libs.findLibrary("firebase.crashlytics").get())
            }
        }
    }
}