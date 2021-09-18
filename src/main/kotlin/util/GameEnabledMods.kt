package util

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import model.ModInfo
import org.hjson.JsonObject
import org.tinylog.kotlin.Logger

class GameEnabledMods(
    private val gson: Gson,
    private val gamePath: GamePath
) {
    companion object {
        private const val FILE = "enabled_mods.json"
    }

    fun getEnabledModIds(): EnabledMods =
        kotlin.runCatching {
            val enabledModsFile = getEnabledModsFile()

            if (!enabledModsFile.exists()) {
                enabledModsFile.writer().use { outStream ->
                    gson.toJson(EnabledMods(emptyList()), outStream)
                }
            }

            enabledModsFile.reader().use { inStream ->
                gson.fromJson<EnabledMods>(JsonObject.readHjson(inStream).toString())
            }
        }
            .onFailure { Logger.warn(it) }
            .getOrThrow()

    fun areModsEnabled(modInfos: List<ModInfo>) =
        getEnabledModIds()
            .run {
                modInfos
                    .filter { it.id in this.enabledMods }
            }

    fun enable(modId: String) {
        getEnabledModIds().run {
            this.copy(enabledMods = this.enabledMods.toMutableList()
                .apply { add(modId) }
                .distinct()
            )
        }
            .also { enabledMods ->
                kotlin.runCatching {
                    val enabledModsFile = getEnabledModsFile()
                    val backupFile = gamePath.getModsPath().resolve("$FILE.bak")

                    // Make a backup before modifying it for the first time
                    if (!backupFile.exists()) {
                        enabledModsFile.copyTo(backupFile)
                    }

                    enabledModsFile.writer().use { outStream ->
                        gson.toJson(enabledMods, outStream)
                    }
                }
                    .onFailure { Logger.error(it) }
                    .getOrThrow()
            }
    }

    private fun getEnabledModsFile() = gamePath.getModsPath().resolve(FILE)
}

data class EnabledMods(
    @SerializedName("enabledMods") val enabledMods: List<String>
)