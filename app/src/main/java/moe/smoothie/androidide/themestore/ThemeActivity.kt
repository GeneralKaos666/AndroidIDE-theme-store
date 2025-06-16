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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import dagger.hilt.android.AndroidEntryPoint
import moe.smoothie.androidide.themestore.model.StoreType
import moe.smoothie.androidide.themestore.ui.JetbrainsThemeCardState
import moe.smoothie.androidide.themestore.ui.MicrosoftStoreCardState
import moe.smoothie.androidide.themestore.ui.ThemeActivityTopBar
import moe.smoothie.androidide.themestore.ui.theme.AndroidIDEThemesTheme
import moe.smoothie.androidide.themestore.util.getParcelableExtraApiDependent
import moe.smoothie.androidide.themestore.util.getSerializableExtraApiDependent
import moe.smoothie.androidide.themestore.viewmodels.ThemeActivityViewModel
import moe.smoothie.androidide.themestore.viewmodels.ThemeActivityViewModelFactory
import okhttp3.OkHttpClient
import javax.inject.Inject

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
        const val EXTRA_ICON_URL: String = "ICON_URL" // Kept for now, might be removed
        const val EXTRA_THEME_URL: String = "THEME_URL" // Kept for now, might be removed
        const val EXTRA_THEME_STATE: String = "THEME_STATE"
    }

    private val tag = "ThemeActivity"
    private lateinit var viewModel: ThemeActivityViewModel

    @Inject
    lateinit var httpClient: OkHttpClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val storeType = intent.getSerializableExtraApiDependent(EXTRA_STORE_TYPE, StoreType::class.java)
        if (storeType == null) {
            Log.e(tag, "No store type passed in the intent. Cannot display theme details.")
            finish()
            return
        }

        var themeName: String? = null
        var themeDescription: String? = null
        var themeDownloadUrl: String? = intent.getStringExtra(EXTRA_THEME_URL) // Fallback

        when (storeType) {
            StoreType.JETBRAINS -> {
                val themeState = intent.getParcelableExtraApiDependent<JetbrainsThemeCardState>(EXTRA_THEME_STATE)
                if (themeState == null) {
                    Log.e(tag, "No JetbrainsThemeCardState passed for JETBRAINS store type.")
                    finish()
                    return
                }
                themeName = themeState.name
                themeDescription = themeState.trimmedDescription
                // JetbrainsThemeCardState doesn't have a downloadUrl yet.
                // For now, we'll use previewUrl if EXTRA_THEME_URL is not present.
                // This will be properly handled in a later step.
                if (themeDownloadUrl == null) {
                    themeDownloadUrl = themeState.previewUrl
                    Log.w(tag, "Using previewUrl as downloadUrl for Jetbrains theme. This should be updated.")
                }
            }
            StoreType.MICROSOFT -> {
                val themeState = intent.getParcelableExtraApiDependent<MicrosoftStoreCardState>(EXTRA_THEME_STATE)
                if (themeState == null) {
                    Log.e(tag, "No MicrosoftStoreCardState passed for MICROSOFT store type.")
                    finish()
                    return
                }
                themeName = themeState.name
                themeDescription = themeState.description
                themeDownloadUrl = themeState.downloadUrl // This is the correct download URL
            }
        }

        if (themeName == null || themeDescription == null || themeDownloadUrl == null) {
            Log.e(tag, "Theme details (name, description, or URL) are missing. Cannot display theme.")
            finish()
            return
        }

        // Initialize ViewModel with the determined URL
        // The ViewModelFactory will use this URL when creating the ViewModel instance
        viewModel = ViewModelProvider(
            this,
            ThemeActivityViewModelFactory(httpClient, themeDownloadUrl)
        )[ThemeActivityViewModel::class.java]


        setContent {
            AndroidIDEThemesTheme {
                val scrollState = rememberScrollState()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        ThemeActivityTopBar(
                            storeName = stringResource(storeType.storeName),
                            storeIcon = painterResource(storeType.storeIcon),
                            scrolled = scrollState.value != 0,
                            backButtonCallback = { this.finish() }
                        )
                    }
                ) { innerPadding ->
                    ThemeView(
                        innerPadding = innerPadding,
                        scrollState = scrollState,
                        viewModel = viewModel,
                        themeName = themeName,
                        themeDescription = themeDescription
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
    themeName: String,
    themeDescription: String
) {
    val context = LocalContext.current

    val isDownloading by viewModel.isDownloading.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadError by viewModel.downloadError.collectAsState()
    val installStatus by viewModel.installStatus.collectAsState()

    Box(Modifier.padding(innerPadding)) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = themeName,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = themeDescription,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Button(
                onClick = { viewModel.downloadAndInstallTheme(context) },
                enabled = !isDownloading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isDownloading) "Downloading..." else "Download and Install Theme")
            }

            if (isDownloading) {
                LinearProgressIndicator(
                    progress = downloadProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            downloadError?.let { error ->
                Text(
                    text = "Error: $error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            installStatus?.let { status ->
                Text(
                    text = status,
                    color = MaterialTheme.colorScheme.primary, // Or another appropriate color
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
