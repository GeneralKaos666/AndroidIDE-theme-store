package moe.smoothie.androidide.themestore.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import moe.smoothie.androidide.themestore.data.MicrosoftStoreRequestPayload
import moe.smoothie.androidide.themestore.data.MicrosoftStoreResponse
import moe.smoothie.androidide.themestore.ui.LoadingStatus
import moe.smoothie.androidide.themestore.ui.MicrosoftStoreCardState
import moe.smoothie.androidide.themestore.util.hasNetwork
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync
import javax.inject.Inject

@HiltViewModel
class MicrosoftStoreViewModel @Inject constructor(
    private val httpClient: OkHttpClient
) : StoreFrontViewModel<MicrosoftStoreCardState>() {
    override val itemsPerPage: Int = 10

    private val tag = "MicrosoftStoreViewModel"

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun loadItems(context: Context) {
        if (!hasNetwork(context)) {
            mutableLoadingStatus.update { LoadingStatus.NO_NETWORK }
            return
        }

        mutableLoadingStatus.update { LoadingStatus.LOADING }

        viewModelScope.launch(Dispatchers.IO) {
            if (mutableAllItemsLoaded.value) {
                return@launch
            }

            if (mutableIsLoading.value) {
                return@launch
            }

            mutableIsLoading.update { true }

            val payload = Json.encodeToString(MicrosoftStoreRequestPayload.construct(
                pageSize = itemsPerPage,
                pageNumber = 1 + items.value.size / itemsPerPage,
                searchQuery = searchQuery.value
            ))

            val request = Request.Builder()
                .url("https://marketplace.visualstudio.com/_apis/public/gallery/extensionquery")
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            try {
                httpClient.newCall(request).executeAsync().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(tag, "Request failed ${response.code}\n${response.body}")
                        mutableLoadingStatus.update { LoadingStatus.ERROR_RECEIVING }
                        return@use
                    }

                    var responseBody = ""
                    var data: MicrosoftStoreResponse? = null
                    try {
                        responseBody = response.body.string()
                        data = Json.decodeFromString(responseBody)
                    } catch (exception: Exception) {
                        Log.d(tag, "Failed to serialize the response")
                        exception.printStackTrace()
                        mutableLoadingStatus.update { LoadingStatus.ERROR_PARSING }
                        return@use
                    }

                    mutableItems.update { list ->
                        list + data!!.results.first().extensions.map { extension ->
                            val versionFiles = extension.versions.first().files
                            val iconUrl = versionFiles.find {
                                it.assetType == "Microsoft.VisualStudio.Services.Icons.Default"
                            }?.source ?: ""
                            val downloadUrl = versionFiles.find {
                                it.assetType == "Microsoft.VisualStudio.Services.VSIXPackage" // Key for VSIX
                            }?.source ?: ""

                            MicrosoftStoreCardState(
                                iconUrl = iconUrl,
                                name = extension.displayName,
                                developerName = extension.publisher.displayName,
                                developerWebsite = extension.publisher.domain,
                                developerWebsiteVerified = extension.publisher.isDomainVerified,
                                downloads = extension.statistics.find {
                                    it.statisticName == "downloadCount"
                                }?.value.toString().toLongOrNull() ?: 0L,
                                description = extension.shortDescription,
                                rating = extension.statistics.find {
                                    it.statisticName == "averagerating"
                                }?.value.toString().toFloatOrNull() ?: 0f,
                                downloadUrl = downloadUrl // Populate the new field
                            )
                        }
                    }
                }
            } catch (exception: Exception) {
                Log.e(tag, "Error parsing or receiving the response")
                exception.printStackTrace()
                mutableLoadingStatus.update { LoadingStatus.ERROR_RECEIVING }
            }

            mutableIsLoading.update { false }
        }
    }
}
