package smol_app.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.update4j.Configuration
import org.update4j.FileMetadata
import org.update4j.UpdateOptions
import smol_access.Constants
import smol_access.config.AppConfig
import timber.ktx.Timber
import java.net.URI
import java.nio.file.Paths


class Updater(
    private val appConfig: AppConfig
) {
    companion object {
        const val PROP_VERSION = "smol-version"
        const val UPDATE_CONFIG_XML = "update-config.xml"
        const val SMOL_UPDATE_ZIP = "smol-update.zip"
    }

    /**
     * Percent of download done between 0 and 1.
     */
    val updateDownloadFraction = MutableStateFlow<Float?>(null)

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

    fun update(remoteConfig: Configuration) {
        Timber.i { "Fetching SMOL update from ${remoteConfig.baseUri}." }
        kotlin.runCatching {
            remoteConfig.update(
                UpdateOptions
                    .archive(Paths.get(SMOL_UPDATE_ZIP))
                    .updateHandler(object : SmolUpdateHandler() {
                        override fun updateDownloadFileProgress(file: FileMetadata?, frac: Float) {
                            super.updateDownloadFileProgress(file, frac)
                            updateDownloadFraction.value = frac
                        }

                        override fun stop() {
                            super.stop()
                            updateDownloadFraction.value = null
                        }
                    })
            )
        }
            .onFailure { Timber.w(it) }
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