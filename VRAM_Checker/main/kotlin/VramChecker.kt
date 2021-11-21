import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import de.siegmar.fastcsv.reader.CsvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import utilities.parallelMap
import utilities.walk
import java.nio.file.Path
import java.util.*
import javax.imageio.ImageIO
import kotlin.io.path.*

class VramChecker(
    val enabledModIds: List<String>?,
    val gameModsFolder: Path,
    val showGfxLibDebugOutput: Boolean,
    val showPerformance: Boolean,
    val showSkippedFiles: Boolean,
    val showCountedFiles: Boolean,
    val graphicsLibConfig: GraphicsLibConfig,
    val traceOut: (String) -> Unit = { println(it) },
    val debugOut: (String) -> Unit = { println(it) }
) {
    companion object {
        const val VANILLA_BACKGROUND_WIDTH = 2048
        const val VANILLA_BACKGROUND_TEXTURE_SIZE_IN_BYTES = 12582912f
        const val VANILLA_GAME_VRAM_USAGE_IN_BYTES =
            433586176 // 0.9.1a, per https://fractalsoftworks.com/forum/index.php?topic=8726.0
        internal const val OUTPUT_LABEL_WIDTH = 38

        /** If one of these strings is in the filename, the file is skipped **/
        val UNUSED_INDICATOR = listOf("CURRENTLY_UNUSED", "DO_NOT_USE")
        private const val BACKGROUND_FOLDER_NAME = "backgrounds"
    }

    var progressText = StringBuilder()
        private set
    var modTotals = StringBuilder()
        private set
    var summaryText = StringBuilder()
        private set
    var startTime = Date().time
        private set

    suspend fun check(): List<Mod> {
        progressText = StringBuilder()
        modTotals = StringBuilder()
        summaryText = StringBuilder()
        startTime = Date().time

        val csvReader = CsvReader.builder()
            .skipEmptyRows(true)
            .errorOnDifferentFieldCount(false)


        val jsonMapper = JsonMapper.builder()
            .defaultLeniency(true)
            .enable(
                JsonReadFeature.ALLOW_JAVA_COMMENTS, JsonReadFeature.ALLOW_SINGLE_QUOTES,
                JsonReadFeature.ALLOW_YAML_COMMENTS, JsonReadFeature.ALLOW_MISSING_VALUES,
                JsonReadFeature.ALLOW_TRAILING_COMMA, JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES,
                JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS,
                JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER,
                JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS
            )
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build()

        if (!gameModsFolder.exists()) {
            throw RuntimeException("This doesn't exist! ${gameModsFolder.absolutePathString()}")
        }

        if (enabledModIds != null) progressText.appendAndPrint(
            line = "\nEnabled Mods:\n${enabledModIds.joinToString(separator = "\n")}",
            traceOut = traceOut
        )

        progressText.appendAndPrint("Mods folder: ${gameModsFolder.absolutePathString()}", traceOut)

        val mods = gameModsFolder.listDirectoryEntries()
            .filter { it.isDirectory() }
            .mapNotNull {
                getModInfo(jsonMapper = jsonMapper, modFolder = it, progressText = progressText)
            }
            .map { modInfo ->
                progressText.appendAndPrint("\nFolder: ${modInfo.name}", traceOut)
                val startTimeForMod = Date().time

                val filesInMod =
                    modInfo.folder.walk()
                        .filter { it.isRegularFile() }
                        .toList()

                val graphicsLibFilesToExcludeForMod =
                    graphicsLibFilesToExcludeForMod(
                        filesInMod = filesInMod,
                        csvReader = csvReader,
                        progressText = progressText,
                        showGfxLibDebugOutput = showGfxLibDebugOutput,
                        graphicsLibConfig = graphicsLibConfig
                    )

                val timeFinishedGettingGraphicsLibData = Date().time
                if (showPerformance) progressText.appendAndPrint(
                    "Finished getting graphicslib data for ${modInfo.name} in ${(timeFinishedGettingGraphicsLibData - startTimeForMod)} ms",
                    traceOut
                )

                val modImages = filesInMod
                    .parallelMap { file ->
                        val image = try {
                            withContext(Dispatchers.IO) {
                                ImageIO.read(file.inputStream())!!
                            }
                        } catch (e: Exception) {
                            if (showSkippedFiles)
                                progressText.appendAndPrint(
                                    "Skipped non-image ${file.relativePath} (${e.message})",
                                    traceOut
                                )
                            return@parallelMap null
                        }

                        ModImage(
                            file = file,
                            textureHeight = if (image.width == 1) 1 else Integer.highestOneBit(image.width - 1) * 2,
                            textureWidth = if (image.height == 1) 1 else Integer.highestOneBit(image.height - 1) * 2,
                            bitsInAllChannels = image.colorModel.componentSize.toList(),
                            imageType = when {
                                file.relativePath.contains(BACKGROUND_FOLDER_NAME) -> ModImage.ImageType.Background
                                UNUSED_INDICATOR.any { suffix -> file.relativePath.contains(suffix) } -> ModImage.ImageType.Unused
                                else -> ModImage.ImageType.Texture
                            }
                        )
                    }
                    .filterNotNull()

                val timeFinishedGettingFileData = Date().time
                if (showPerformance) progressText.appendAndPrint(
                    "Finished getting file data for ${modInfo.formattedName} in ${(timeFinishedGettingFileData - timeFinishedGettingGraphicsLibData)} ms",
                    traceOut
                )

                val imagesToSumUp = modImages.toMutableList()

                imagesToSumUp.removeAll(modImages
                    .filter { it.imageType == ModImage.ImageType.Unused }
                    .also {
                        if (it.any() && showSkippedFiles) progressText.appendAndPrint(
                            "Skipping unused files",
                            traceOut
                        )
                    }
                    .onEach { progressText.appendAndPrint("  ${it.file.relativePath}", traceOut) }
                )


                // The game only loads one background at a time and vanilla always has one loaded.
                // Therefore, a mod only increases the VRAM use by the size difference of the largest background over vanilla.
                val largestBackgroundBiggerThanVanilla = modImages
                    .filter { it.imageType == ModImage.ImageType.Background && it.textureWidth > VANILLA_BACKGROUND_WIDTH }
                    .maxByOrNull { it.bytesUsed }
                imagesToSumUp.removeAll(
                    modImages.filter { it.imageType == ModImage.ImageType.Background && it != largestBackgroundBiggerThanVanilla }
                        .also {
                            if (it.any())
                                progressText.appendAndPrint(
                                    "Skipping backgrounds that are not larger than vanilla and/or not the mod's largest background.",
                                    traceOut
                                )
                        }
                        .onEach { progressText.appendAndPrint("   ${it.file.relativePath}", traceOut) }
                )

                imagesToSumUp.forEach { image ->
                    if (showCountedFiles) progressText.appendAndPrint(
                        "${image.file.relativePath} - TexHeight: ${image.textureHeight}, " +
                                "TexWidth: ${image.textureWidth}, " +
                                "Channels: ${image.bitsInAllChannels}, " +
                                "Mult: ${image.multiplier}\n" +
                                "   --> ${image.textureHeight} * ${image.textureWidth} * ${image.bitsInAllChannels.sum()} * ${image.multiplier} = ${image.bytesUsed} bytes added over vanilla",
                        traceOut
                    )
                }

                val imagesWithoutExcludedGfxLibMaps =
                    if (graphicsLibFilesToExcludeForMod != null)
                        imagesToSumUp.filterNot { image ->
                            image.file.relativeTo(modInfo.folder) in graphicsLibFilesToExcludeForMod
                                .map { Path.of(it.relativeFilePath) }
                        }
                    else imagesToSumUp

                val mod = Mod(
                    info = modInfo,
                    isEnabled = modInfo.id in (enabledModIds ?: emptyList()),
                    images = imagesWithoutExcludedGfxLibMaps
                )

                if (showPerformance) progressText.appendAndPrint(
                    "Finished calculating file sizes for ${mod.info.formattedName} in ${(Date().time - timeFinishedGettingFileData)} ms",
                    traceOut
                )
                progressText.appendAndPrint(mod.totalBytesForMod.bytesAsReadableMiB, traceOut)
                mod
            }
            .sortedByDescending { it.totalBytesForMod }

        mods.forEach { mod ->
            modTotals.appendLine()
            modTotals.appendLine("${mod.info.formattedName} - ${mod.images.count()} images - ${if (mod.isEnabled) "Enabled" else "Disabled"}")
            modTotals.appendLine(mod.totalBytesForMod.bytesAsReadableMiB)
        }

        val enabledMods = mods.filter { mod -> mod.isEnabled }
        val totalBytes = mods.getBytesUsedByDedupedImages()

        val totalBytesOfEnabledMods = enabledMods
            .getBytesUsedByDedupedImages()

        if (showPerformance) progressText.appendAndPrint(
            "Finished run in ${(Date().time - startTime)} ms",
            traceOut
        )

        val enabledModsString =
            enabledMods.joinToString(separator = "\n    ") { it.info.formattedName }.ifBlank { "(none)" }

        progressText.appendAndPrint("\n", traceOut)
        summaryText.appendLine()
        summaryText.appendLine("-------------")
        summaryText.appendLine("VRAM Use Estimates")
        summaryText.appendLine()
        summaryText.appendLine("Configuration")
        summaryText.appendLine("  Enabled Mods")
        summaryText.appendLine("    $enabledModsString")
        summaryText.appendLine("  GraphicsLib")
        summaryText.appendLine("    Normal Maps Enabled: ${graphicsLibConfig.areGfxLibNormalMapsEnabled}")
        summaryText.appendLine("    Material Maps Enabled: ${graphicsLibConfig.areGfxLibMaterialMapsEnabled}")
        summaryText.appendLine("    Surface Maps Enabled: ${graphicsLibConfig.areGfxLibSurfaceMapsEnabled}")
        summaryText.appendLine("    Edit 'config.properties' to choose your GraphicsLib settings.")
        kotlin.runCatching {
            getGPUInfo()?.also { info ->
                summaryText.appendLine("  System")
                summaryText.appendLine(info.getGPUString()?.joinToString(separator = "\n") { "    $it" })

                // If expected VRAM after loading game and mods is less than 300 MB, show warning
                if (info.getFreeVRAM() - (totalBytesOfEnabledMods + VANILLA_GAME_VRAM_USAGE_IN_BYTES) < 300000) {
                    summaryText.appendLine()
                    summaryText.appendLine("WARNING: You may not have enough free VRAM to run your current modlist.")
                }
            }
        }
            .onFailure {
                summaryText.appendLine()
                summaryText.appendLine("Unable to get GPU information due to the follow error:")
                summaryText.appendLine(it.stackTraceToString())
            }
        summaryText.appendLine()

        summaryText.appendLine("Enabled + Disabled Mods w/o Vanilla".padEnd(OUTPUT_LABEL_WIDTH) + totalBytes.bytesAsReadableMiB)
        summaryText.appendLine("Enabled + Disabled Mods w/ Vanilla".padEnd(OUTPUT_LABEL_WIDTH) + (totalBytes + VANILLA_GAME_VRAM_USAGE_IN_BYTES).bytesAsReadableMiB)
        summaryText.appendLine()
        summaryText.appendLine("Enabled Mods w/o Vanilla".padEnd(OUTPUT_LABEL_WIDTH) + totalBytesOfEnabledMods.bytesAsReadableMiB)
        summaryText.appendLine("Enabled Mods w/ Vanilla".padEnd(OUTPUT_LABEL_WIDTH) + (totalBytesOfEnabledMods + VANILLA_GAME_VRAM_USAGE_IN_BYTES).bytesAsReadableMiB)

        summaryText.appendLine()
        summaryText.appendLine("** This is only an estimate of VRAM use and actual use may be higher or lower.")
        summaryText.appendLine("** Unused images in mods are counted unless they contain one of ${UNUSED_INDICATOR.joinToString { "\"$it\"" }} in the file name.")

        traceOut(modTotals.toString())
        debugOut(summaryText.toString())
        return mods
    }

    private fun getModInfo(jsonMapper: JsonMapper, modFolder: Path, progressText: StringBuilder): ModInfo? {
        return try {
            modFolder.listDirectoryEntries()
                ?.firstOrNull { file -> file.name == "mod_info.json" }
                ?.let { modInfoFile ->
                    runCatching {
                        val model = jsonMapper.readValue(modInfoFile.inputStream(), ModInfoJsonModel_095a::class.java)

                        ModInfo(
                            id = model.id,
                            folder = modFolder,
                            name = model.name,
                            version = "${model.version.major}.${model.version.minor}.${model.version.patch}"
                        )
                    }
                        .recoverCatching {
                            val model =
                                jsonMapper.readValue(modInfoFile.inputStream(), ModInfoJsonModel_091a::class.java)

                            ModInfo(
                                id = model.id,
                                folder = modFolder,
                                name = model.name,
                                version = model.version
                            )
                        }
                        .getOrThrow()
                }
        } catch (e: Exception) {
            progressText.appendAndPrint(
                "Unable to find or read 'mod_info.json' in ${modFolder.absolutePathString()}. (${e.message})",
                traceOut
            )
            null
        }
    }

    private fun List<Mod>.getBytesUsedByDedupedImages(): Long = this
        .flatMap { mod -> mod.images.map { img -> mod.info.folder to img } }
        .distinctBy { (modFolder: Path, image: ModImage) -> image.file.relativeTo(modFolder).pathString + image.file.name }
        .sumOf { it.second.bytesUsed }

    private fun graphicsLibFilesToExcludeForMod(
        filesInMod: List<Path>,
        csvReader: CsvReader.CsvReaderBuilder,
        progressText: StringBuilder,
        showGfxLibDebugOutput: Boolean,
        graphicsLibConfig: GraphicsLibConfig
    ): List<GraphicsLibInfo>? {
        return filesInMod
            .filter { it.name.endsWith(".csv") }
            .mapNotNull { file ->
                runCatching { csvReader.build(file.reader()) }
                    .recover {
                        progressText.appendAndPrint("Unable to read ${file.relativePath}: ${it.message}", traceOut)
                        null
                    }
                    .getOrNull()
                    ?.asSequence()
                    ?.map { it.fields }
            }
            // Look for a CSV with a header row containing certain column names
            .firstOrNull {
                it.first().containsAll(listOf("id", "type", "map", "path"))
            }
            ?.run {
                val mapColumn = this.first().indexOf("map")
                val pathColumn = this.first().indexOf("path")

                this.mapNotNull { row: List<String> ->
                    try {
                        val mapType = when (row[mapColumn]) {
                            "normal" -> GraphicsLibInfo.MapType.Normal
                            "material" -> GraphicsLibInfo.MapType.Material
                            "surface" -> GraphicsLibInfo.MapType.Surface
                            else -> return@mapNotNull null
                        }
                        val path = row[pathColumn].trim()
                        GraphicsLibInfo(mapType, path)
                    } catch (e: Exception) {
                        progressText.appendAndPrint("$row - ${e.message}", traceOut)
                        null
                    }
                }
            }
            ?.filter {
                when (it.mapType) {
                    GraphicsLibInfo.MapType.Normal -> !graphicsLibConfig.areGfxLibNormalMapsEnabled
                    GraphicsLibInfo.MapType.Material -> !graphicsLibConfig.areGfxLibMaterialMapsEnabled
                    GraphicsLibInfo.MapType.Surface -> !graphicsLibConfig.areGfxLibSurfaceMapsEnabled
                }
            }
            .also {
                if (showGfxLibDebugOutput) it?.forEach { info -> progressText.appendAndPrint(info.toString(), traceOut) }
            }
            ?.toList()
    }

    private fun StringBuilder.appendAndPrint(line: String, traceOut: (String) -> Unit) {
        traceOut(line)
        this.appendLine(line)
    }

    private val Path.relativePath: String
        get() = this.relativeTo(gameModsFolder).pathString
}