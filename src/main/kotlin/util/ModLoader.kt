package util

import model.Mod
import model.ModVersion
import org.tinylog.Logger
import java.io.File

class ModLoader(
    private val gamePath: GamePath,
    private val config: AppConfig,
    private val archives: Archives,
    private val modInfoLoader: ModInfoLoader,
    private val gameEnabledMods: GameEnabledMods
) {

    @OptIn(ExperimentalStdlibApi::class)
    fun getMods(): List<Mod> {
        val enabledModIds = gameEnabledMods.getEnabledMods().enabledMods

        // Get items in archives
        val archivedMods = archives.getArchivesManifest()?.manifestItems?.values
            ?.map { archivedItem ->
                Logger.trace { "Archive: ${archivedItem.modInfo.name}" }

                val modVersion = ModVersion(
                    modInfo = archivedItem.modInfo,
                    stagingInfo = null, // Will zip with staged items later to populate
                    isEnabledInSmol = false, // Archived-only items can't be enabled
                    archiveInfo = ModVersion.ArchiveInfo(File(archivedItem.archivePath)),
                )
                Mod(
                    id = archivedItem.modInfo.id,
                    modsFolderInfo = null,  // Will zip with mods items later to populate
                    isEnabledInGame = archivedItem.modInfo.id in enabledModIds,
                    modVersions = mapOf(modVersion.smolId to modVersion)
                )
            } ?: emptyList()

        // Get items in staging
        val stagingMods: File = config.stagingPath?.toFileOrNull()!!

        val stagedMods = modInfoLoader.readModInfosFromFolderOfMods(stagingMods, onlySmolManagedMods = true)
            .map { (modFolder, modInfo) ->
                val modVersion = ModVersion(
                    modInfo = modInfo,
                    stagingInfo = ModVersion.StagingInfo(folder = modFolder),
                    isEnabledInSmol = false,
                    archiveInfo = null,
                )
                Mod(
                    id = modInfo.id,
                    modsFolderInfo = null,  // Will zip with mods items later to populate
                    isEnabledInGame = modInfo.id in enabledModIds,
                    modVersions = mapOf(modVersion.smolId to modVersion)
                )
            }
            .toList()

        // Get items in /mods folder
        val modsFolder = gamePath.getModsPath()
        val modsFolderMods = modInfoLoader.readModInfosFromFolderOfMods(modsFolder, onlySmolManagedMods = true)
            .map { (modFolder, modInfo) ->
                val modVersion = ModVersion(
                    modInfo = modInfo,
                    stagingInfo = null,
                    isEnabledInSmol = true,
                    archiveInfo = null,
                )
                Mod(
                    id = modInfo.id,
                    modsFolderInfo = Mod.ModsFolderInfo(folder = modFolder),
                    isEnabledInGame = modInfo.id in enabledModIds,
                    modVersions = mapOf(modVersion.smolId to modVersion)
                )
            }
            .toList()

        // Merge all items together, replacing nulls with data.
        val result = (archivedMods + stagedMods + modsFolderMods)
            .groupingBy { it.id }
            .reduce { _, accumulator, element ->
                accumulator.copy(
                    isEnabledInGame = accumulator.isEnabledInGame || element.isEnabledInGame,
                    modsFolderInfo = accumulator.modsFolderInfo ?: element.modsFolderInfo,
                    modVersions = kotlin.run {
                        val ret = accumulator.modVersions.toMutableMap()
                        element.modVersions.forEach { (elementKey, elementValue) ->
                            val accValue = ret[elementKey]

                            // Either merge in the new element or add it to the list.
                            if (accValue != null) {
                                ret[elementKey] = accValue.copy(
                                    isEnabledInSmol = accValue.isEnabledInSmol || elementValue.isEnabledInSmol,
                                    stagingInfo = accValue.stagingInfo ?: elementValue.stagingInfo,
                                    archiveInfo = accValue.archiveInfo ?: elementValue.archiveInfo
                                )
                            } else {
                                ret[elementKey] = elementValue
                            }
                        }
                        ret
                    }
                )
            }
            .values
            .filter { mod -> mod.modVersions.any { it.value.exists } }
            .onEach { mod -> mod.modVersions.values.forEach { it.mod = mod } }
            .toList()
            .onEach { Logger.debug { "Loaded mod: $it" } }

        return result
    }
}