package moe.smoothie.androidide.themestore.viewmodels

import android.app.Application
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import moe.smoothie.androidide.themestore.model.ThemeState
import moe.smoothie.androidide.themestore.vscode.VSCodeTheme
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import ru.gildor.coroutines.okhttp.await
import java.net.ConnectException
import java.util.concurrent.TimeoutException
import javax.inject.Inject

@AssistedFactory
interface ThemeActivityViewModelFactory {
    fun create(url: String): ThemeActivityViewModel
}

sealed interface ExportState {
    object Idle : ExportState
    object Exporting : ExportState
    data class Success(val path: String) : ExportState
    data class Error(val message: String) : ExportState
}

@HiltViewModel
class ThemeActivityViewModel @AssistedInject constructor(
    @Assisted private val url: String,
    private val httpClient: OkHttpClient,
    private val json: Json,
    @ApplicationContext private val application: Application
) : ViewModel() {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _themeState = MutableStateFlow<ThemeState?>(null)
    val themeState: StateFlow<ThemeState?> = _themeState

    // Download state flows
    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress

    private val _downloadedThemeFileUri = MutableStateFlow<String?>(null)
    val downloadedThemeFileUri: StateFlow<String?> = _downloadedThemeFileUri

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState

    // Utility Function: Generate Scheme ID
    private fun generateSchemeId(themeName: String): String {
        return themeName
            .lowercase()
            .replace(Regex("[^a-z0-9_-]"), "_") // Replace non-alphanumeric/hyphen/underscore with underscore
            .replace(Regex("_+"), "_") // Replace multiple underscores with single
            .trim('_') // Trim leading/trailing underscores
            .take(50) // Limit length
            .ifEmpty { "unnamed_theme" } // Fallback for empty names
    }

    // Utility Function: Prepare Temporary Theme Directory
    private fun prepareTemporaryThemeDirectory(schemeId: String): java.io.File? {
        return try {
            val tempDir = java.io.File(application.cacheDir, schemeId)
            if (!tempDir.exists()) {
                if (!tempDir.mkdirs()) {
                    Log.e("ThemeActivityViewModel", "Failed to create temporary directory: ${tempDir.absolutePath}")
                    return null
                }
            }
            if (!tempDir.isDirectory) { // Check if it's actually a directory
                Log.e("ThemeActivityViewModel", "Temporary path exists but is not a directory: ${tempDir.absolutePath}")
                return null
            }
            tempDir
        } catch (e: Exception) {
            Log.e("ThemeActivityViewModel", "Error preparing temporary directory for $schemeId", e)
            null
        }
    }

    fun exportThemeToDownloads(schemeId: String, themeNameInput: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _exportState.value = ExportState.Exporting
            val themeNameToUse = themeNameInput ?: _themeState.value?.name ?: "UnnamedTheme"

            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val androidIdeThemesDir = java.io.File(downloadsDir, "AndroidIDEThemes")
                if (!androidIdeThemesDir.exists()) {
                    if (!androidIdeThemesDir.mkdirs()) {
                        _exportState.value = ExportState.Error("Failed to create AndroidIDEThemes directory in Downloads.")
                        return@launch
                    }
                }

                val targetThemeDir = java.io.File(androidIdeThemesDir, schemeId)
                val sourceDir = java.io.File(application.cacheDir, schemeId)

                if (!sourceDir.exists() || !sourceDir.isDirectory) {
                    _exportState.value = ExportState.Error("Source theme directory not found in cache.")
                    return@launch
                }

                if (targetThemeDir.exists()) {
                    if (!targetThemeDir.deleteRecursively()) {
                        _exportState.value = ExportState.Error("Failed to clear existing target theme directory.")
                        return@launch
                    }
                }
                if (!targetThemeDir.mkdirs()) {
                    _exportState.value = ExportState.Error("Failed to create target theme directory.")
                    return@launch
                }

                sourceDir.listFiles()?.forEach { sourceFile ->
                    val destinationFile = java.io.File(targetThemeDir, sourceFile.name)
                    try {
                        sourceFile.copyTo(destinationFile, overwrite = true)
                    } catch (e: Exception) {
                        Log.e("ThemeActivityViewModel", "Failed to copy file ${sourceFile.name} to ${destinationFile.name}", e)
                        // Rollback or error: For now, just report error and stop
                        _exportState.value = ExportState.Error("Failed to copy file: ${sourceFile.name}. Error: ${e.message}")
                        // Clean up partially copied files by deleting the target directory
                        targetThemeDir.deleteRecursively()
                        return@launch
                    }
                }
                _exportState.value = ExportState.Success(targetThemeDir.absolutePath)

            } catch (e: Exception) {
                Log.e("ThemeActivityViewModel", "Error exporting theme to Downloads", e)
                _exportState.value = ExportState.Error("Error exporting theme: ${e.message}")
            }
        }
    }

    fun downloadThemeFile() {
        viewModelScope.launch {
            if (_isDownloading.value) {
                return@launch
            }

            val currentThemeState = _themeState.value
            if (currentThemeState?.themeDownloadUrl.isNullOrEmpty()) {
                Log.e("ThemeActivityViewModel", "Theme state or download URL is null/empty.")
                _downloadError.value = "Download URL is not available."
                return@launch
            }

            _isDownloading.value = true
            _downloadProgress.value = 0
            _downloadedThemeFileUri.value = null
            _downloadError.value = null

            var tempFile: java.io.File? = null

            try {
                val request = Request.Builder().url(currentThemeState!!.themeDownloadUrl!!).build()
                val response = httpClient.newCall(request).execute() // Use execute for sync call in coroutine

                if (!response.isSuccessful) {
                    Log.e("ThemeActivityViewModel", "Download failed: ${response.code} ${response.message}")
                    _downloadError.value = "Download failed: ${response.message}"
                    response.close()
                    return@launch
                }

                val responseBody = response.body
                if (responseBody == null) {
                    Log.e("ThemeActivityViewModel", "Download failed: Response body is null")
                    _downloadError.value = "Download failed: Empty response."
                    response.close()
                    return@launch
                }

                // Determine filename
                var fileName = currentThemeState.name.replace(Regex("[^a-zA-Z0-9._-]"), "_") +
                        (currentThemeState.fileExtension ?: "")
                val contentDisposition = response.header("Content-Disposition")
                if (contentDisposition != null) {
                    val regex = Regex("filename=\"([^\"]+)\"")
                    val matchResult = regex.find(contentDisposition)
                    if (matchResult != null && matchResult.groupValues.size > 1) {
                        fileName = matchResult.groupValues[1]
                    }
                }
                if (fileName.isEmpty()) { // Fallback if somehow name and extension are empty
                    fileName = "theme_download${currentThemeState.fileExtension ?: ".dat"}"
                }


                tempFile = java.io.File(application.cacheDir, fileName)
                val inputStream = responseBody.byteStream()
                val outputStream = java.io.FileOutputStream(tempFile)
                val totalBytes = responseBody.contentLength()
                var bytesCopied: Long = 0
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var bytes = inputStream.read(buffer)

                while (bytes >= 0) {
                    outputStream.write(buffer, 0, bytes)
                    bytesCopied += bytes
                    if (totalBytes > 0) {
                        _downloadProgress.value = ((bytesCopied * 100) / totalBytes).toInt()
                    }
                    bytes = inputStream.read(buffer)
                }

                outputStream.flush()
                outputStream.close()
                inputStream.close()
                response.close()

                _downloadedThemeFileUri.value = tempFile.toURI().toString() // Or absolutePath
                _downloadProgress.value = 100 // Ensure it reaches 100%

            } catch (e: Exception) {
                Log.e("ThemeActivityViewModel", "Error downloading theme", e)
                _downloadError.value = "Error downloading theme: ${e.message}"
                tempFile?.delete() // Clean up partial file
            } finally {
                _isDownloading.value = false
            }
        }
    }

    fun loadInfo() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val request = Request.Builder().url(url).build()
                val response = httpClient.newCall(request).await() // Assumes okhttp-coroutines
                val responseBodyString = response.body?.string()

                if (response.isSuccessful && responseBodyString != null) {
                    _themeState.value = json.decodeFromString<ThemeState>(responseBodyString)
                } else {
                    Log.e("ThemeActivityViewModel", "Error fetching theme: ${response.code} ${response.message}")
                    _themeState.value = null
                }
            } catch (e: TimeoutException) { // More specific: HttpRequestTimeoutException if using that one
                Log.e("ThemeActivityViewModel", "Network timeout loading theme", e)
                _themeState.value = null
            } catch (e: ConnectException) {
                Log.e("ThemeActivityViewModel", "Network connection error loading theme", e)
                _themeState.value = null
            } catch (e: SerializationException) {
                Log.e("ThemeActivityViewModel", "Error parsing theme JSON", e)
                _themeState.value = null
            } catch (e: Exception) {
                Log.e("ThemeActivityViewModel", "Error loading theme", e)
                _themeState.value = null
            } finally {
                _isLoading.value = false
            }
        }
    }

    suspend fun convertVSCodeThemeToAndroidIDE(downloadedFilePath: String, themeName: String): String? {
        val schemeId = generateSchemeId(themeName)
        val themeDir = prepareTemporaryThemeDirectory(schemeId)

        if (themeDir == null) {
            Log.e("ThemeActivityViewModel", "Failed to prepare temporary directory for $schemeId")
            return null
        }

        return try {
            // Read and parse VSCode Theme JSON
            val vscodeThemeFile = File(downloadedFilePath)
            if (!vscodeThemeFile.exists()) {
                Log.e("ThemeActivityViewModel", "VSCode theme file does not exist: $downloadedFilePath")
                return null
            }
            val vscodeThemeJson = vscodeThemeFile.readText()
            val vscodeTheme = json.decodeFromString<VSCodeTheme>(vscodeThemeJson)

            // Extract key colors (simplified)
            val editorBackground = vscodeTheme.colors?.get("editor.background") ?: "#222222"
            val editorForeground = vscodeTheme.colors?.get("editor.foreground") ?: "#DDDDDD"
            var commentColor = vscodeTheme.tokenColors?.find { it.scope?.contains("comment") == true }?.settings?.foreground ?: "#808080"
            var stringColor = vscodeTheme.tokenColors?.find { it.scope?.contains("string") == true }?.settings?.foreground ?: "#CE9178"
            var keywordColor = vscodeTheme.tokenColors?.find { it.scope?.any { s -> s.startsWith("keyword.") || s == "keyword" } == true }?.settings?.foreground ?: "#569CD6"
            var numberColor = vscodeTheme.tokenColors?.find { it.scope?.contains("constant.numeric") == true }?.settings?.foreground ?: "#B5CEA8"

            // Generate scheme.prop content
            val isDark = vscodeTheme.type?.equals("dark", ignoreCase = true) ?: true // Default to dark if type is missing
            val schemePropContent = """
                |scheme.name=$themeName
                |scheme.isDark=$isDark
                |scheme.file=theme.json
            """.trimMargin()

            // Generate theme.json content for AndroidIDE (simplified)
            // Using a basic structure, real conversion would be more complex
            val themeJsonContent = """
            |{
            |  "name": "$themeName",
            |  "isDark": $isDark,
            |  "definitions": {
            |    "editor_bg": "$editorBackground",
            |    "editor_fg": "$editorForeground",
            |    "comment": "$commentColor",
            |    "string": "$stringColor",
            |    "keyword": "$keywordColor",
            |    "number": "$numberColor"
            |  },
            |  "editor": {
            |    "background": "@{editor_bg}",
            |    "foreground": "@{editor_fg}",
            |    "caret": "@{editor_fg}",
            |    "caret_row_background": "${adjustColorBrightness(editorBackground, if (isDark) 1.1f else 0.9f)}",
            |    "selected_text_background": "${adjustColorBrightness(editorForeground, 0.3f, true)}",
            |    "line_numbers_foreground": "${adjustColorBrightness(editorBackground, if (isDark) 1.5f else 0.7f)}"
            |  },
            |  "languages": [
            |    {
            |      "name": "default",
            |      "tokens": {
            |        "comment": "@{comment}",
            |        "string": "@{string}",
            |        "keyword": "@{keyword}",
            |        "number": "@{number}"
            |      }
            |    }
            |  ]
            |}
            """.trimMargin()

            // Write files
            FileOutputStream(File(themeDir, "scheme.prop")).use { it.write(schemePropContent.toByteArray()) }
            FileOutputStream(File(themeDir, "theme.json")).use { it.write(themeJsonContent.toByteArray()) }

            Log.d("ThemeActivityViewModel", "VSCode theme '$themeName' converted and saved to $schemeId")
            schemeId
        } catch (e: SerializationException) {
            Log.e("ThemeActivityViewModel", "Error parsing VSCode theme JSON for $themeName", e)
            themeDir.deleteRecursively() // Clean up
            null
        } catch (e: Exception) {
            Log.e("ThemeActivityViewModel", "Error converting VSCode theme $themeName", e)
            themeDir.deleteRecursively() // Clean up
            null
        }
    }

    // Helper to adjust color brightness (very basic)
    private fun adjustColorBrightness(hexColor: String, factor: Float, toAlpha: Boolean = false): String {
        var color = hexColor.removePrefix("#")
        if (color.length == 3) { // Expand shorthand hex
            color = color.map { "$it$it" }.joinToString("")
        }
        if (color.length != 6 && color.length != 8) return hexColor // Invalid format

        val (r, g, b) = color.chunked(2).map { it.toInt(16) }

        if (toAlpha) {
            val alpha = (255 * factor).toInt().coerceIn(0, 255)
            return String.format("#%02X%02X%02X%02X", alpha, r, g, b)
        }

        val newR = (r * factor).toInt().coerceIn(0, 255)
        val newG = (g * factor).toInt().coerceIn(0, 255)
        val newB = (b * factor).toInt().coerceIn(0, 255)

        return String.format("#%02X%02X%02X", newR, newG, newB)
    }
}
