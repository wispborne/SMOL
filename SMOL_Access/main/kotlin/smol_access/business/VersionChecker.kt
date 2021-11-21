package smol_access.business

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import org.hjson.JsonValue
import org.tinylog.kotlin.Logger
import smol_access.config.VersionCheckerCache
import smol_access.model.Mod
import smol_access.model.ModId
import smol_access.model.VersionCheckerInfo
import utilities.parallelMap
import utilities.trace

class VersionChecker(private val gson: Gson, private val versionCheckerCache: VersionCheckerCache) {
    fun getOnlineVersion(modId: ModId) =
        versionCheckerCache.onlineVersions[modId]

    @Suppress("ConvertCallChainIntoSequence")
    suspend fun lookUpVersions(mods: List<Mod>) {
        HttpClient(CIO) {
            install(Logging)
        }.use { client ->
            val results =
                trace({ _, millis -> Logger.info { "Version checked ${mods.count()} mods in ${millis}ms" } }) {
                    mods
                        .distinctBy { it.id }
                        .mapNotNull { it.findHighestVersion }
                        .filter { !it.versionCheckerInfo?.masterVersionFile.isNullOrBlank() }
                        .parallelMap { modVariant ->
                            kotlin.runCatching {
                                client.get<HttpResponse>(modVariant.versionCheckerInfo!!.masterVersionFile!!)
                                    .receive<String>()
                                    .let { JsonValue.readHjson(it) } // Parse first using HJson
                                    .let { modVariant.mod to gson.fromJson<VersionCheckerInfo>(it.toString()).modVersion!! }
                            }
                                .onFailure { Logger.warn { "Version check failed for ${modVariant.modInfo.name}: ${it.message} (url: ${modVariant.versionCheckerInfo?.masterVersionFile})" } }
                                .getOrNull()
                        }
                        .filterNotNull()
                        .onEach {
                            Logger.debug {
                                "Version checked ${it.first.findHighestVersion?.modInfo?.name}: " +
                                        "existing: ${it.first.findHighestVersion?.versionCheckerInfo?.modVersion}" +
                                        ", online: ${it.second}" +
                                        " | (url: ${it.first.findHighestVersion?.versionCheckerInfo?.masterVersionFile})"
                            }
                        }
                }

            versionCheckerCache.onlineVersions = results.associate { it.first.id to it.second }
        }
    }
}