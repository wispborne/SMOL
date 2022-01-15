package smol_app.browser

import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import smol_access.Access
import smol_access.Constants
import utilities.IOLock
import timber.ktx.Timber
import utilities.transferTo
import java.net.URL
import java.util.*
import kotlin.io.path.*

class DownloadManager(
    private val access: Access
) {
    private val downloadsInner = MutableStateFlow<List<DownloadItem>>(emptyList())

    val downloads = downloadsInner.asStateFlow()
    val activeDownloads = mutableMapOf<String, Job>()
    private val scope = CoroutineScope(Job())

    /**
     * Adds a [DownloadItem] to be tracked, with the progress and state modified outside of [DownloadManager].
     */
    internal fun addDownload(downloadItem: DownloadItem) {
        val existing = downloads.value.firstOrNull { it.path == downloadItem.path }

        if (existing != null && existing.status.value !is DownloadItem.Status.Failed) {
            Timber.w { "Not adding download already added item $downloadItem." }
        } else {
            Timber.i { "Adding download $downloadItem" }
            downloadsInner.tryEmit(downloadsInner.value + downloadItem)
        }
    }

    fun isDownloadable(url: String?): Boolean =
        kotlin.runCatching {
            val conn = URL(url ?: return false).openConnection()
            val headers = conn.headerFields
            Timber.v { "Url $url has headers ${headers.entries.joinToString(separator = "\n")}." }

            val contentDisposition = ContentDisposition.parse(conn.getHeaderField("Content-Disposition"))
            return contentDisposition.disposition?.startsWith("attachment", ignoreCase = true) ?: false
        }
            .onFailure { Timber.d(it) }
            .getOrDefault(false)

    /**
     * Tries to download from the given url.
     * @param allowRedownload If false, will fail if the url has already been downloaded.
     */
    internal fun downloadFromUrl(
        url: String,
        shouldInstallAfter: Boolean = true,
        allowRedownload: Boolean = true
    ): DownloadItem {
        val id = UUID.randomUUID().toString()
        val downloadItem = DownloadItem(
            id = id
        )
        // Add download to the downloads list
        addDownload(downloadItem)

        try {
            if (!isDownloadable(url)) {
                Timber.i { "Link not downloadable (no file): $url." }
                return downloadItem
                    .apply {
                        this.status.value =
                            DownloadItem.Status.Failed(RuntimeException("Link not downloadable (no file): $url."))
                    }
            }

            val conn = URL(url).openConnection()
            Timber.v { "Url $url has headers ${conn.headerFields.entries.joinToString(separator = "\n")}" }

            Timber.i { "Downloadable file clicked: $url." }
            val contentDisposition = ContentDisposition.parse(conn.getHeaderField("Content-Disposition"))
            val filename = contentDisposition.parameter("filename")

            val file = java.nio.file.Path.of(
                Constants.TEMP_DIR.absolutePathString(),
                Constants.APP_FOLDER_NAME,
                filename
            )

            if (!allowRedownload) {
                if (downloads.value.map { it.path.value?.name }.any { it == filename }) {
                    Timber.i { "Skipping file that is already in the downloads list: $filename." }
                    return downloadItem
                        .apply {
                            this.status.value =
                                DownloadItem.Status.Failed(RuntimeException("Skipping file that is already in the downloads list: $filename."))
                        }
                }
            }

            activeDownloads[id] = scope.launch {
                conn.connect()
                val downloadSize = conn.getHeaderField("Content-Length")?.toLongOrNull()

                downloadItem.path.value = file
                downloadItem.totalBytes.value = downloadSize

                IOLock.write {
                    file.deleteIfExists()
                    file.parent.createDirectories()
                    file.createFile()

                    file.outputStream().use { outs ->
                        downloadItem.status.emit(DownloadItem.Status.Downloading)

                        kotlin.runCatching {
                            // Do the download
                            conn.getInputStream().transferTo(
                                outs,
                                onProgressUpdated = { progress ->
                                    downloadItem.progressBytes.tryEmit(progress)
                                })

                            // Download complete!
                            if (downloadSize != null) downloadItem.progressBytes.emit(downloadSize)
                            downloadItem.status.emit(DownloadItem.Status.Completed)
                            Timber.i { "Downloaded $filename to $file." }
                        }
                            .onFailure { downloadItem.status.emit(DownloadItem.Status.Failed(it)) }
                            .getOrThrow()
                    }
                }

                if (shouldInstallAfter) {
                    access.installFromUnknownSource(inputFile = file, shouldCompressModFolder = true)
                    access.reload()
                }
            }
        } catch (e: CancellationException) {
            downloads.value.firstOrNull { it.id == id }?.apply {
                this.status.value = DownloadItem.Status.Cancelled
            }
        }

        return downloadItem
    }
}