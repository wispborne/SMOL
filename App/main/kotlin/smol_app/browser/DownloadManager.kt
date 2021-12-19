package smol_app.browser

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.ktx.Timber

class DownloadManager {
    private val downloadsInner = MutableStateFlow<List<DownloadItem>>(emptyList())

    val downloads = downloadsInner.asStateFlow()

    internal fun addDownload(downloadItem: DownloadItem) {
        val existing = downloads.value.firstOrNull { it.path == downloadItem.path }

        if (existing != null && existing.status.value !is DownloadItem.Status.Failed) {
            Timber.w { "Not adding download already added item $downloadItem." }
        } else {
            Timber.i { "Adding download $downloadItem" }
            downloadsInner.tryEmit(downloadsInner.value + downloadItem)
        }
    }
}