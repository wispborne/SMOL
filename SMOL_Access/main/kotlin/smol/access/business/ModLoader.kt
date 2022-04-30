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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import smol.access.Constants
import smol.access.config.GamePathManager
import smol.access.model.Mod
import smol.access.model.ModId
import smol.access.model.ModVariant
import smol.timber.ktx.Timber
import smol.timber.ktx.i
import smol.utilities.asList
import smol.utilities.trace
import java.nio.file.Path
import kotlin.io.path.absolutePathString

internal class ModLoader internal constructor(
    private val gamePathManager: GamePathManager,
    private val modInfoLoader: ModInfoLoader,
    private val gameEnabledMods: GameEnabledMods,
    private val modsCache: ModsCache,
    private val staging: Staging
) {

    private var isReloadingMutable = MutableStateFlow(false)
    val isLoading = isReloadingMutable.asStateFlow()

    /**
     * Reads all or specific mods from /mods, staging folders.
     */
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun reload(modIds: List<ModId>? = null): ModListUpdate? {
        if (isLoading.value) {
            Timber.i { "Mod reload requested, but declined; already reloading." }
            return modsCache.mods.value
        }

        Timber.i { "Refreshing mod info files: ${modIds ?: "all"}." }

        val previousMods = modsCache.mods.value
        val previousModVariants = previousMods?.mods?.flatMap { it.variants }

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

                    // Get items in /mods folder
                    val modsFolder = gamePathManager.getModsPath() ?: run {
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
                                    modsFolderInfo = Mod.ModsFolderInfo(folder = modFolder)
                                )
                                Mod(
                                    id = modInfo.id,
                                    isEnabledInGame = modInfo.id in enabledModIds,
                                    variants = modVariant.asList()
                                )
                            }
                            .toList()
                            .onEach { Timber.v { "Found /mods mod $it" } }
                    val gameLauncherEnabledMods = gameEnabledMods.getEnabledMods()?.enabledMods ?: emptyList()

                    // Merge all items together, replacing nulls with data.
                    val result = (modsFolderMods)
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
                        .toList()
                        .onEach {
                            val variantsInModsFolder =
                                it.variants.filter { it.isModInfoEnabled && it.modInfo.id in gameLauncherEnabledMods }

                            if (variantsInModsFolder.size > 1) {
                                Timber.w {
                                    "${it.id} has multiple enabled variants in /mods: ${
                                        variantsInModsFolder.joinToString {
                                            it.modsFolderInfo.folder.absolutePathString()
                                        }
                                    }"
                                }
                            }
                        }

                    val currentGamePath = gamePathManager.path.value
                    val hasGamePathChanged = previousMods?.gamePath != currentGamePath

                    val update = if (modIds == null) {
                        val newModVariants = result.flatMap { it.variants }
                        ModListUpdate(
                            gamePath = currentGamePath,
                            mods = result,
                            added = if (previousModVariants == null || hasGamePathChanged) emptyList()
                            else newModVariants.filter { it.smolId !in previousModVariants.map { it.smolId } },
                            removed = previousModVariants?.filter { it.smolId !in newModVariants.map { it.smolId } }
                                ?: emptyList()
                        )
                    } else {
                        // If this was an update of only some mods, update only those.
                        val updatedList = modsCache.modsMutable.value?.mods?.toMutableList().apply {
                            result.forEach { selectedMod ->
                                this?.removeIf { it.id == selectedMod.id }
                                this?.add(selectedMod)
                            }
                        } ?: emptyList()
                        val updatedListVariants = updatedList.flatMap { it.variants }

                        ModListUpdate(
                            gamePath = currentGamePath,
                            mods = updatedList,
                            added = if (previousModVariants == null || hasGamePathChanged) emptyList()
                            else updatedListVariants.filter { it.smolId !in previousModVariants.map { it.smolId } },
                            removed = previousModVariants?.filter { it.smolId !in updatedListVariants.map { it.smolId } }
                                ?: emptyList()
                        )
                    }
                        .also {
                            it.added.forEach {
                                staging.disableModVariant(
                                    modVariant = it,
                                    disableInVanillaLauncher = false
                                )
                            }
                        }
                        .also {
                            it.added.onEach { Timber.i { "Added mod: $it" } }
                            it.removed.onEach { Timber.i { "Removed mod: $it" } }
                            it.mods.onEach { Timber.d { "Loaded mod: $it" } }
                        }

                    modsCache.modsMutable.emit(update)

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
    val gamePath: Path?,
    val mods: List<Mod>,
    val added: List<ModVariant>,
    val removed: List<ModVariant>
)