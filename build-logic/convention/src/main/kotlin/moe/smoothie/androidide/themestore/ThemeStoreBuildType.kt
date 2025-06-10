package moe.smoothie.androidide.themestore

/**
 * This is shared between :app and :benchmarks module to provide configurations type safety.
 */
@Suppress("unused")
enum class ThemeStoreBuildType(val applicationIdSuffix: String? = null) {
    DEBUG(".debug"),
    RELEASE
}