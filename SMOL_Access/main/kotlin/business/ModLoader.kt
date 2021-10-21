package business

import config.AppConfig
import config.GamePath
import model.Mod
import model.ModVariant
import org.tinylog.Logger
import util.toFileOrNull
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

                val modVariant = ModVariant(
                    modInfo = archivedItem.modInfo,
                    modsFolderInfo = null,  // Will zip with mods items later to populate
                    stagingInfo = null, // Will zip with staged items later to populate
                    archiveInfo = ModVariant.ArchiveInfo(File(archivedItem.archivePath)),
                )
                Mod(
                    id = archivedItem.modInfo.id,
                    isEnabledInGame = archivedItem.modInfo.id in enabledModIds,
                    variants = mapOf(modVariant.smolId to modVariant)
                )
            }
            ?.onEach { Logger.trace { "Found archived mod $it" } }
            ?: emptyList()

        // Get items in staging
        val stagingMods: File = config.stagingPath?.toFileOrNull()!!

        val stagedMods = modInfoLoader.readModInfosFromFolderOfMods(stagingMods)
            .map { (modFolder, modInfo) ->
                val modVariant = ModVariant(
                    modInfo = modInfo,
                    modsFolderInfo = null,  // Will zip with mods items later to populate
                    stagingInfo = ModVariant.StagingInfo(folder = modFolder),
                    archiveInfo = null,
                )
                Mod(
                    id = modInfo.id,
                    isEnabledInGame = modInfo.id in enabledModIds,
                    variants = mapOf(modVariant.smolId to modVariant)
                )
            }
            .toList()
            .onEach { Logger.trace { "Found staged/installed mod $it" } }

        // Get items in /mods folder
        val modsFolder = gamePath.getModsPath()
        val modsFolderMods = modInfoLoader.readModInfosFromFolderOfMods(modsFolder)
            .map { (modFolder, modInfo) ->
                val modVariant = ModVariant(
                    modInfo = modInfo,
                    modsFolderInfo = Mod.ModsFolderInfo(folder = modFolder),
                    stagingInfo = null,
                    archiveInfo = null,
                )
                Mod(
                    id = modInfo.id,
                    isEnabledInGame = modInfo.id in enabledModIds,
                    variants = mapOf(modVariant.smolId to modVariant)
                )
            }
            .toList()
            .onEach { Logger.trace { "Found /mods mod $it" } }

        // Merge all items together, replacing nulls with data.
        val result = (archivedMods + stagedMods + modsFolderMods)
            .groupingBy { it.id }
            .reduce { _, accumulator, element ->
                accumulator.copy(
                    isEnabledInGame = accumulator.isEnabledInGame || element.isEnabledInGame,
                    variants = kotlin.run {
                        val ret = accumulator.variants.toMutableMap()
                        element.variants.forEach { (elementKey, elementValue) ->
                            val accValue = ret[elementKey]

                            // Either merge in the new element or add it to the list.
                            if (accValue != null) {
                                ret[elementKey] = accValue.copy(
                                    modsFolderInfo = accValue.modsFolderInfo ?: elementValue.modsFolderInfo,
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
            .filter { mod -> mod.variants.any { it.value.exists } }
            .onEach { mod -> mod.variants.values.forEach { it.mod = mod } }
            .toList()
            .onEach {
                Logger.debug { "Loaded mod: $it" }

                val variantsInModsFolder = it.variants.filter { variant -> variant.value.modsFolderInfo != null }

                if (variantsInModsFolder.size > 1) {
                    Logger.warn { "${it.id} has multiple variants in /mods: ${variantsInModsFolder.values.joinToString { it.modsFolderInfo!!.folder.absolutePath }}" }
                }
            }

        return result
    }
}