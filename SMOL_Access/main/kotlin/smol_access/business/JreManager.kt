package smol_access.business

import io.ktor.client.call.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import smol_access.HttpClientBuilder
import smol_access.config.AppConfig
import smol_access.config.GamePath
import timber.ktx.Timber
import utilities.*
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*
import kotlin.io.use

class JreManager(
    private val gamePath: GamePath,
    private val appConfig: AppConfig,
    private val httpClientBuilder: HttpClientBuilder,
    private val archives: Archives
) {
    companion object {
        const val gameJreFolderName = "jre"
        private val versionRegex = Regex("""(\d+\.\d+\.\d+[\d_\-.+\w]*)""")
    }

    fun findJREs(): List<JreEntry> {
        IOLock.read(IOLocks.gameMainFolderLock) {
            return gamePath.path.value?.listDirectoryEntries()
                ?.mapNotNull { path ->
                    val javaExe = path.resolve("bin/java.exe")
                    if (!javaExe.exists()) return@mapNotNull null

                    val versionString = kotlin.runCatching {
                        ProcessBuilder()
                            .command(javaExe.absolutePathString(), "-version")
                            .directory(path.toFile().resolve("bin"))
                            .redirectErrorStream(true)
                            .start()
                            .inputStream
                            .bufferedReader()
                            .readLines()
                            .let { lines ->
                                lines.firstNotNullOfOrNull { versionRegex.find(it) }?.value
                                    ?: lines.firstOrNull()
                            }
                    }
                        .onFailure { timber.ktx.Timber.e(it) { "Error getting java version from '$path'." } }
                        .getOrNull() ?: return@mapNotNull null

                    return@mapNotNull JreEntry(versionString = versionString, path = path)
                }
                ?.toList() ?: emptyList()
        }
    }

    fun changeJre(newJre: JreEntry) {
        IOLock.write(IOLocks.gameMainFolderLock) {
            runBlocking {
                kotlin.runCatching {
                    val gamePathNN = gamePath.path.value!!
                    val currentJreSource = findJREs().firstOrNull { it.isUsedByGame }
                    var currentJreDest: Path? = null
                    val gameJrePath = kotlin.runCatching { gamePathNN.resolve(gameJreFolderName) }
                        .onFailure { Timber.w(it) }
                        .getOrNull() ?: return@runBlocking

                    // Move current JRE to a new folder.
                    kotlin.runCatching {
                        if (currentJreSource != null && currentJreSource.path.exists()) {
                            currentJreDest =
                                Path.of(gameJrePath.absolutePathString() + "-${currentJreSource.versionString}")

                            if (currentJreDest!!.exists()) {
                                currentJreDest =
                                    Path.of(
                                        currentJreDest!!.absolutePathString() + "-" + UUID.randomUUID().toString()
                                            .take(6)
                                    )
                            }

                            Timber.i { "Moving JRE ${currentJreSource.versionString} from '${currentJreSource.path}' to '$currentJreDest'." }
                            currentJreSource.path.awaitWrite().moveDirectory(currentJreDest!!)
                        }
                    }
                        .onFailure {
                            Timber.w(it) { "Unable to move currently used JRE. Make sure the game is not running." }
                            return@onFailure
                        }

                    // Rename target JRE to "jre".
                    kotlin.runCatching {
//                        gameJrePath.awaitWrite(5000)
                        newJre.path.awaitWrite()
                        Timber.i { "Moving JRE ${newJre.versionString} from '${newJre.path}' to '$gameJrePath'." }
                        newJre.path.awaitWrite().moveDirectory(gameJrePath)
                    }
                        .onFailure {
                            Timber.w(it) { "Unable to move new JRE $newJre to '$gameJrePath'." }
                            // If we failed to move the new JRE into place but we did rename the one used by the game,
                            // move the old one back into place so we don't have no "jre" folder at all.
                            if (!gameJrePath.exists() && currentJreDest != null && currentJreDest!!.exists()) {
                                Timber.w(it) { "Rolling back JRE change. Moving '$currentJreDest' to '$gameJrePath'." }
                                currentJreDest!!.moveDirectory(gameJrePath)
                            }
                        }
                }
            }
        }
    }

    val jre8DownloadProgress = MutableStateFlow<Jre8Progress?>(null)

    sealed class Jre8Progress {
        data class Downloading(val progress: Float?) : Jre8Progress()
        object Extracting : Jre8Progress()
        object Done : Jre8Progress()
    }

    /**
     * Observe progress using [jre8DownloadProgress].
     */
    suspend fun downloadJre8() {
        kotlin.runCatching {
            val gamePathNN = gamePath.path.value!!
            val gameJrePath = kotlin.runCatching { gamePathNN.resolve(gameJreFolderName) }
                .onFailure { Timber.w(it) }
                .getOrNull() ?: return@runCatching

            httpClientBuilder.invoke().use { client ->
                jre8DownloadProgress.value = Jre8Progress.Downloading(0f)

                withContext(Dispatchers.IO) {
                    var destForDownload = Path.of(gameJrePath.absolutePathString() + "-1.8.0")

                    client.get<HttpStatement>(appConfig.jre8Url) {
                        timeout {
                            connectTimeoutMillis = 45000
                            requestTimeoutMillis = 45000
                        }
                        onDownload { bytesSentTotal, contentLength ->
                            Timber.d { "Received $bytesSentTotal bytes from $contentLength" }
                            jre8DownloadProgress.value =
                                Jre8Progress.Downloading(bytesSentTotal.toFloat() / contentLength.toFloat())
                        }
                    }
                        .execute { httpResponse ->

                            if (destForDownload.exists()) {
                                destForDownload = Path.of(
                                    destForDownload.absolutePathString() + "-" + UUID.randomUUID().toString().take(6)
                                )
                            }

                            destForDownload = Path.of(destForDownload.absolutePathString() + ".7z")
                            IOLock.write(IOLocks.gameMainFolderLock) {
                                destForDownload.deleteIfExists()
                                destForDownload.createFile()
                            }
                            val channel: ByteReadChannel = httpResponse.receive()

                            while (!channel.isClosedForRead) {
                                val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())

                                IOLock.write(IOLocks.gameMainFolderLock) {
                                    while (!packet.isEmpty) {
                                        val bytes = packet.readBytes()
                                        destForDownload.appendBytes(bytes)
                                        println("Received ${destForDownload.fileSize()} bytes from ${httpResponse.contentLength()}")
                                    }
                                }
                            }

                            Timber.i { "JRE 8 saved to $destForDownload." }
                        }


                    Timber.i { "Extracting $destForDownload to $gameJrePath." }
                    jre8DownloadProgress.value = Jre8Progress.Extracting
                    val extractionTarget = gamePathNN.resolve("jre8-tmp")
                    archives.extractArchive(
                        archiveFile = destForDownload,
                        destinationPath = extractionTarget
                    )
                    extractionTarget.resolve("jre")
                        .moveDirectory(gamePathNN.resolve(destForDownload.nameWithoutExtension))
                    destForDownload.deleteIfExists()
                    extractionTarget.deleteRecursively()
                    jre8DownloadProgress.value = Jre8Progress.Done
                }
            }
        }
            .onFailure { Timber.e(it) { "Failed to download JRE 8 from ${appConfig.jre8Url}." } }
            .also {
                jre8DownloadProgress.value = null
            }
    }
}

data class JreEntry(
    val versionString: String,
    val path: Path
) {
    val isUsedByGame = path.name == JreManager.gameJreFolderName
    val version = kotlin.runCatching {
        if (versionString.startsWith("1.")) versionString.removePrefix("1.").take(1).toInt()
        else versionString.takeWhile { it != '.' }.toInt()
    }
        .onFailure { Timber.d(it) }
        .getOrElse { 0 }
}