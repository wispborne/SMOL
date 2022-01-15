package smol_app.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.update4j.Configuration
import org.update4j.FileMetadata
import org.update4j.UpdateOptions
import smol_access.Constants
import smol_access.config.AppConfig
import timber.ktx.Timber
import java.net.URI
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.writer
import kotlin.streams.asSequence


private const val UPDATE_CONFIG_XML = "update-config.xml"

fun main() {
    UpdateApp.writeLocalUpdateConfig(
        onlineUrl = Constants.UPDATE_URL_UNSTABLE,
        localPath = Path.of("App\\dist\\main\\app\\SMOL")
    )
}

class UpdateApp(
    private val appConfig: AppConfig
) {
    companion object {
        fun writeLocalUpdateConfig(onlineUrl: String, localPath: Path): Configuration? {
            val config = Configuration.builder()
                .baseUri(onlineUrl)
                .basePath(Path.of("").absolutePathString())
                .files(
                    FileMetadata.streamDirectory(localPath)
                        .asSequence()
                        .onEach { r -> r.classpath(r.source.toString().endsWith(".jar")) }
                        .toList())
                .build()


            localPath.resolve(UPDATE_CONFIG_XML).run {
                this.writer().use {
                    config.write(it)
                    println("Wrote config to ${this.absolutePathString()}")
                }
            }
            return config
        }
    }

    suspend fun getRemoteConfig(
        channel: UpdateChannel = getUpdateChannelSetting()
    ): Configuration? {
        val remoteConfigUrl = URI.create(
            "${getUpdateConfigUrl(channel)}/$UPDATE_CONFIG_XML"
        ).toURL()

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
        kotlin.runCatching {
            remoteConfig.update(UpdateOptions.archive(Paths.get("update.zip")))
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