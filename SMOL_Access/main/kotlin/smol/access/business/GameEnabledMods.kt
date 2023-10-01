/*
 * This file is distributed under the GPLv3. An informal description follows:
 * - Anyone can copy, modify and distribute this software as long as the other points are followed.
 * - You must include the license and copyright notice with each and every distribution.
 * - You may this software for commercial purposes.
 * - If you modify it, you must indicate changes made to the code.
 * - Any modifications of this code base MUST be distributed with the same license, GPLv3.
 * - This software is provided without warranty.
 * - The software author or license can not be held liable for any damages inflicted by the software.
 * The full license is available from <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package smol.access.business

import com.google.gson.annotations.SerializedName
import org.hjson.JsonObject
import smol.access.Constants.ENABLED_MODS_FILENAME
import smol.access.config.GamePathManager
import smol.access.model.ModInfo
import smol.timber.ktx.Timber
import smol.utilities.IOLock
import smol.utilities.Jsanity
import java.nio.file.Path
import kotlin.io.path.*

class GameEnabledMods(
    private val gson: Jsanity,
    private val gamePathManager: GamePathManager
) {
    fun getEnabledMods(): EnabledMods? =
        runCatching {
            val enabledModsFile = getEnabledModsFile() ?: return null

            fun createFileIfMissing() {
                if (!enabledModsFile.exists()) {
                    IOLock.write {
                        enabledModsFile.writer().use { outStream ->
                            gson.toJson(EnabledMods(emptyList()), outStream)
                        }
                    }
                }
            }

            fun readEnabledModsFile() =
                IOLock.read {
                    enabledModsFile.reader().use { inStream ->
                        gson.fromJson<EnabledMods>(
                            json = JsonObject.readHjson(inStream).toString(),
                            filename = enabledModsFile.fileName.toString(),
                            shouldStripComments = true
                        )
                    }
                }

            createFileIfMissing()

            runCatching { readEnabledModsFile() }
                .recoverCatching {
                    Timber.w(it)
                    IOLock.write {
                        runCatching { enabledModsFile.moveTo(enabledModsFile.parent.resolve(enabledModsFile.name + ".invalid")) }
                            .onFailure { enabledModsFile.deleteIfExists() }
                    }
                    createFileIfMissing()
                    readEnabledModsFile()
                }
                .getOrNull()
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
        runCatching {
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
            val backupFile = gamePathManager.getModsPath()?.resolve("$ENABLED_MODS_FILENAME.bak")

            // Make a backup before modifying it for the first time
            if (backupFile != null && !backupFile.exists()) {
                enabledModsFile.copyTo(backupFile)
            }
        }
    }

    private fun getEnabledModsFile(): Path? = gamePathManager.getModsPath()?.resolve(ENABLED_MODS_FILENAME)
}

data class EnabledMods(
    @SerializedName("enabledMods") val enabledMods: List<String> = emptyList()
)