package util

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import model.ModInfo
import org.hjson.JsonObject
import org.tinylog.kotlin.Logger
import java.io.File

class GameEnabledMods(
    private val gson: Gson,
    private val gamePath: GamePath
) {
    companion object {
        private const val FILE = "enabled_mods.json"
    }

    fun getEnabledMods(): EnabledMods =
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
        getEnabledMods()
            .run {
                modInfos
                    .filter { it.id in this.enabledMods }
            }

    fun enable(modId: String) {
        updateEnabledModsFile { enabledModsObj ->
            enabledModsObj.copy(enabledMods = enabledModsObj.enabledMods.toMutableList()
                .apply { add(modId) }
            )
        }
        Logger.info { "Enabled mod for game: $modId" }
    }

    fun disable(modId: String) {
        updateEnabledModsFile { enabledModsObj ->
            enabledModsObj.copy(enabledMods = enabledModsObj.enabledMods.toMutableList()
                .apply {
                    // If nothing to remove, bail. No reason to write file again.
                    if (!remove(modId)) {
                        Logger.debug { "Mod was already disabled. $modId" }
                        return@updateEnabledModsFile null
                    }
                }
            )
        }
        Logger.info { "Disabled mod for game: $modId" }
    }

    private fun updateEnabledModsFile(mutator: (EnabledMods) -> EnabledMods?) {
        kotlin.runCatching {
            val enabledModsFile = getEnabledModsFile()
            createBackupFileIfDoesntExist(enabledModsFile)

            enabledModsFile.writer().use { outStream ->
                val enabledMods = mutator(getEnabledMods()) ?: return
                gson.toJson(enabledMods, outStream)
            }
        }
            .onFailure { Logger.error(it) }
            .getOrThrow()
    }

    private fun createBackupFileIfDoesntExist(enabledModsFile: File) {
        val backupFile = gamePath.getModsPath().resolve("$FILE.bak")

        // Make a backup before modifying it for the first time
        if (!backupFile.exists()) {
            enabledModsFile.copyTo(backupFile)
        }
    }

    private fun getEnabledModsFile() = gamePath.getModsPath().resolve(FILE)
}

data class EnabledMods(
    @SerializedName("enabledMods") val enabledMods: List<String> = emptyList()
)