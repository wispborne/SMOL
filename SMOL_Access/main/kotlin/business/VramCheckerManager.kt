package business

import GraphicsLibConfig
import VramChecker
import config.GamePath
import model.ModId
import model.ModVariant
import model.SmolId
import model.Version
import org.tinylog.kotlin.Logger

class VramCheckerManager(
    private val modLoader: ModLoader,
    private val gamePath: GamePath
) {
    private var cached: Map<SmolId, VramCheckerMod>? = null

    suspend fun getVramUsage(shouldRefresh: Boolean): Map<SmolId, VramCheckerMod> {
        if (!shouldRefresh && cached != null) {
            return cached!!
        }

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

        cached = results
            .associateBy(keySelector = {
                ModVariant.createSmolId(it.info.id, Version.parse(it.info.version))
            }) {
                VramCheckerMod(
                    modId = it.info.id,
                    bytesForMod = it.totalBytesForMod,
                    imageCount = it.images.count()
                )
            }

        return cached!!
    }

    data class VramCheckerMod(
        val modId: ModId,
        val bytesForMod: Long,
        val imageCount: Int,
    )
}