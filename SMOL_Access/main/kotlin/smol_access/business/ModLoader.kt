package smol_access.business

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import smol_access.Constants
import smol_access.config.AppConfig
import smol_access.config.GamePath
import smol_access.model.Mod
import smol_access.model.ModId
import smol_access.model.ModVariant
import timber.ktx.Timber
import timber.ktx.i
import utilities.asList
import utilities.toPathOrNull
import utilities.trace
import java.nio.file.Path
import kotlin.io.path.absolutePathString

internal class ModLoader internal constructor(
    private val gamePath: GamePath,
    private val config: AppConfig,
    private val archives: Archives,
    private val modInfoLoader: ModInfoLoader,
    private val gameEnabledMods: GameEnabledMods
) {
    private val modsMutable = MutableStateFlow<ModListUpdate?>(null)
    val mods = modsMutable.asStateFlow()
        .also {
            GlobalScope.launch(Dispatchers.Default) {
                it.collect { Timber.i { "Mod list updated: ${it?.mods?.size} mods (${it?.added?.joinToString { it.smolId }} added, ${it?.removed?.joinToString { it.smolId }} removed)." } }
            }
        }

    private var isReloadingMutable = MutableStateFlow(false)
    val isLoading = isReloadingMutable.asStateFlow()

    /**
     * Reads all or specific mods from /mods, staging, and archive folders.
     */
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun reload(modIds: List<ModId>? = null): ModListUpdate? {
        if (isLoading.value) {
            Timber.i { "Mod reload requested, but declined; already reloading." }
            return mods.value
        }

        Timber.i { "Refreshing mod info files: ${modIds ?: "all"}." }

        val previousMods = mods.value
        val previousModVariants = (previousMods?.mods?.flatMap { it.variants } ?: emptyList())

        return try {
            isReloadingMutable.emit(true)
            trace({ mods, time ->
                Timber.tag(Constants.TAG_TRACE)
                    .i { "Time to load and merge all ${mods?.mods?.count()} mod info files: ${time}ms" }
            }) {
                withContext(Dispatchers.IO) {
                    val enabledModIds = gameEnabledMods.getEnabledMods()?.enabledMods ?: run {
                        Timber.w { "Couldn't get enabled mods, cannot load mods." }
                        return@withContext null
                    }

                    // Get items in archives
                    val archivedMods = archives.getArchivesManifest()?.manifestItems?.values
                        ?.filter { modIds?.contains(it.modInfo.id) ?: true } // Filter to the selected mods, if not null
                        ?.map { archivedItem ->
                            Timber.v { "Archive: ${archivedItem.modInfo.name}" }

                            val modVariant = ModVariant(
                                modInfo = archivedItem.modInfo,
                                versionCheckerInfo = archivedItem.versionCheckerInfo,
                                modsFolderInfo = null,  // Will zip with mods items later to populate
                                stagingInfo = null, // Will zip with staged items later to populate
                                archiveInfo = ModVariant.ArchiveInfo(Path.of(archivedItem.archivePath)),
                            )
                            Mod(
                                id = archivedItem.modInfo.id,
                                isEnabledInGame = archivedItem.modInfo.id in enabledModIds,
                                variants = listOf(modVariant)
                            )
                        }
                        ?.onEach { Timber.v { "Found archived mod $it" } }
                        ?: emptyList()

                    // Get items in staging
                    val stagingMods = config.stagingPath?.toPathOrNull()!!

                    val stagedMods =
                        modInfoLoader.readModDataFilesFromFolderOfMods(
                            folderWithMods = stagingMods,
                            desiredFiles = listOf(ModInfoLoader.DataFile.VERSION_CHECKER)
                        )
                            .filter {
                                modIds?.contains(it.second.modInfo.id) ?: true
                            } // Filter to the selected mods, if not null
                            .map { (modFolder, dataFiles) ->
                                val modInfo = dataFiles.modInfo
                                val modVariant = ModVariant(
                                    modInfo = modInfo,
                                    versionCheckerInfo = dataFiles.versionCheckerInfo,
                                    modsFolderInfo = null,  // Will zip with mods items later to populate
                                    stagingInfo = ModVariant.StagingInfo(folder = modFolder),
                                    archiveInfo = null,
                                )
                                Mod(
                                    id = modInfo.id,
                                    isEnabledInGame = modInfo.id in enabledModIds,
                                    variants = modVariant.asList()
                                )
                            }
                            .toList()
                            .onEach { Timber.v { "Found staged/installed mod $it" } }

                    // Get items in /mods folder
                    val modsFolder = gamePath.getModsPath() ?: run {
                        Timber.w { "No mods path set, cannot load mods." }
                        return@withContext null
                    }
                    val modsFolderMods =
                        modInfoLoader.readModDataFilesFromFolderOfMods(
                            modsFolder,
                            listOf(ModInfoLoader.DataFile.VERSION_CHECKER)
                        )
                            .filter {
                                modIds?.contains(it.second.modInfo.id) ?: true
                            } // Filter to the selected mods, if not null
                            .map { (modFolder, dataFiles) ->
                                val modInfo = dataFiles.modInfo
                                val modVariant = ModVariant(
                                    modInfo = modInfo,
                                    versionCheckerInfo = dataFiles.versionCheckerInfo,
                                    modsFolderInfo = Mod.ModsFolderInfo(folder = modFolder),
                                    stagingInfo = null,
                                    archiveInfo = null,
                                )
                                Mod(
                                    id = modInfo.id,
                                    isEnabledInGame = modInfo.id in enabledModIds,
                                    variants = modVariant.asList()
                                )
                            }
                            .toList()
                            .onEach { Timber.v { "Found /mods mod $it" } }

                    // Merge all items together, replacing nulls with data.
                    val result = (archivedMods + stagedMods + modsFolderMods)
                        .groupingBy { it.id }
                        .reduce { _, accumulator, newElement ->
                            accumulator.copy(
                                isEnabledInGame = accumulator.isEnabledInGame || newElement.isEnabledInGame,
                                variants = kotlin.run {
                                    val mergedVariants = accumulator.variants.toMutableList()
                                    newElement.variants.forEach { element ->
                                        val acc = mergedVariants.firstOrNull { it.smolId == element.smolId }

                                        // Either merge in the new element or add it to the list.
                                        if (acc != null) {
                                            mergedVariants[mergedVariants.indexOf(acc)] = acc.copy(
                                                modsFolderInfo = acc.modsFolderInfo ?: element.modsFolderInfo,
                                                stagingInfo = acc.stagingInfo ?: element.stagingInfo,
                                                archiveInfo = acc.archiveInfo ?: element.archiveInfo,
                                                versionCheckerInfo = acc.versionCheckerInfo
                                                    ?: element.versionCheckerInfo
                                            )
                                        } else {
                                            mergedVariants.add(element)
                                        }
                                    }
                                    mergedVariants
                                }
                            )
                        }
                        .values
                        .map { mod -> mod.copy(variants = mod.variants.sortedBy { it.modInfo.version }) }
                        .filter { mod -> mod.variants.any { it.exists } }
                        .toList()
                        .onEach {
                            Timber.d { "Loaded mod: $it" }

                            val variantsInModsFolder = it.variants.filter { variant -> variant.modsFolderInfo != null }

                            if (variantsInModsFolder.size > 1) {
                                Timber.w {
                                    "${it.id} has multiple variants in /mods: ${
                                        variantsInModsFolder.joinToString {
                                            it.modsFolderInfo!!.folder.absolutePathString()
                                        }
                                    }"
                                }
                            }
                        }

                    val update = if (modIds == null) {
                        val newModVariants = result.flatMap { it.variants }
                        ModListUpdate(
                            mods = result,
                            added = newModVariants.filter { it.smolId !in previousModVariants.map { it.smolId } },
                            removed = previousModVariants.filter { it.smolId !in newModVariants.map { it.smolId } }
                        )
                    } else {
                        // If this was an update of only some mods, update only those.
                        val updatedList = modsMutable.value?.mods?.toMutableList().apply {
                            result.forEach { selectedMod ->
                                this?.removeIf { it.id == selectedMod.id }
                                this?.add(selectedMod)
                            }
                        } ?: emptyList()
                        val updatedListVariants = updatedList.flatMap { it.variants }

                        ModListUpdate(
                            mods = updatedList,
                            added = updatedListVariants.filter { it.smolId !in previousModVariants.map { it.smolId } },
                            removed = previousModVariants.filter { it.smolId !in updatedListVariants.map { it.smolId } }
                        )
                    }
                    modsMutable.emit(update)

                    return@withContext update
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
            return null
        } finally {
            isReloadingMutable.emit(false)
        }
    }
}

data class ModListUpdate(
    val mods: List<Mod>,
    val added: List<ModVariant>,
    val removed: List<ModVariant>
)