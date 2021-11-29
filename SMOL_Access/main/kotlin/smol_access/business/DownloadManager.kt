package smol_access.business

import io.ktor.http.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import smol_access.Constants
import smol_access.SL
import smol_access.util.IOLock
import timber.ktx.Timber
import utilities.transferTo
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.*

class DownloadManager {
    private val downloadsInner = MutableStateFlow<List<DownloadItem>>(emptyList())

    //        listOf(DownloadItem(Path.of("C:\\Users\\whitm\\AppData\\Local\\Temp\\SMOL\\prv.Starworks.v21.rar"), 100).apply { progress.value = 50 }))
    val downloads = downloadsInner.asStateFlow()

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

    suspend fun downloadAndInstall(url: String?) {
        runCatching {
            if (!isDownloadable(url)) {
                Timber.d { "Link not downloadable (no file): $url." }
                return
            }

            val conn = URL(url ?: return).openConnection()
            Timber.v { "Url $url has headers ${conn.headerFields.entries.joinToString(separator = "\n")}" }

            Timber.d { "Downloadable file clicked: $url." }
            val contentDisposition = ContentDisposition.parse(conn.getHeaderField("Content-Disposition"))
            val filename = contentDisposition.parameter("filename")

            if (downloads.value.map { it.path.name }.any { it == filename }) {
                Timber.i { "Skipping file that is already in the downloads list: $filename." }
                return
            }

            coroutineScope {
                conn.connect()
                val file = Path.of(
                    getTempDirectory().absolutePathString(),
                    Constants.APP_FOLDER_NAME,
                    filename
                )
                val downloadSize = conn.getHeaderField("Content-Length")?.toLongOrNull()

                val download = DownloadItem(
                    path = file,
                    total = downloadSize
                )

                IOLock.write {
                    file.deleteIfExists()
                    file.parent.createDirectories()
                    file.createFile()

                    file.outputStream().use { outs ->
                        // Add download to the downloads list
                        downloadsInner.emit(downloads.value + download)
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

                            // Try to install the mod
                            SL.access.installFromUnknownSource(
                                inputFile = download.path,
                                shouldCompressModFolder = true
                            )
                        }
                            .onFailure { download.status.emit(DownloadItem.Status.Failed(it)) }
                            .getOrThrow()
                    }
                }
            }
        }
            .onFailure { Timber.w(it) }
    }

    private fun getTempDirectory() =
        System.getProperty("java.io.tmpdir")?.let { Path.of(it) } ?: Constants.APP_FOLDER_DEFAULT
}