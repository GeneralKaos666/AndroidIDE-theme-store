package moe.smoothie.androidide.themestore.vscode

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VSCodeTheme(
    val name: String? = null,
    val type: String? = null, // "light" or "dark"
    val colors: Map<String, String>? = null, // General editor colors like "editor.background"
    @SerialName("tokenColors")
    val tokenColors: List<VSCodeTokenColor>? = null
)

@Serializable
data class VSCodeTokenColor(
    val name: String? = null,
    val scope: List<String>? = null, // Can be a list or a single string (adapter might be needed if mixed)
    // val scope: String? = null, // Simpler if always a single string
    val settings: VSCodeTokenSettings? = null
)

@Serializable
data class VSCodeTokenSettings(
    val foreground: String? = null,
    val background: String? = null,
    val fontStyle: String? = null // e.g., "italic", "bold underline"
)
