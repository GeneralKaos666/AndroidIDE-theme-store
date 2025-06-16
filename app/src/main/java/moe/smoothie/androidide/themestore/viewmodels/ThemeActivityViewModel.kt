package moe.smoothie.androidide.themestore.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.smoothie.androidide.themestore.data.AndroidIDEEditorColors
import moe.smoothie.androidide.themestore.data.AndroidIDELanguageStyling
import moe.smoothie.androidide.themestore.data.AndroidIDERootTheme
import moe.smoothie.androidide.themestore.data.AndroidIDEStyleEntry
import moe.smoothie.androidide.themestore.data.IclsThemeData
import moe.smoothie.androidide.themestore.data.JetbrainsThemeJson
import moe.smoothie.androidide.themestore.data.VSCodeThemeJson
import moe.smoothie.androidide.themestore.model.StoreType
import moe.smoothie.androidide.themestore.util.unzip
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import android.os.Environment // Required for public directory access


class ThemeActivityViewModel(
    private val httpClient: OkHttpClient,
    private val url: String, // This is the themeDownloadUrl
    private val themeName: String,
    private val storeType: StoreType
) : ViewModel() {
    private val tag = "ThemeActivityViewModel" // For logging
    private val jsonParser = Json { ignoreUnknownKeys = true; isLenient = true }
    private val _isLoading = MutableStateFlow(false) // General loading, might be removed if not used
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // StateFlows for download and installation status
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError.asStateFlow()

    private val _installStatus = MutableStateFlow<String?>(null)
    val installStatus: StateFlow<String?> = _installStatus.asStateFlow()


    fun downloadAndInstallTheme(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _isDownloading.value = true
            _downloadProgress.value = 0f
            _downloadError.value = null
            _installStatus.value = null

            try {
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    _downloadError.value = "Download failed: ${response.message} (Code: ${response.code})"
                    return@launch
                }

                val body = response.body
                if (body == null) {
                    _downloadError.value = "Download failed: Empty response body."
                    return@launch
                }

                // Basic filename extraction, might need improvement
                val filename = url.substringAfterLast('/', "unknown_theme_file")
                // Save to cache directory first
                val tempDownloadedFile = File(context.cacheDir, filename)

                val totalBytes = body.contentLength()
                var bytesRead = 0L

                body.byteStream().use { inputStream ->
                    FileOutputStream(tempDownloadedFile).use { outputStream ->
                        val buffer = ByteArray(8 * 1024) // 8KB buffer
                        var bytes = inputStream.read(buffer)
                        while (bytes >= 0) {
                            outputStream.write(buffer, 0, bytes)
                            bytesRead += bytes
                            if (totalBytes > 0) {
                                _downloadProgress.value = (bytesRead.toFloat() / totalBytes)
                            }
                            bytes = inputStream.read(buffer)
                        }
                    }
                }
                _installStatus.value = "Downloaded, now processing..."
                processDownloadedTheme(context, tempDownloadedFile, storeType)

            } catch (e: IOException) {
                _downloadError.value = "File operation failed: ${e.message}"
            } catch (e: Exception) {
                _downloadError.value = "An unexpected error occurred: ${e.message}"
            } finally {
                _isDownloading.value = false
            }
        }
    }

    private fun processDownloadedTheme(context: Context, downloadedFile: File, storeType: StoreType) {
        val workingDir = File(context.cacheDir, "theme_processing_${System.currentTimeMillis()}")
        try {
            if (!workingDir.mkdirs()) {
                // It might be that the directory already exists if System.currentTimeMillis() is the same for some reason
                // or if mkdirs fails for another reason. Add an extra check or alternative.
                if (!workingDir.exists() || !workingDir.isDirectory) {
                    throw IOException("Failed to create working directory: ${workingDir.absolutePath}")
                }
            }

            val downloadedFilenameLowercase = downloadedFile.name.lowercase()
            val needsUnzip = when (storeType) {
                StoreType.JETBRAINS -> downloadedFilenameLowercase.endsWith(".zip") || downloadedFilenameLowercase.endsWith(".jar")
                StoreType.MICROSOFT -> downloadedFilenameLowercase.endsWith(".vsix")
            }
            var themeDefinitionFile: File? = null
            var parsedThemeData: Any? = null

            if (needsUnzip) {
                unzip(downloadedFile, workingDir)
                _installStatus.value = "Unzipped theme package."
                Log.d(tag, "Unzipped to ${workingDir.absolutePath}")

                if (storeType == StoreType.JETBRAINS) {
                    themeDefinitionFile = workingDir.walkTopDown().firstOrNull { it.name.endsWith(".theme.json") }
                    if (themeDefinitionFile == null) {
                        themeDefinitionFile = workingDir.walkTopDown().firstOrNull { it.name.endsWith(".icls") }
                    }
                } else if (storeType == StoreType.MICROSOFT) {
                    val themesDir = File(workingDir, "extension/themes")
                    if (themesDir.exists() && themesDir.isDirectory) {
                        themeDefinitionFile = themesDir.walkTopDown().firstOrNull { it.name.endsWith(".json") }
                    }
                }
            } else {
                // If not unzipped, check if the downloaded file itself is a theme definition
                if (downloadedFilenameLowercase.endsWith(".json") || (storeType == StoreType.JETBRAINS && downloadedFilenameLowercase.endsWith(".icls"))) {
                    val targetFileInWorkingDir = File(workingDir, downloadedFilenameLowercase)
                    downloadedFile.copyTo(targetFileInWorkingDir, overwrite = true)
                    themeDefinitionFile = targetFileInWorkingDir
                    _installStatus.value = "Processing direct theme file."
                } else {
                    _installStatus.value = "File is not a recognized archive or direct theme file: $downloadedFilenameLowercase"
                }
            }

            if (themeDefinitionFile != null && themeDefinitionFile.exists()) {
                Log.d(tag, "Theme definition file found: ${themeDefinitionFile.absolutePath}")
                _installStatus.value = "Found theme file: ${themeDefinitionFile.name}"
                val fileContent = themeDefinitionFile.readText()

                try {
                    when {
                        themeDefinitionFile.extension.equals("json", ignoreCase = true) -> {
                            parsedThemeData = if (storeType == StoreType.MICROSOFT) {
                                jsonParser.decodeFromString<VSCodeThemeJson>(fileContent)
                                    .also { Log.d(tag, "Successfully parsed VSCode theme JSON.") }
                            } else { // JETBRAINS .theme.json
                                jsonParser.decodeFromString<JetbrainsThemeJson>(fileContent)
                                    .also { Log.d(tag, "Successfully parsed Jetbrains theme JSON.") }
                            }
                        }
                        themeDefinitionFile.extension.equals("icls", ignoreCase = true) -> {
                            Log.d(tag, "ICLS file found: ${themeDefinitionFile.name}. Basic parsing.")
                            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
                            val parser = factory.newPullParser()
                            parser.setInput(themeDefinitionFile.inputStream(), null)
                            var themeNameFromIcls: String? = null
                            var eventType = parser.eventType
                            while (eventType != XmlPullParser.END_DOCUMENT) {
                                if (eventType == XmlPullParser.START_TAG && parser.name == "scheme") {
                                    themeNameFromIcls = parser.getAttributeValue(null, "name")
                                    break
                                }
                                eventType = parser.next()
                            }
                            parsedThemeData = IclsThemeData(name = themeNameFromIcls)
                            Log.d(tag, "ICLS theme name extracted: $themeNameFromIcls")
                            _installStatus.value = "Partially parsed ICLS file (name: $themeNameFromIcls)."
                        }
                        else -> {
                            Log.e(tag, "Unsupported theme definition file type: ${themeDefinitionFile.absolutePath}")
                            _downloadError.value = "Unsupported theme file type: ${themeDefinitionFile.extension}"
                            deleteWorkingDir(workingDir)
                            return // from processDownloadedTheme
                        }
                    }
                } catch (e: Exception) { // Catches SerializationException and XmlPullParserException etc.
                    Log.e(tag, "Failed to parse theme file ${themeDefinitionFile.name}: ${e.message}", e)
                    _downloadError.value = "Failed to parse theme file: ${e.message}"
                    deleteWorkingDir(workingDir)
                    return // from processDownloadedTheme
                }

                if (parsedThemeData != null) {
                    Log.d(tag, "Theme data parsed successfully.")
                    _installStatus.value = "Theme file parsed: ${themeDefinitionFile.name}"

                    var isDarkInferred = false
                    if (parsedThemeData is JetbrainsThemeJson) {
                        isDarkInferred = parsedThemeData.dark ?: false
                    } else if (parsedThemeData is VSCodeThemeJson) {
                        if (themeDefinitionFile.name.contains("dark", ignoreCase = true) ||
                            (parsedThemeData.name ?: "").contains("dark", ignoreCase = true) ||
                            parsedThemeData.type?.contains("dark", ignoreCase = true) == true) {
                            isDarkInferred = true
                        }
                    } else if (parsedThemeData is IclsThemeData) {
                        if ((parsedThemeData.name ?: "").contains("dark", ignoreCase = true)) {
                            isDarkInferred = true
                        }
                        // Additionally, ICLS might imply dark through background color, but that's more complex.
                    }

                    val androidIdeTheme = transformToAndroidIDETheme(parsedThemeData, themeName, isDarkInferred, themeDefinitionFile.name)
                    if (androidIdeTheme != null) {
                        try {
                            val finalJsonString = jsonParser.encodeToString(androidIdeTheme)
                            // Determine schemeId
                            var tempSchemeId = themeName.replace(Regex("[^a-zA-Z0-9_.-]"), "_").lowercase()
                            if (tempSchemeId.isBlank()) {
                                tempSchemeId = "custom_theme_" + System.currentTimeMillis()
                            }
                            val schemeId = tempSchemeId
                            val schemeFileName = "$schemeId.json"

                            // Construct scheme.prop content
                            val propBuilder = StringBuilder()
                            propBuilder.append("scheme.name=").append(themeName).append("\n") // Use original themeName for display
                            propBuilder.append("scheme.version=1").append("\n")
                            propBuilder.append("scheme.isDark=").append(isDarkInferred).append("\n")
                            propBuilder.append("scheme.file=").append(schemeFileName)
                            val schemePropContent = propBuilder.toString()

                            // Prepare Output Directory
                            val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                            val themesBaseDir = File(publicDir, "AndroidIDEThemes")
                            if (!themesBaseDir.exists()) { themesBaseDir.mkdirs() }
                            val finalThemeDir = File(themesBaseDir, schemeId)
                            if (finalThemeDir.exists()) { finalThemeDir.deleteRecursively() }
                            finalThemeDir.mkdirs()

                            // Write Files
                            try {
                                File(finalThemeDir, schemeFileName).writeText(finalJsonString)
                                File(finalThemeDir, "scheme.prop").writeText(schemePropContent)
                                Log.d(tag, "AndroidIDE theme '$schemeId' saved to ${finalThemeDir.absolutePath}")
                                _installStatus.value = "Theme '$themeName' prepared for AndroidIDE.\nSaved to: Downloads/AndroidIDEThemes/$schemeId\n\nInstructions:\n1. Open AndroidIDE.\n2. Navigate to Editor settings > Color Scheme.\n3. Use AndroidIDE's import function to install it by selecting the '$schemeId' folder or its files from the path above, OR manually copy the '$schemeId' folder to AndroidIDE's internal schemes directory if you have advanced access."
                                // Successfully packaged, now delete workingDir
                                deleteWorkingDir(workingDir)
                            } catch (ioe: IOException) {
                                Log.e(tag, "Failed to write theme files: ${ioe.message}", ioe)
                                _downloadError.value = "Failed to save prepared theme: ${ioe.message}"
                                deleteWorkingDir(workingDir) // Also clean up workingDir on write error
                            }

                        } catch (e: Exception) { // Catch error from jsonParser.encodeToString
                            Log.e(tag, "Error serializing transformed theme: ${e.message}", e)
                            _downloadError.value = "Failed to serialize final theme: ${e.message}"
                            deleteWorkingDir(workingDir)
                        }
                    } else { // transformToAndroidIDETheme returned null
                        _downloadError.value = "Failed to transform theme for AndroidIDE."
                        deleteWorkingDir(workingDir)
                    }

                } else if (_downloadError.value == null) { // parsedThemeData was null, and no earlier error set
                    _downloadError.value = "Failed to obtain parsed data, though file was found."
                    deleteWorkingDir(workingDir)
                }

            } else { // themeDefinitionFile was null or did not exist
                Log.e(tag, "Could not find theme definition file in ${workingDir.absolutePath} or from direct file.")
                _downloadError.value = "Could not find theme definition file."
                deleteWorkingDir(workingDir) // Clean up if definition file not found
            }

        } catch (e: IOException) { // Catch errors from initial file operations or unzipping
            _downloadError.value = "Error processing theme: ${e.message}"
            deleteWorkingDir(workingDir)
        } catch (e: Exception) { // Catch any other unexpected errors
            _downloadError.value = "Unexpected error during theme processing: ${e.message}"
            deleteWorkingDir(workingDir)
        } finally {
            // Clean up the original temporary downloaded file (archive or raw)
            if (downloadedFile.exists()) {
                downloadedFile.delete()
            }
            // Ensure workingDir is cleaned IF an error occurred and it wasn't cleaned by specific error handling
            // This is a fallback; ideally, specific error handlers should manage workingDir cleanup.
            // However, if an error occurs BEFORE workingDir is used for successful packaging, it should be cleaned.
            // If _downloadError is set and workingDir exists, it implies an issue before successful packaging.
            if (_downloadError.value != null && workingDir.exists()) {
                 // deleteWorkingDir(workingDir) // Re-evaluating: workingDir is now cleaned in most error paths.
                 // If an error occurs, workingDir *should* be cleaned by the catch block that sets the error.
                 // This final check might be redundant or could hide logic errors if specific handlers miss cleanup.
                 // For now, let's assume specific handlers do their job.
            }
        }
    }

    private fun transformToAndroidIDETheme(
        parsedData: Any?,
        originalThemeName: String, // The name from the store/card state
        isDarkTheme: Boolean,
        themeDefinitionFilename: String // Filename of the parsed definition file
    ): AndroidIDERootTheme? {
        val definitionsMap = mutableMapOf<String, String>()
        val editorColorsValues = mutableMapOf<String, String>() // Store direct color values for editor
        var languageStyling: AndroidIDELanguageStyling? = null

        // Helper to add to definitions and return reference
        fun defineColor(name: String, colorValue: String?): String? {
            if (colorValue.isNullOrBlank() || !colorValue.startsWith("#")) return null // Basic validation
            val defName = name.replace(".", "_").lowercase() // Sanitize for definition key
            definitionsMap[defName] = colorValue
            return "@$defName"
        }

        when (parsedData) {
            is VSCodeThemeJson -> {
                parsedData.colors?.forEach { (key, value) ->
                    when (key) {
                        "editor.background" -> editorColorsValues["bg"] = defineColor("editor_bg", value) ?: value
                        "editor.foreground" -> editorColorsValues["fg"] = defineColor("editor_fg", value) ?: value
                        "editorCursor.foreground" -> editorColorsValues["caretFg"] = defineColor("editor_caret_fg", value) ?: value
                        "editor.selectionBackground" -> editorColorsValues["selectionBg"] = defineColor("editor_selection_bg", value) ?: value
                        "editorGutter.background" -> editorColorsValues["gutterBg"] = defineColor("editor_gutter_bg", value) ?: value
                        "editorLineNumber.foreground" -> editorColorsValues["gutterFg"] = defineColor("editor_gutter_fg", value) ?: value
                        "editor.lineHighlightBackground" -> editorColorsValues["lineHighlightBg"] = defineColor("editor_line_highlight_bg", value) ?: value
                        "editorWhitespace.foreground" -> editorColorsValues["whitespaceFg"] = defineColor("editor_whitespace_fg", value) ?: value
                        // Add more direct mappings here
                    }
                }
                // Simplified token color mapping (comment example)
                val commentToken = parsedData.tokenColors?.find { it.scope?.contains("comment") == true }
                commentToken?.settings?.get("foreground")?.let { fg ->
                    val commentFgRef = defineColor("comment_fg", fg)
                    if (commentFgRef != null) {
                        languageStyling = AndroidIDELanguageStyling(
                            types = listOf("global"), // Apply to all, or detect language
                            styles = mapOf("comment" to AndroidIDEStyleEntry(fg = commentFgRef))
                        )
                    }
                }
            }
            is JetbrainsThemeJson -> {
                val jbColors = parsedData.colors ?: emptyMap()
                val jbUi = parsedData.ui ?: emptyMap()

                // Prioritize specific keys, then general UI keys, then fallback from `colors` map
                val bg = jbUi["Editor.background"] ?: jbColors["BACKGROUND"]
                val fg = jbUi["Editor.foreground"] ?: jbColors["FOREGROUND"]
                val caret = jbUi["Caret.foreground"] ?: jbColors["CARET"]
                val selection = jbUi["Editor.selectionBackground"] ?: jbColors["SELECTION_BACKGROUND"]
                val gutterBg = jbUi["Gutter.background"] ?: jbColors["GUTTER_BACKGROUND"] // Jetbrains often uses "Gutter.background"
                val gutterFg = jbUi["EditorLineNumber.foreground"] ?: jbColors["LINE_NUMBERS_COLOR"]
                val lineHighlight = jbUi["Editor.caretRowBackground"] ?: jbColors["CARET_ROW_COLOR"]

                editorColorsValues["bg"] = defineColor("editor_bg", bg) ?: bg
                editorColorsValues["fg"] = defineColor("editor_fg", fg) ?: fg
                editorColorsValues["caretFg"] = defineColor("editor_caret_fg", caret) ?: caret
                editorColorsValues["selectionBg"] = defineColor("editor_selection_bg", selection) ?: selection
                editorColorsValues["gutterBg"] = defineColor("editor_gutter_bg", gutterBg) ?: gutterBg
                editorColorsValues["gutterFg"] = defineColor("editor_gutter_fg", gutterFg) ?: gutterFg
                editorColorsValues["lineHighlightBg"] = defineColor("editor_line_highlight_bg", lineHighlight) ?: lineHighlight

                // Simplified comment mapping for JetBrains
                jbColors["COMMENT_FOREGROUND"]?.let { fgColor ->
                     val commentFgRef = defineColor("comment_fg", fgColor)
                     if (commentFgRef != null) {
                        languageStyling = AndroidIDELanguageStyling(
                            types = listOf("global"),
                            styles = mapOf("comment" to AndroidIDEStyleEntry(fg = commentFgRef))
                        )
                    }
                }
            }
            is IclsThemeData -> {
                // ICLS parsing is very basic, only name was extracted.
                // A more complete ICLS parser would populate parsedData.colors and parsedData.attributes.
                // For now, we'll rely on the name for darkness and potentially defaults.
                // Example: if parsedData.colors had "BACKGROUND"
                // editorColorsValues["bg"] = defineColor("editor_bg", parsedData.colors["BACKGROUND"]) ?: parsedData.colors["BACKGROUND"]
                Log.d(tag, "ICLS transformation is minimal, using theme name: ${parsedData.name}")
            }
            else -> {
                Log.e(tag, "Unsupported parsed data type: ${parsedData?.javaClass?.name}")
                return null
            }
        }

        if (editorColorsValues.isEmpty() && languageStyling == null && definitionsMap.isEmpty()) {
            Log.w(tag, "No relevant theme data extracted for AndroidIDE format from $themeDefinitionFilename.")
            return null
        }

        val finalEditorColors = AndroidIDEEditorColors(
            bg = editorColorsValues["bg"],
            fg = editorColorsValues["fg"],
            gutterBg = editorColorsValues["gutterBg"],
            gutterFg = editorColorsValues["gutterFg"],
            caretFg = editorColorsValues["caretFg"],
            selectionBg = editorColorsValues["selectionBg"],
            lineHighlightBg = editorColorsValues["lineHighlightBg"],
            whitespaceFg = editorColorsValues["whitespaceFg"]
        )

        return AndroidIDERootTheme(
            name = originalThemeName, // Use the name from the store/manifest
            isDark = isDarkTheme,
            definitions = definitionsMap.ifEmpty { null },
            editor = if (finalEditorColors.reflectivelyHasAnyNonNull()) finalEditorColors else null,
            languages = languageStyling?.let { listOf(it) }
        )
    }

    // Helper extension to check if any property of a data class is non-null
    private fun Any.reflectivelyHasAnyNonNull(): Boolean {
        return this::class.java.declaredFields.any { field ->
            field.isAccessible = true
            field.get(this) != null
        }
    }

    private fun deleteWorkingDir(workingDir: File) {
        if (workingDir.exists()) {
            workingDir.deleteRecursively()
            Log.d(tag, "Cleaned up working directory: ${workingDir.path}")
        }
    }
}
