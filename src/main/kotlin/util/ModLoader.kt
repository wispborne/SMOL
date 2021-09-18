package util

import model.Mod
import org.tinylog.Logger
import java.io.File

class ModLoader(
    val gamePath: GamePath,
    val config: AppConfig,
    val archives: Archives,
    val modInfoLoader: ModInfoLoader
) {

    @OptIn(ExperimentalStdlibApi::class)
    fun getMods(): List<Mod> {
        // Get items in archives
        val archivedMods = archives.getArchivesManifest()?.manifestItems?.values
            ?.map { archivedItem ->
                Logger.trace { "Archive: ${archivedItem.modInfo.name}" }

                Mod(
                    modInfo = archivedItem.modInfo,
                    staged = null, // Will zip with staged items later to populate
                    isEnabled = false, // Archived-only items can't be enabled
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
                    isEnabled = false,
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
                    isEnabled = true,
                    archived = null
                )
            }
            .toList()

        val result = (archivedMods + stagedMods + modsFolderMods).groupingBy { it.smolId }
            .reduce { _, accumulator, element ->
                accumulator.copy(
                    isEnabled = accumulator.isEnabled || element.isEnabled,
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