package smol_access.business

import GraphicsLibConfig
import VramChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import smol_access.Constants
import smol_access.config.GamePath
import smol_access.config.VramCheckerCache
import smol_access.model.Mod
import smol_access.model.ModVariant
import smol_access.model.SmolId
import smol_access.model.Version
import timber.ktx.Timber

class VramCheckerManager(
    private val gamePath: GamePath,
    private val vramCheckerCache: VramCheckerCache
) {
    private val _vramUsage = MutableStateFlow(vramCheckerCache.bytesPerVariant)
    val vramUsage = _vramUsage.asStateFlow()

    /**
     * Warning: this is very slow.
     */
    suspend fun refreshVramUsage(mods: List<Mod>): Map<SmolId, VramCheckerCache.Result> {
        val modIdsToUpdate = mods.map { it.id }

        Timber.i { "Refreshing VRAM use of ${modIdsToUpdate.count()} mods." }

        val modsPath = gamePath.getModsPath()

        val results = VramChecker(
            enabledModIds = mods.filter { it.hasEnabledVariant }.map { it.id },
            modIdsToCheck = modIdsToUpdate,
            foldersToCheck = listOfNotNull(modsPath, Constants.STAGING_FOLDER_DEFAULT),
            showGfxLibDebugOutput = false,
            showPerformance = false,
            showSkippedFiles = false,
            showCountedFiles = false,
            graphicsLibConfig = GraphicsLibConfig(
                // TODO Set these properly.
                areGfxLibNormalMapsEnabled = true,
                areGfxLibMaterialMapsEnabled = true,
                areGfxLibSurfaceMapsEnabled = true
            ),
            traceOut = { Timber.v { it } },
            debugOut = { Timber.d { it } },
        )
            .check()

        val mergedResult = _vramUsage.value?.toMutableMap() ?: mutableMapOf()

        results
            .associateBy(keySelector = {
                ModVariant.createSmolId(it.info.id, Version.parse(it.info.version))
            }) {
                VramCheckerCache.Result(
                    modId = it.info.id,
                    version = it.info.version,
                    bytesForMod = it.totalBytesForMod,
                    imageCount = it.images.count()
                )
            }
            .forEach { (smolId, result) ->
                mergedResult[smolId] = result
            }

        _vramUsage.emit(mergedResult)
        vramCheckerCache.bytesPerVariant = mergedResult

        return mergedResult
    }
}