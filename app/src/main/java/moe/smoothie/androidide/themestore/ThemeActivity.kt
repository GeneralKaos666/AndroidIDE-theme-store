package moe.smoothie.androidide.themestore

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import moe.smoothie.androidide.themestore.model.ThemeState
import moe.smoothie.androidide.themestore.ui.ThemeActivityTopBar
import moe.smoothie.androidide.themestore.ui.ThemeDescription
import moe.smoothie.androidide.themestore.ui.theme.AndroidIDEThemesTheme
import moe.smoothie.androidide.themestore.util.getSerializableExtraApiDependent
import moe.smoothie.androidide.themestore.viewmodels.ExportState
import moe.smoothie.androidide.themestore.viewmodels.ThemeActivityViewModel
import okhttp3.OkHttpClient
import javax.inject.Inject
import java.io.File

enum class StoreType(
    @StringRes val storeName: Int,
    @DrawableRes val storeIcon: Int
) {
    JETBRAINS(
        storeName = R.string.store_name_jetbrains,
        storeIcon = R.drawable.jetbrains_marketplace_icon
    ),
    MICROSOFT(
        storeName = R.string.store_name_microsoft,
        storeIcon = R.drawable.microsoft_store_icon
    )
}

@AndroidEntryPoint
class ThemeActivity : ComponentActivity() {
    companion object {
        const val EXTRA_STORE_TYPE: String = "STORE_TYPE"
        const val EXTRA_ICON_URL: String = "ICON_URL"
        const val EXTRA_THEME_URL: String = "THEME_URL"
    }

    val tag = "ThemeActivity"
    val viewModel: ThemeActivityViewModel by viewModels()

    @Inject
    lateinit var httpClient: OkHttpClient // This might not be needed here anymore if ViewModel handles all http
    private var themeUrl: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val store = intent.getSerializableExtraApiDependent(
            name = EXTRA_STORE_TYPE,
            clazz = StoreType::class.java
        )
        themeUrl = intent.getStringExtra(EXTRA_THEME_URL)

        if (store == null || themeUrl == null) {
            Log.e(tag, "No store type or theme URL passed in the intent. Store: $store, URL: $themeUrl")
            finish()
            return
        }

        setContent {
            AndroidIDEThemesTheme {
                val scrollState = rememberScrollState()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        ThemeActivityTopBar(
                            storeName = stringResource(store.storeName),
                            storeIcon = painterResource(store.storeIcon),
                            scrolled = scrollState.value != 0,
                            backButtonCallback = { this.finish() }
                        )
                    }
                ) { innerPadding ->
                    ThemeView(
                        innerPadding = innerPadding,
                        scrollState = scrollState,
                        viewModel = viewModel,
                        themeUrl = themeUrl!! // Already checked for null
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeView(
    innerPadding: PaddingValues,
    scrollState: ScrollState,
    viewModel: ThemeActivityViewModel,
    themeUrl: String
) {
    val coroutineScope = rememberCoroutineScope()

    // Collect states from ViewModel
    val themeState by viewModel.themeState.collectAsState()
    val isLoadingInfo by viewModel.isLoading.collectAsState() // For initial load
    val isDownloading by viewModel.isDownloading.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadError by viewModel.downloadError.collectAsState()
    val exportState by viewModel.exportState.collectAsState()
    val downloadedFileUri by viewModel.downloadedThemeFileUri.collectAsState()


    // Trigger initial load
    LaunchedEffect(key1 = themeUrl) { // themeUrl should be stable from intent
        Log.d("ThemeView", "LaunchedEffect: Calling loadInfo for URL: $themeUrl")
        // ViewModel's constructor receives the URL via Hilt's AssistedInject,
        // so loadInfo can use the URL passed at construction.
        // If the URL was not available at construction, we'd pass it here.
        viewModel.loadInfo()
    }

    val currentTheme = themeState // This is moe.smoothie.androidide.themestore.model.ThemeState

    Box(
        Modifier
            .padding(innerPadding)
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isLoadingInfo && currentTheme == null) {
                CircularProgressIndicator(modifier = Modifier.padding(vertical = 32.dp))
            }

            currentTheme?.let { theme ->
                Text(
                    text = theme.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "by ${theme.author}",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                theme.previewImageUrl?.let { imageUrl ->
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "${theme.name} preview",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 300.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Fit,
                        error = painterResource(R.drawable.ic_launcher_background), // Replace with a better placeholder
                        placeholder = painterResource(R.drawable.ic_launcher_background) // Replace with a better placeholder
                    )
                }

                Text(
                    text = theme.description,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth()
                )

                if (theme.tags.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(theme.tags) { tag ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Text(text = tag, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val buttonEnabled = !isDownloading && exportState !is ExportState.Exporting && !isLoadingInfo

                Button(
                    onClick = {
                        coroutineScope.launch {
                            if (theme.themeDownloadUrl != null) {
                                // 1. Download
                                viewModel.downloadThemeFile()
                                // downloadThemeFile updates downloadedThemeFileUri StateFlow
                                // We need to react to this change. A simple way here is to rely on the flow
                                // or use a suspendCancellableCoroutine if we need to directly bridge this.
                                // For this button, we'll assume the next step checks the value.
                            } else {
                                Log.e("ThemeView", "Download URL is null, cannot proceed.")
                                // Show error to user
                            }
                        }
                    },
                    enabled = buttonEnabled && theme.themeDownloadUrl != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Download and Prepare Theme")
                }

                // Effect to trigger conversion and export after download
                LaunchedEffect(downloadedFileUri) {
                    val uri = downloadedFileUri
                    if (uri != null && theme.themeDownloadUrl != null) { // Ensure it's a new download for this theme
                        val actualFilePath = File(java.net.URI.create(uri)).absolutePath
                        Log.d("ThemeView", "File downloaded to URI: $uri, attempting conversion for path: $actualFilePath")
                        val schemeId = viewModel.convertVSCodeThemeToAndroidIDE(actualFilePath, theme.name)
                        if (schemeId != null) {
                            Log.d("ThemeView", "Conversion successful, schemeId: $schemeId. Exporting...")
                            viewModel.exportThemeToDownloads(schemeId, theme.name)
                        } else {
                            Log.e("ThemeView", "Conversion failed for $actualFilePath")
                            // Update UI or show error based on convertVSCodeThemeToAndroidIDE outcome if it had its own error state
                        }
                         // viewModel.clearDownloadedFileUri() // Optional: clear URI after processing
                    }
                }


                if (isDownloading) {
                    LinearProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                    Text("Downloading... $downloadProgress%", textAlign = TextAlign.Center)
                }

                downloadError?.let { error ->
                    Text(
                        text = "Download Error: $error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                when (val currentExportState = exportState) {
                    is ExportState.Exporting -> {
                        CircularProgressIndicator(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Preparing theme...", textAlign = TextAlign.Center)
                    }
                    is ExportState.Success -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Theme Prepared Successfully!", fontWeight = FontWeight.Bold)
                                Text("Theme '${theme.name}' is ready.")
                                Text("Path: ${currentExportState.path}")
                                Text("Please copy this folder to AndroidIDE's theme directory.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    is ExportState.Error -> {
                        Text(
                            text = "Preparation Error: ${currentExportState.message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    ExportState.Idle -> { /* Nothing specific to show for idle export state */ }
                }
            } ?: if (!isLoadingInfo) { // If not loading and theme is still null
                Text(
                    "Failed to load theme information. Please try again.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 32.dp)
                )
            }
        }
    }
}
