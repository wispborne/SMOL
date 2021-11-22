package smol_access.business

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import smol_access.config.AppConfig
import smol_access.config.GamePath
import smol_access.model.Mod
import smol_access.model.ModVariant
import timber.ktx.Timber
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
    private val gameEnabledMods: GameEnabledMods,
    private val versionChecker: VersionChecker
) {
    private val onModsReloadedEmitter = MutableStateFlow<List<Mod>?>(null)
    val mods = onModsReloadedEmitter.asStateFlow()

    private var isReloadingMutable = MutableStateFlow(false)
    val isLoading = isReloadingMutable.asStateFlow()

    /**
     * Reads all mods from /mods, staging, and archive folders.
     */
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun reload(): List<Mod>? {
        if (isLoading.value) {
            Timber.i { "Mod reload requested, but declined; already reloading." }
            return mods.value
        }

        Timber.i { "Refreshing mod info files." }

        return try {
            isReloadingMutable.emit(true)
            trace({ _, time -> Timber.i { "Time to load and merge all mod info files: ${time}ms" } }) {
                val enabledModIds = gameEnabledMods.getEnabledMods().enabledMods

                // Get items in archives
                val archivedMods = archives.getArchivesManifest()?.manifestItems?.values
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
                        stagingMods,
                        listOf(ModInfoLoader.DataFile.VERSION_CHECKER)
                    )
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
                                            versionCheckerInfo = acc.versionCheckerInfo ?: element.versionCheckerInfo
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
                    .onEach { mod -> mod.variants.forEach { it.mod = mod } }
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

                onModsReloadedEmitter.tryEmit(result)
                GlobalScope.launch {
                    versionChecker.lookUpVersions(result)
                }
                result
            }
        } finally {
            isReloadingMutable.emit(false)
        }
    }
}