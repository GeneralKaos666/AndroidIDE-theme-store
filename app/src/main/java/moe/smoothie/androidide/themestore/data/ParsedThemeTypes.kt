package moe.smoothie.androidide.themestore.data

import kotlinx.serialization.Serializable

@Serializable
data class TokenColorSetting(
    val name: String? = null,
    val scope: String? = null, // Note: In VSCode, scope can be String or List<String>. Simplified for now.
    val settings: Map<String, String>? = null // Settings are typically string key-value pairs for fontStyle, foreground, background
)

@Serializable
data class VSCodeThemeJson(
    val name: String? = null,
    val type: String? = null, // "light", "dark", "hc"
    val colors: Map<String, String>? = null, // Editor colors like "editor.background"
    val tokenColors: List<TokenColorSetting>? = null // Syntax highlighting rules
)

@Serializable
data class JetbrainsThemeJson(
    val name: String? = null,
    val dark: Boolean? = false,
    val author: String? = null,
    val editorScheme: String? = null, // Path to .icls file for editor colors
    val colors: Map<String, String>? = null, // Specific IDE component colors (e.g. "ActionButton.background")
    val ui: Map<String, String>? = null, // General UI element colors (e.g. "*": { "background": "#FFFFFF" } )
    val icons: Map<String, String>? = null // Icon color overrides (e.g. "ColorPalette": {} )
)

// Not directly from JSON, but for holding structured data from ICLS XML
data class IclsThemeData(
    val name: String? = null,
    val colors: Map<String, String> = emptyMap(), // <colors> entries
    val attributes: List<IclsAttribute> = emptyList() // <option> entries under <attributes>
)

data class IclsAttribute(
    val name: String, // e.g., "TEXT"
    val value: String? = null, // Raw value string from XML
    val foreground: String? = null,
    val background: String? = null,
    val fontType: String? = null, // "0", "1", "2", "3"
    val effectColor: String? = null,
    val effectType: String? = null, // "0", "1", "2", "3", "4", "5"
    val errorCode: String? = null // For error stripes
)
