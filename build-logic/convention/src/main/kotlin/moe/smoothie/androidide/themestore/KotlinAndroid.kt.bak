package moe.smoothie.androidide.themestore

import com.android.build.gradle.BaseExtension
//import com.android.build.api.dsl.CommonExtension

//import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
//import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.provideDelegate
import org.gradle.kotlin.dsl.withType

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.dsl.jvm.JvmTargetValidationMode
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal fun Project.configureKotlinAndroid() {
    extensions.findByType(BaseExtension::class)?.run {
          compileSdkVersion(35)

          defaultConfig {
              minSdk = 21
              //targetSdk = 28
              //versionCode = 201
              //versionName = "2.0.1"
          }

          compileOptions {
              sourceCompatibility = JavaVersion.VERSION_17
              targetCompatibility = JavaVersion.VERSION_17
              isCoreLibraryDesugaringEnabled = true
          }
      }
  
      configureKotlin()

      val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

      dependencies {
          add("coreLibraryDesugaring", libs.findLibrary("android.desugarJdkLibs").get())
      }
}

internal fun Project.configureKotlinJvm() {
    extensions.findByType(BaseExtension::class)?.run {
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        configureKotlin()
    }
}

private fun Project.configureKotlin() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            apiVersion = KotlinVersion.KOTLIN_2_0
            languageVersion = KotlinVersion.KOTLIN_2_0
            jvmTarget = JvmTarget.JVM_17
            jvmTargetValidationMode = JvmTargetValidationMode.WARNING
            val warningsAsErrors: String? by project
            allWarningsAsErrors = warningsAsErrors.toBoolean()
            freeCompilerArgs.add(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjvm-default=all"
            )
            /*
            freeCompilerArgs = freeCompilerArgs + listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-Xjvm-default=all"
            )
            */
        }
    }
}