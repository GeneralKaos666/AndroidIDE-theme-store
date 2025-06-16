package moe.smoothie.androidide.themestore.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class ThemeActivityViewModel(
    private val httpClient: OkHttpClient,
    private val url: String // This is the themeDownloadUrl
) : ViewModel() {
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

                val themesDir = File(context.getExternalFilesDir(null), "themes")
                if (!themesDir.exists()) {
                    themesDir.mkdirs()
                }

                // Basic filename extraction, might need improvement
                val filename = url.substringAfterLast('/', "unknown_theme_file")
                val themeFile = File(themesDir, filename)

                val totalBytes = body.contentLength()
                var bytesRead = 0L

                body.byteStream().use { inputStream ->
                    FileOutputStream(themeFile).use { outputStream ->
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
                _installStatus.value = "Theme downloaded to ${themeFile.absolutePath}"

            } catch (e: IOException) {
                _downloadError.value = "Download failed: ${e.message}"
            } catch (e: Exception) {
                _downloadError.value = "An unexpected error occurred: ${e.message}"
            } finally {
                _isDownloading.value = false
            }
        }
    }
}
