package smol_app.browser

import io.ktor.http.*
import kotlinx.coroutines.coroutineScope
import smol_access.Constants
import smol_access.util.IOLock
import timber.ktx.Timber
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.nio.file.Path
import kotlin.io.path.*

class DownloadManager {
    companion object {
        suspend fun handleDownload(newLoc: String?, onProgressUpdated: (progress: Long, total: Long?) -> Unit) {
            val extensions = listOf(".zip", ".7z")

            runCatching {
                val conn = URL(newLoc).openConnection()
                val headers = conn.headerFields
                Timber.v { "Url $newLoc has headers ${headers.entries.joinToString(separator = "\n")}" }

                val contentDispStr = conn.getHeaderField("Content-Disposition")

                if (contentDispStr != null &&
                    contentDispStr.startsWith("attachment", ignoreCase = true)
                ) {
                    Timber.d { "Downloadable file clicked: $newLoc" }

                    coroutineScope {
                        conn.connect()
                        val contentDisposition = ContentDisposition.parse(contentDispStr)

                        val filename = contentDisposition.parameter("filename")
                        val file = Path.of(
                            getTempDirectory().absolutePathString(),
                            Constants.APP_FOLDER_NAME,
                            filename
                        )

                        IOLock.write {
                            file.deleteIfExists()
                            file.parent.createDirectories()
                            file.createFile()

                            file.outputStream().use { outs ->
                                conn.getInputStream().transferTo(
                                    outs,
                                    onProgressUpdated = {
                                        onProgressUpdated(
                                            it,
                                            conn.getHeaderField("Content-Length")?.toLongOrNull()
                                        )
                                    })
                                Timber.i { "Downloaded $filename to $file." }
                            }
                        }
                    }


                }
            }
                .onFailure { Timber.w(it) }
        }

        /**
         * From [java.io.InputStream], but with a delegate for progress.
         */
        @Throws(IOException::class)
        fun InputStream.transferTo(out: OutputStream, onProgressUpdated: (progress: Long) -> Unit): Long {
            val defaultBufferSize = 8192

            var transferred: Long = 0
            val buffer = ByteArray(defaultBufferSize)
            var read: Int

            while (this.read(buffer, 0, defaultBufferSize).also { read = it } >= 0) {
                out.write(buffer, 0, read)
                transferred += read.toLong()
                onProgressUpdated(transferred)
            }

            return transferred
        }

        private fun getTempDirectory() =
            System.getProperty("java.io.tmpdir")?.let { Path.of(it) } ?: Constants.APP_FOLDER_DEFAULT
    }
}