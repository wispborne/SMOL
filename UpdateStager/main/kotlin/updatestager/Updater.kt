package updatestager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.update4j.Archive
import org.update4j.Configuration
import org.update4j.FileMetadata
import org.update4j.UpdateOptions
import smol_access.Constants
import smol_access.config.AppConfig
import timber.ktx.Timber
import java.net.URI
import java.nio.file.Path
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

        return remoteConfig
    }

    suspend fun update(remoteConfig: Configuration) {
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

    @Deprecated("use client")
    suspend fun installUpdate() {
        withContext(Dispatchers.IO) {
            Archive.read(SMOL_UPDATE_ZIP).install()
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