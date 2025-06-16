package moe.smoothie.androidide.themestore.model

import kotlinx.serialization.Serializable

@Serializable
data class ThemeState(
    val name: String,
    val author: String,
    val version: String,
    val description: String,
    val themeDownloadUrl: String? = null,
    val previewImageUrl: String? = null,
    val tags: List<String> = emptyList(),
    val fileExtension: String? = null // e.g. ".jar", ".zip"
)
