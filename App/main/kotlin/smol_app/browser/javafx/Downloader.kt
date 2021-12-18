package smol_app.browser.javafx

import io.ktor.http.*
import kotlinx.coroutines.coroutineScope
import smol_access.Constants
import smol_access.util.IOLock
import smol_app.browser.DownloadItem
import smol_app.browser.DownloadManager
import timber.ktx.Timber
import utilities.transferTo
import java.net.URL
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*

class Downloader(
    private val downloadManager: DownloadManager
) {

    fun isDownloadable(url: String?): Boolean {
        return kotlin.runCatching {
            val conn = URL(url ?: return false).openConnection()
            val headers = conn.headerFields
            Timber.v { "Url $url has headers ${headers.entries.joinToString(separator = "\n")}." }

            val contentDisposition = ContentDisposition.parse(conn.getHeaderField("Content-Disposition"))
            return contentDisposition.disposition?.startsWith("attachment", ignoreCase = true) ?: false
        }
            .onFailure { Timber.d(it) }
            .getOrDefault(false)
    }

    /**
     * Safe, doesn't throw exception.
     */
    suspend fun download(url: String?): Path? {
        runCatching {
            if (!isDownloadable(url)) {
                Timber.d { "Link not downloadable (no file): $url." }
                return null
            }

            val conn = URL(url ?: return null).openConnection()
            Timber.v { "Url $url has headers ${conn.headerFields.entries.joinToString(separator = "\n")}" }

            Timber.d { "Downloadable file clicked: $url." }
            val contentDisposition = ContentDisposition.parse(conn.getHeaderField("Content-Disposition"))
            val filename = contentDisposition.parameter("filename")

            val file = Path.of(
                Constants.TEMP_DIR.absolutePathString(),
                Constants.APP_FOLDER_NAME,
                filename
            )

            if (downloadManager.downloads.value.map { it.path.name }.any { it == filename }) {
                Timber.i { "Skipping file that is already in the downloads list: $filename." }
                return null
            }

            coroutineScope {
                conn.connect()
                val downloadSize = conn.getHeaderField("Content-Length")?.toLongOrNull()

                val download = DownloadItem(
                    id = UUID.randomUUID().toString(),
                    path = file,
                    totalBytes = downloadSize
                )

                IOLock.write {
                    file.deleteIfExists()
                    file.parent.createDirectories()
                    file.createFile()

                    file.outputStream().use { outs ->
                        // Add download to the downloads list
                        downloadManager.addDownload(download)
                        download.status.emit(DownloadItem.Status.Downloading)

                        kotlin.runCatching {
                            // Do the download
                            conn.getInputStream().transferTo(
                                outs,
                                onProgressUpdated = { progress ->
                                    download.progress.tryEmit(progress)
                                })

                            // Download complete!
                            if (downloadSize != null) download.progress.emit(downloadSize)
                            download.status.emit(DownloadItem.Status.Completed)
                            Timber.i { "Downloaded $filename to $file." }
                        }
                            .onFailure { download.status.emit(DownloadItem.Status.Failed(it)) }
                            .getOrThrow()
                    }
                }
            }

            return file
        }
            .onFailure { Timber.w(it) }

        return null
    }
}