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
import model.ModVariant
import model.VersionCheckerInfo
import org.hjson.JsonValue
import org.tinylog.kotlin.Logger

class VersionChecker(private val gson: Gson, private val versionCheckerCache: VersionCheckerCache) {

    suspend fun lookUpVersions(mods: List<ModVariant>) {
        HttpClient(CIO) {
            install(Logging)
        }.use { client ->
            val results = mods
                .filter { !it.versionCheckerInfo?.masterVersionFile.isNullOrBlank() }
                .mapNotNull { modVariant ->
                    kotlin.runCatching {
                        client.get<HttpResponse>(modVariant.versionCheckerInfo!!.masterVersionFile!!)
                            .receive<String>()
                            .let { JsonValue.readHjson(it) } // Parse first using HJson
                            .let { modVariant to gson.fromJson<VersionCheckerInfo>(it.toString()).modVersion!! }
                    }
                        .onFailure { Logger.warn { "Version check failed ${modVariant.modInfo.name}: ${it.message} (url: ${modVariant.versionCheckerInfo?.masterVersionFile})" } }
                        .getOrNull()
                }
                .onEach { Logger.debug { "Version checked ${it.first.modInfo.name}: existing: ${it.first.versionCheckerInfo?.modVersion}, online: ${it.second}" } }

            versionCheckerCache.onlineVersions = results.map { it.first.smolId to it.second }.toMap()
        }
    }
}