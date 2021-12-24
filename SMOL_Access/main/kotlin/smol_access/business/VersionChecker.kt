package smol_access.business

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.features.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.hjson.JsonValue
import smol_access.Constants
import smol_access.config.VersionCheckerCache
import smol_access.model.Mod
import smol_access.model.ModId
import smol_access.model.VersionCheckerInfo
import timber.ktx.Timber
import timber.ktx.i
import utilities.Jsanity
import utilities.parallelMap
import utilities.trace
import java.time.Instant

class VersionChecker(
    private val gson: Jsanity,
    private val versionCheckerCache: VersionCheckerCache,
    private val userManager: UserManager,
    private val modLoader: ModLoader
) {
    companion object {
        const val DEFAULT_CHECK_INTERVAL_MILLIS: Long = 1000 * 60 * 5 // 5 mins
    }

    fun getOnlineVersion(modId: ModId) =
        versionCheckerCache.onlineVersions[modId]

    @Suppress("ConvertCallChainIntoSequence")
    suspend fun lookUpVersions(mods: List<Mod>, forceLookup: Boolean) {
        val msSinceLastCheck = Instant.now().toEpochMilli() - versionCheckerCache.lastCheckTimestamp
        val checkIntervalMillis =
            userManager.activeProfile.value.versionCheckerIntervalMillis ?: DEFAULT_CHECK_INTERVAL_MILLIS

        if (!forceLookup && msSinceLastCheck < checkIntervalMillis) {
            Timber.i { "Skipping version check, it has only been ${msSinceLastCheck / 1000}s of ${checkIntervalMillis / 1000}s." }
            return
        }

        withContext(Dispatchers.IO) {
            HttpClient(CIO) {
                install(Logging)
                this.followRedirects = true
            }.use { client ->
                val results =
                    trace({ _, millis ->
                        Timber.tag(Constants.TAG_TRACE).i { "Version checked ${mods.count()} mods in ${millis}ms" }
                    }) {
                        mods
                            .distinctBy { it.id }
                            .mapNotNull { it.findHighestVersion }
                            .filter { !it.versionCheckerInfo?.masterVersionFile.isNullOrBlank() }
                            .parallelMap { modVariant ->
                                kotlin.runCatching {
                                    client.get<HttpResponse>(modVariant.versionCheckerInfo!!.masterVersionFile!!)
                                        .receive<String>()
                                        .let { JsonValue.readHjson(it) } // Parse first using HJson
                                        .let { modVariant.mod(modLoader) to gson.fromJson<VersionCheckerInfo>(it.toString()).modVersion!! }
                                }
                                    .onFailure {
                                        fun message(error: String?) =
                                            "Version check failed for ${modVariant.modInfo.name}: $error (url: ${modVariant.versionCheckerInfo?.masterVersionFile})"
                                        if (it is ClientRequestException) {
                                            Timber.w {
                                                // API errors tend to include the entire webpage html in the error message,
                                                // so only show the first line.
                                                message(it.message.lines().firstOrNull())
                                            }
                                        } else {
                                            Timber.w { message(it.message) }
                                        }
                                    }
                                    .getOrNull()
                            }
                            .filterNotNull()
                            .onEach {
                                Timber.d {
                                    "Version checked ${it.first.findHighestVersion?.modInfo?.name}: " +
                                            "existing: ${it.first.findHighestVersion?.versionCheckerInfo?.modVersion}" +
                                            ", online: ${it.second}" +
                                            " | (url: ${it.first.findHighestVersion?.versionCheckerInfo?.masterVersionFile})"
                                }
                            }
                    }

                versionCheckerCache.onlineVersions = results.associate { it.first.id to it.second }
                versionCheckerCache.lastCheckTimestamp = Instant.now().toEpochMilli()
            }
        }
    }
}