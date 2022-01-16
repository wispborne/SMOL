package smol_access.business

import com.google.gson.annotations.SerializedName
import org.hjson.JsonObject
import smol_access.Constants.ENABLED_MODS_FILENAME
import smol_access.config.GamePath
import smol_access.model.ModInfo
import timber.ktx.Timber
import utilities.IOLock
import utilities.Jsanity
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.exists
import kotlin.io.path.reader
import kotlin.io.path.writer

class GameEnabledMods(
    private val gson: Jsanity,
    private val gamePath: GamePath
) {
    fun getEnabledMods(): EnabledMods? =
        kotlin.runCatching {
            IOLock.write {
                val enabledModsFile = getEnabledModsFile() ?: return null

                if (!enabledModsFile.exists()) {
                    enabledModsFile.writer().use { outStream ->
                        gson.toJson(EnabledMods(emptyList()), outStream)
                    }
                }

                enabledModsFile.reader().use { inStream ->
                    gson.fromJson<EnabledMods>(
                        json = JsonObject.readHjson(inStream).toString(),
                        shouldStripComments = true
                    )
                }
            }
        }
            .onFailure { Timber.w(it) }
            .getOrThrow()

    fun areModsEnabled(modInfos: List<ModInfo>): List<ModInfo> {
        return (getEnabledMods() ?: return emptyList())
            .run {
                modInfos
                    .filter { it.id in this.enabledMods }
            }
    }

    fun enable(modId: String) {
        updateEnabledModsFile { enabledModsObj ->
            val prevEnabled = enabledModsObj ?: EnabledMods()

            prevEnabled.copy(
                enabledMods = prevEnabled
                    .enabledMods
                    .toMutableList()
                    .apply { add(modId) }
                    .distinct()
                    .sortedBy { it.lowercase() }
                    .toList()
            )
        }
        Timber.i { "Enabled mod in enabled_mods.json: $modId" }
    }

    fun disable(modId: String) {
        updateEnabledModsFile { enabledModsObj ->
            val prevEnabled = enabledModsObj ?: EnabledMods()

            prevEnabled.copy(enabledMods = prevEnabled.enabledMods.toMutableList()
                .apply {
                    // If nothing to remove, bail. No reason to write file again.
                    if (!remove(modId)) {
                        Timber.i { "Mod was already disabled. $modId" }
                        return@updateEnabledModsFile null
                    }
                }
            )
        }
        Timber.i { "Disabled mod for game: $modId" }
    }

    private fun updateEnabledModsFile(mutator: (EnabledMods?) -> EnabledMods?) {
        kotlin.runCatching {
            IOLock.write {
                val enabledModsFile = getEnabledModsFile()
                createBackupFileIfDoesntExist(enabledModsFile ?: return)
                val prevEnabledMods = getEnabledMods()

                enabledModsFile.writer().use { outStream ->
                    val enabledMods = mutator(prevEnabledMods) ?: prevEnabledMods
                    gson.toJson(enabledMods, outStream)
                }
            }
        }
            .onFailure { Timber.e(it) }
            .getOrThrow()
    }

    private fun createBackupFileIfDoesntExist(enabledModsFile: Path) {
        IOLock.write {
            val backupFile = gamePath.getModsPath()?.resolve("$ENABLED_MODS_FILENAME.bak")

            // Make a backup before modifying it for the first time
            if (backupFile != null && !backupFile.exists()) {
                enabledModsFile.copyTo(backupFile)
            }
        }
    }

    private fun getEnabledModsFile() = gamePath.getModsPath()?.resolve(ENABLED_MODS_FILENAME)
}

data class EnabledMods(
    @SerializedName("enabledMods") val enabledMods: List<String> = emptyList()
)