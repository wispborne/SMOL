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
        val enabledModIds = gameEnabledMods.getEnabledModIds().enabledMods

        // Get items in archives
        val archivedMods = archives.getArchivesManifest()?.manifestItems?.values
            ?.map { archivedItem ->
                Logger.trace { "Archive: ${archivedItem.modInfo.name}" }

                Mod(
                    modInfo = archivedItem.modInfo,
                    staged = null, // Will zip with staged items later to populate
                    isEnabledInSmol = false, // Archived-only items can't be enabled
                    isEnabledInGame = archivedItem.modInfo.id in enabledModIds,
                    archived = Mod.Archived(File(archivedItem.archivePath))
                )
            } ?: emptyList()

        // Get items in staging
        val stagingMods: File = config.stagingPath?.toFileOrNull()!!

        val stagedMods = modInfoLoader.readModInfosFromFolderOfMods(stagingMods, onlySmolManagedMods = true)
            .map { (modFolder, modInfo) ->
                Mod(
                    modInfo = modInfo,
                    staged = Mod.Staged(folder = modFolder),
                    isEnabledInSmol = false,
                    isEnabledInGame = modInfo.id in enabledModIds,
                    archived = null
                )
            }
            .toList()

        val modsFolder = gamePath.getModsPath()
        val modsFolderMods = modInfoLoader.readModInfosFromFolderOfMods(modsFolder, onlySmolManagedMods = true)
            .map { (_, modInfo) ->
                Mod(
                    modInfo = modInfo,
                    staged = null,
                    isEnabledInSmol = true,
                    isEnabledInGame = modInfo.id in enabledModIds,
                    archived = null
                )
            }
            .toList()

        val result = (archivedMods + stagedMods + modsFolderMods).groupingBy { it.smolId }
            .reduce { _, accumulator, element ->
                accumulator.copy(
                    isEnabledInSmol = accumulator.isEnabledInSmol || element.isEnabledInSmol,
                    staged = accumulator.staged ?: element.staged,
                    archived = accumulator.archived ?: element.archived
                )
            }
            .values
            .filter { it.exists }
            .toList()
            .onEach { Logger.debug { "Loaded mod: $it" } }

        return result
    }
}