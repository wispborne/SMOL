package util

import model.Mod
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

                Mod(
                    modInfo = archivedItem.modInfo,
                    modsFolderInfo = null,  // Will zip with mods items later to populate
                    stagingInfo = null, // Will zip with staged items later to populate
                    isEnabledInSmol = false, // Archived-only items can't be enabled
                    isEnabledInGame = archivedItem.modInfo.id in enabledModIds,
                    archiveInfo = Mod.ArchiveInfo(File(archivedItem.archivePath))
                )
            } ?: emptyList()

        // Get items in staging
        val stagingMods: File = config.stagingPath?.toFileOrNull()!!

        val stagedMods = modInfoLoader.readModInfosFromFolderOfMods(stagingMods, onlySmolManagedMods = true)
            .map { (modFolder, modInfo) ->
                Mod(
                    modInfo = modInfo,
                    modsFolderInfo = null,  // Will zip with mods items later to populate
                    stagingInfo = Mod.StagingInfo(folder = modFolder),
                    isEnabledInSmol = false,
                    isEnabledInGame = modInfo.id in enabledModIds,
                    archiveInfo = null
                )
            }
            .toList()

        // Get items in /mods folder
        val modsFolder = gamePath.getModsPath()
        val modsFolderMods = modInfoLoader.readModInfosFromFolderOfMods(modsFolder, onlySmolManagedMods = true)
            .map { (modFolder, modInfo) ->
                Mod(
                    modInfo = modInfo,
                    modsFolderInfo = Mod.ModsFolderInfo(folder = modFolder),
                    stagingInfo = null,
                    isEnabledInSmol = true,
                    isEnabledInGame = modInfo.id in enabledModIds,
                    archiveInfo = null
                )
            }
            .toList()

        // Merge all items together, replacing nulls with data.
        val result = (archivedMods + stagedMods + modsFolderMods)
            .groupingBy { it.smolId }
            .reduce { _, accumulator, element ->
                accumulator.copy(
                    isEnabledInSmol = accumulator.isEnabledInSmol || element.isEnabledInSmol,
                    isEnabledInGame = accumulator.isEnabledInGame || element.isEnabledInGame,
                    modsFolderInfo = accumulator.modsFolderInfo ?: element.modsFolderInfo,
                    stagingInfo = accumulator.stagingInfo ?: element.stagingInfo,
                    archiveInfo = accumulator.archiveInfo ?: element.archiveInfo
                )
            }
            .values
            .filter { it.exists }
            .toList()
            .onEach { Logger.debug { "Loaded mod: $it" } }

        return result
    }
}