import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

group = "moe.smoothie.androidide.themestore.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.spotless.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("themestore.hilt") {
            id = "themestore.hilt"
            implementationClass = "HiltPlugin"
        }
        register("themestore.library") {
            id = "themestore.library"
            implementationClass = "LibraryPlugin"
        }
        register("themestore.application") {
            id = "themestore.application"
            implementationClass = "ApplicationPlugin"
        }
        register("themestore.spotless") {
            id = "themestore.spotless"
            implementationClass = "SpotlessPlugin"
        }
        register("themestore.detekt") {
            id = "themestore.detekt"
            implementationClass = "detektPlugin"
        }
        register("themestore.ksp") {
            id = "themestore.ksp"
            implementationClass = "kspPlugin"
        }
    }
}