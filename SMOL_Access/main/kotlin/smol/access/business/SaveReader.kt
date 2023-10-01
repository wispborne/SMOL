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

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import kotlinx.coroutines.flow.MutableStateFlow
import smol.access.config.GamePathManager
import smol.access.model.Version
import smol.timber.ktx.Timber
import smol.utilities.IOLock
import smol.utilities.IOLocks
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class SaveReader(
    gamePathManager: GamePathManager
) {
    val saves = MutableStateFlow<List<SaveFile>>(emptyList())

    private val descriptorFileName = "descriptor.xml"
    private val defaultSaveFolder = gamePathManager.path.value?.resolve("saves")
    private val datePatterns = listOf("yyyy-MM-dd HH:mm:ss.SSS zzz", "yyyy-MM-dd HH:mm:ss.S zzz")

    fun readAllSaves(saveFolder: Path? = defaultSaveFolder, forceRefresh: Boolean) {
        saveFolder ?: run {
            Timber.e { "Save folder was null" }
            return
        }

        if (saves.value.isEmpty() && !forceRefresh) {
            Timber.d { "Saves already loaded, not refreshing, not forced." }
            return
        }

        IOLock.read(IOLocks.gameMainFolderLock) {
            saveFolder.listDirectoryEntries("save*")
                .filter { it.isDirectory() }
                .mapNotNull { folder ->
                    runCatching { readSave(folder) }
                        .onFailure { Timber.i { "Failed to read save folder $folder. ${it.message}" } }
                        .getOrNull()
                }
                .also {
                    saves.value = it
                }
        }
    }

    private fun readSave(singleSaveFolder: Path): SaveFile {
        IOLock.read(IOLocks.gameMainFolderLock) {
            val saveTree = XmlMapper().readTree(singleSaveFolder.resolve(descriptorFileName).toFile())

            val mods = saveTree["allModsEverEnabled"]
                .firstOrNull { it.isArray }
                ?.associate { previouslyEnabledMod ->
                    val spec = previouslyEnabledMod["spec"]
                    val version = spec["versionInfo"]

                    spec.get("z")?.asInt() to SaveFileMod(
                        id = spec["id"].asText(),
                        name = spec["name"].asText(),
                        version = Version(
                            raw = version["string"]?.asText(),
                            major = version["major"]?.asText() ?: "",
                            minor = version["minor"]?.asText() ?: "",
                            patch = version["patch"]?.asText() ?: "",
                        )
                    )
                }

            return SaveFile(
                id = singleSaveFolder.name,
                characterName = saveTree["characterName"]?.asText() ?: "",
                characterLevel = saveTree["characterLevel"]?.asInt() ?: 0,
                portraitPath = saveTree["portraitName"]?.asText() ?: "",
                saveFileVersion = saveTree["saveFileVersion"]?.asText() ?: "",
                saveDate = (saveTree["saveDate"]?.get("")?.textValue())
                    .let { dateString ->
                        runCatching {
                            LocalDateTime.parse(
                                dateString
                                    ?.replace(" UTC", "")
                                    ?.replace(" ", "T")
                            )
                                .atZone(ZoneOffset.UTC)
                        }
                            .onFailure { Timber.w(it) }
                            .getOrElse { ZonedDateTime.now() }
                    },
                mods =
                if (mods != null) {
                    saveTree["enabledMods"]
                        .firstOrNull { it.isArray }
                        ?.mapNotNull { enabledMod ->
                            mods[enabledMod["spec"]["ref"]?.asInt()]
                        } ?: emptyList()
                } else {
                    emptyList()
                }
            )
        }
    }
}

data class SaveFile(
    val id: String,
    val characterName: String,
    val characterLevel: Int,
    val portraitPath: String?,
    val saveFileVersion: String?,
    val saveDate: ZonedDateTime,
    val mods: List<SaveFileMod>
)

data class SaveFileMod(
    val id: String,
    val name: String,
    val version: Version?
)