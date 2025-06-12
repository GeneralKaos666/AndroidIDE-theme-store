import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.dsl.jvm.JvmTargetValidationMode
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

group = "moe.smoothie.androidide.themestore.buildlogic"

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
      //apiVersion = KotlinVersion.KOTLIN_2_0
      //languageVersion = KotlinVersion.KOTLIN_2_0
      jvmTarget = JvmTarget.JVM_17
      //jvmTargetValidationMode = JvmTargetValidationMode.WARNING
      //freeCompilerArgs.add("-Xjvm-default=all")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplicationCompose") {
            id = "themestore.android.application.compose"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }

        register("androidApplication") {
            id = "themestore.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }

        register("androidLibraryCompose") {
            id = "themestore.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }

        register("androidLibrary") {
            id = "themestore.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        
        register("androidFeature") {
            id = "themestore.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }

        register("androidTest") {
            id = "themestore.android.test"
            implementationClass = "AndroidTestConventionPlugin"
        }

        register("androidHilt") {
            id = "themestore.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }

        register("jvmLibrary") {
            id = "volution.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }

        register("kotlinDetekt") {
            id = "themestore.kotlin.detekt"
            implementationClass = "DetektConventionPlugin"
        }
    }
}