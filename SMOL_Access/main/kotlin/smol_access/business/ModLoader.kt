package smol_access.business

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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

class ModLoader internal constructor(
    private val gamePath: GamePath,
    private val config: AppConfig,
    private val archives: Archives,
    private val modInfoLoader: ModInfoLoader,
    private val gameEnabledMods: GameEnabledMods
) {
    private val onModsReloadedEmitter = MutableStateFlow<List<Mod>?>(null)
    val mods = onModsReloadedEmitter.asStateFlow()
        .also { GlobalScope.launch(Dispatchers.Default) { it.collect { Timber.d { "Mod list updated: ${it?.size} mods." } } } }

    private var isReloadingMutable = MutableStateFlow(false)
    val isLoading = isReloadingMutable.asStateFlow()

    /**
     * Reads all mods from /mods, staging, and archive folders.
     */
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun reload(modIds: List<ModId>? = null): List<Mod>? {
        if (isLoading.value) {
            Timber.i { "Mod reload requested, but declined; already reloading." }
            return mods.value
        }

        Timber.i { "Refreshing mod info files: ${modIds ?: "all"}." }

        return try {
            isReloadingMutable.emit(true)
            trace({ mods, time ->
                Timber.tag(Constants.TAG_TRACE).i { "Time to load and merge all ${mods.count()} mod info files: ${time}ms" }
            }) {
                withContext(Dispatchers.IO) {
                    val enabledModIds = gameEnabledMods.getEnabledMods().enabledMods

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
                            .filter { modIds?.contains(it.second.modInfo.id) ?: true } // Filter to the selected mods, if not null
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
                    val modsFolder = gamePath.getModsPath()
                    val modsFolderMods =
                        modInfoLoader.readModDataFilesFromFolderOfMods(
                            modsFolder,
                            listOf(ModInfoLoader.DataFile.VERSION_CHECKER)
                        )
                            .filter { modIds?.contains(it.second.modInfo.id) ?: true } // Filter to the selected mods, if not null
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
                        .filter { mod -> mod.variants.any { it.exists } }
//                        .onEach { mod -> mod.variants.forEach { it.mod = mod } }
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

                    onModsReloadedEmitter.emit(result)
                    return@withContext result
                }
            }
        } catch (e: Exception) {
            Timber.e(e)
            return emptyList()
        } finally {
            isReloadingMutable.emit(false)
        }
    }
}