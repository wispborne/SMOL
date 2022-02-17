package updatestager

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.update4j.Configuration
import org.update4j.FileMetadata
import org.update4j.UpdateOptions
import smol_access.Constants
import smol_access.config.AppConfig
import timber.ktx.Timber
import utilities.runCommandInTerminal
import java.io.File
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.name


class Updater(
    private val appConfig: AppConfig
) {
    companion object {
        const val PROP_VERSION_NAME = "smol-version-prop"
        const val UPDATE_CONFIG_XML = "update-config.xml"
        val SMOL_UPDATE_ZIP = Path.of("smol-update.zip")
    }

    /**
     * Percent of download done between 0 and 1.
     */
    val totalDownloadFraction = MutableStateFlow<Float?>(null)
    val totalDownloadBytes = MutableStateFlow<Long?>(null)
    val totalDownloadedBytes = MutableStateFlow<Long>(0)
    val currentFileDownload = MutableStateFlow<FileDownload?>(null)

    data class FileDownload(val name: String, val progress: Float)

    suspend fun getRemoteConfig(
        channel: UpdateChannel = getUpdateChannelSetting()
    ): Configuration? {
        val remoteConfigUrl = URI.create(
            "${getUpdateConfigUrl(channel)}/$UPDATE_CONFIG_XML"
        ).toURL()

        Timber.i { "Fetching SMOL update-config.xml from ${remoteConfigUrl}." }
        val remoteConfig = withContext(Dispatchers.IO) {
            runCatching {
                remoteConfigUrl.openStream().use { stream ->
                    Configuration.read(stream.bufferedReader())
                }
            }
                .onFailure { Timber.w(it) }
                .getOrNull()
        }

        totalDownloadBytes.value = remoteConfig?.files
            ?.filter { it?.requiresUpdate() ?: false }
            ?.filterNotNull()
            ?.sumOf { it.size }

        return remoteConfig
    }

    suspend fun update(remoteConfig: Configuration) {
        if (isUpdatedDownloaded()) {
            Timber.i { "SMOL update already exists." }
            return
        } else {
            Timber.i { "Fetching SMOL update from ${remoteConfig.baseUri}." }
            withContext(Dispatchers.IO) {
                remoteConfig.update(
                    UpdateOptions
                        .archive(SMOL_UPDATE_ZIP)
                        .updateHandler(object : SmolUpdateHandler() {
                            override fun updateDownloadFileProgress(file: FileMetadata?, frac: Float) {
                                if (!isActive) {
                                    currentFileDownload.value = null
                                    throw CancellationException("SMOL update coroutine was canceled.")
                                }

                                super.updateDownloadFileProgress(file, frac)
                                currentFileDownload.value =
                                    FileDownload(name = file?.path?.name ?: "(unknown)", progress = frac)
                            }

                            override fun updateDownloadProgress(frac: Float) {
                                if (!isActive) {
                                    totalDownloadFraction.value = null
                                    throw CancellationException("SMOL update coroutine was canceled.")
                                }

                                super.updateDownloadProgress(frac)
                                totalDownloadFraction.value = frac
                            }

                            override fun doneDownloadFile(file: FileMetadata, tempFile: Path) {
                                super.doneDownloadFile(file, tempFile)
                                totalDownloadedBytes.value += file.size
                            }

                            override fun stop() {
                                super.stop()
                                totalDownloadFraction.value = null
                            }

                            override fun failed(t: Throwable) {
                                super.failed(t)
                                totalDownloadFraction.value = null
                                currentFileDownload.value = null
                                throw t
                            }
                        })
                )
            }
        }
    }

    fun isUpdatedDownloaded() = SMOL_UPDATE_ZIP.exists()

    private var installJob: Job? = null

    /**
     * This will FAIL if the application is still running when the update starts, as it cannot update files in use.
     * Call this, then immediately close SMOL.
     */
    fun installUpdate() {
        val updateInstallerFilename = "UpdateInstaller-fat.jar"
        val standaloneJrePath = Path.of("jre-min-win")

        val command = "\"${
            standaloneJrePath.resolve("bin/java.exe").absolutePathString()
        }\" -jar $updateInstallerFilename '${SMOL_UPDATE_ZIP}'"

        try {
            if (installJob == null) {
                installJob = Job()

                CoroutineScope(installJob!!).launch {
                    runCommandInTerminal(
                        command = command,
                        workingDirectory = File("."),
                        runAsync = true,
                        launchInNewWindow = true,
                        newWindowTitle = "Installing SMOL update"
                    )
                }
            }
        } finally {
            installJob?.cancel()
            installJob = null
        }
    }

    private fun getUpdateChannelSetting() =
        when (appConfig.updateChannel) {
            "unstable" -> UpdateChannel.Unstable
            else -> UpdateChannel.Stable
        }

    fun getUpdateConfigUrl(channel: UpdateChannel = getUpdateChannelSetting()) =
        when (channel) {
            UpdateChannel.Unstable -> Constants.UPDATE_URL_UNSTABLE
            UpdateChannel.Stable -> Constants.UPDATE_URL_STABLE
        }


    enum class UpdateChannel {
        Unstable,
        Stable
    }
}