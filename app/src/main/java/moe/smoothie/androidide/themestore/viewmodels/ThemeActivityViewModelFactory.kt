package moe.smoothie.androidide.themestore.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import okhttp3.OkHttpClient

/**
 * Factory for creating [ThemeActivityViewModel] instances.
 * Hilt cannot automatically provide ViewModels with constructor arguments
 * that are not known at compile time (like the themeDownloadUrl).
 */
@Suppress("UNCHECKED_CAST")
class ThemeActivityViewModelFactory(
    private val httpClient: OkHttpClient,
    private val themeDownloadUrl: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ThemeActivityViewModel::class.java)) {
            return ThemeActivityViewModel(httpClient, themeDownloadUrl) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
