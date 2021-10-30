package business

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import model.ModVariant
import model.VersionCheckerInfo
import org.tinylog.kotlin.Logger

class VersionChecker(private val gson: Gson) {

    suspend fun lookUpVersions(mods: List<ModVariant>) {
        HttpClient(CIO) {
            install(Logging)
        }.use { client ->
            mods
                .filter { !it.versionCheckerInfo?.masterVersionFile.isNullOrBlank() }
                .map { modVariant ->
                    client.get<HttpResponse>(modVariant.versionCheckerInfo!!.masterVersionFile!!)
                        .receive<String>()
                        .run { modVariant to gson.fromJson<VersionCheckerInfo>(this) }
                }
                .forEach { Logger.debug { "Version checked ${it.first.modInfo.name}: existing: ${it.first.versionCheckerInfo?.modVersion}, online: ${it.second.modVersion}" } }
        }
    }
}