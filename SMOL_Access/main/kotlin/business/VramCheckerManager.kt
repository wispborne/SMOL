package business

import GraphicsLibConfig
import VramChecker
import config.GamePath
import config.VramCheckerCache
import model.ModVariant
import model.SmolId
import model.Version
import org.tinylog.kotlin.Logger

class VramCheckerManager(
    private val modLoader: ModLoader,
    private val gamePath: GamePath,
    private val vramCheckerCache: VramCheckerCache
) {
    var cached: Map<SmolId, VramCheckerCache.Result>? = vramCheckerCache.bytesPerVariant
        private set(value) {
            field = value
            vramCheckerCache.bytesPerVariant = value
        }

    suspend fun getVramUsage(forceRefresh: Boolean): Map<SmolId, VramCheckerCache.Result> {
        if (!forceRefresh && cached != null) {
            Logger.debug { "Not refreshing VRAM use. forceRefresh: $forceRefresh, cached: ${cached?.size ?: 0} entries" }
            return cached!!
        }

        Logger.debug { "Refreshing VRAM use. forceRefresh: $forceRefresh, cached: ${cached?.size ?: 0} entries" }
        val results = VramChecker(
            enabledModIds = modLoader.getMods(noCache = false).map { it.id },
            gameModsFolder = gamePath.getModsPath(),
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
            stdOut = { Logger.trace(it) }
        )
            .check()

        val mergedResult = cached?.toMutableMap() ?: mutableMapOf()

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

        cached = mergedResult

        return cached!!
    }
}