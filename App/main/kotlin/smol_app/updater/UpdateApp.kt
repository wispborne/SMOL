package smol_app.updater

import org.update4j.Configuration
import org.update4j.UpdateOptions
import smol_access.Constants
import timber.ktx.Timber
import java.net.URI
import java.nio.file.Paths

class UpdateApp {

    fun getRemoteConfig(channel: UpdateChannel): Configuration? {
        val remoteConfigUrl = URI.create(
            "${
                when (channel) {
                    UpdateChannel.Unstable -> Constants.unstableUpdateUrl
                    UpdateChannel.Stable -> Constants.stableUpdateUrl
                }
            }/update-config.xml"
        ).toURL()

        val remoteConfig = runCatching {
            remoteConfigUrl.openStream().use { stream ->
                Configuration.read(stream.bufferedReader())
            }
        }
            .onFailure { Timber.e(it) }
            .getOrNull()

        return remoteConfig
    }

    fun update(remoteConfig: Configuration) {
        kotlin.runCatching {
            remoteConfig.update(UpdateOptions.archive(Paths.get("update.zip")))
        }
            .onFailure { Timber.w(it) }
    }

    enum class UpdateChannel {
        Unstable,
        Stable
    }
}