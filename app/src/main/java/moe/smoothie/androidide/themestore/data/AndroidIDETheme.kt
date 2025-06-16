package moe.smoothie.androidide.themestore.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AndroidIDERootTheme(
    val name: String? = null, // Added: Name for the theme scheme itself
    val isDark: Boolean = false, // Added: To indicate if it's a dark theme
    val definitions: Map<String, String>? = null, // e.g., "my_color": "#RRGGBB"
    val editor: AndroidIDEEditorColors? = null,
    val languages: List<AndroidIDELanguageStyling>? = null
)

@Serializable
data class AndroidIDEEditorColors(
    val bg: String? = null,
    val fg: String? = null,
    @SerialName("gutter.bg")
    val gutterBg: String? = null,
    @SerialName("gutter.fg")
    val gutterFg: String? = null,
    @SerialName("caret.fg")
    val caretFg: String? = null,
    @SerialName("selection.bg")
    val selectionBg: String? = null,
    @SerialName("line.highlight.bg") // Example: current line highlight
    val lineHighlightBg: String? = null,
    @SerialName("whitespace.fg") // Example: visible whitespace
    val whitespaceFg: String? = null
    // Add more common ones as identified
)

@Serializable
data class AndroidIDELanguageStyling(
    val types: List<String>, // e.g., ["java", "xml", "kotlin", "global"] (use "global" for general token types)
    val styles: Map<String, AndroidIDEStyleEntry> // e.g., "comment": {"fg": "@some_color"}
)

@Serializable
data class AndroidIDEStyleEntry(
    val fg: String? = null,
    val bg: String? = null,
    val bold: Boolean? = null,
    val italic: Boolean? = null,
    val strikethrough: Boolean? = null
)
