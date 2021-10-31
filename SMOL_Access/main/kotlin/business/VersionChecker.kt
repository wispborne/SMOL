package business

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import config.VersionCheckerCache
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import model.Mod
import model.ModId
import model.VersionCheckerInfo
import org.hjson.JsonValue
import org.tinylog.kotlin.Logger

class VersionChecker(private val gson: Gson, private val versionCheckerCache: VersionCheckerCache) {
    fun getOnlineVersion(modId: ModId) =
        versionCheckerCache.onlineVersions[modId]

    suspend fun lookUpVersions(mods: List<Mod>) {
        HttpClient(CIO) {
            install(Logging)
        }.use { client ->
            val results = mods
                .distinctBy { it.id }
                .mapNotNull { it.findHighestVersion }
                .filter { !it.versionCheckerInfo?.masterVersionFile.isNullOrBlank() }
                .mapNotNull { modVariant ->
                    kotlin.runCatching {
                        client.get<HttpResponse>(modVariant.versionCheckerInfo!!.masterVersionFile!!)
                            .receive<String>()
                            .let { JsonValue.readHjson(it) } // Parse first using HJson
                            .let { modVariant.mod to gson.fromJson<VersionCheckerInfo>(it.toString()).modVersion!! }
                    }
                        .onFailure { Logger.warn { "Version check failed for ${modVariant.modInfo.name}: ${it.message} (url: ${modVariant.versionCheckerInfo?.masterVersionFile})" } }
                        .getOrNull()
                }
                .onEach { Logger.debug { "Version checked ${it.first.findHighestVersion?.modInfo?.name}: " +
                        "existing: ${it.first.findHighestVersion?.versionCheckerInfo?.modVersion}" +
                        ", online: ${it.second}" +
                        " | (url: ${it.first.findHighestVersion?.versionCheckerInfo?.masterVersionFile})" } }

            versionCheckerCache.onlineVersions = results.associate { it.first.id to it.second }
        }
    }
}