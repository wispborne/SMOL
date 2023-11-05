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

package smol/*
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

import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import smol.utilities.copyToClipboard
import java.io.File
import java.util.*


/**
 * v1.2.0
 *
 * Original Python script by Dark Revenant.
 * Transcoded to Kotlin and edited to show more info by Wisp.
 */

private const val GRAPHICSLIB_ID = "shaderLib"

//val smol.gameModsFolder = File("C:\\Program Files (x86)\\Fractal Softworks\\Starsector\\mods")
private val currentFolder = File(System.getProperty("user.dir"))
private val gameModsFolder: File = currentFolder.parentFile


suspend fun main(args: Array<String>) {
    val properties = runCatching {
        Properties().apply { load(File(currentFolder, "config.properties").bufferedReader()) }
    }.getOrNull()
    val showSkippedFiles = properties?.getProperty("showSkippedFiles")?.toBoolean() ?: false
    val showCountedFiles = properties?.getProperty("showCountedFiles")?.toBoolean() ?: true
    val showPerformance = properties?.getProperty("showPerformance")?.toBoolean() ?: true
    val showGfxLibDebugOutput = properties?.getProperty("showGfxLibDebugOutput")?.toBoolean() ?: false
    val areGfxLibNormalMapsEnabledProp = properties?.getProperty("areGfxLibNormalMapsEnabled")?.toBoolean()
    val areGfxLibMaterialMapsEnabledProp = properties?.getProperty("areGfxLibMaterialMapsEnabled")?.toBoolean()
    val areGfxLibSurfaceMapsEnabledProp = properties?.getProperty("areGfxLibSurfaceMapsEnabled")?.toBoolean()

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
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build()

    val errorOut = StringBuilder()
    val enabledModIds = getUserEnabledModIds(jsonMapper, errorOut)

    val graphicsLibConfig =
        if (enabledModIds?.contains(GRAPHICSLIB_ID) != true) {
            GraphicsLibConfig.Disabled
        } else {
            // GraphicsLib enabled
            if (listOf(
                    areGfxLibNormalMapsEnabledProp,
                    areGfxLibMaterialMapsEnabledProp,
                    areGfxLibSurfaceMapsEnabledProp
                ).any { it == null }
            ) {
                askUserForGfxLibConfig()
            } else {
                GraphicsLibConfig(
                    areAnyEffectsEnabled = true,
                    areGfxLibNormalMapsEnabled = areGfxLibNormalMapsEnabledProp!!,
                    areGfxLibMaterialMapsEnabled = areGfxLibMaterialMapsEnabledProp!!,
                    areGfxLibSurfaceMapsEnabled = areGfxLibSurfaceMapsEnabledProp!!
                )
            }
        }

    runCatching {
        VramChecker(
            enabledModIds = enabledModIds,
            modIdsToCheck = null,
            showGfxLibDebugOutput = showGfxLibDebugOutput,
            showPerformance = showPerformance,
            showSkippedFiles = showSkippedFiles,
            showCountedFiles = showCountedFiles,
            graphicsLibConfig = graphicsLibConfig,
            foldersToCheck = gameModsFolder.toPath().toList(),
            verboseOut = { println(it) },
            debugOut = { println(it) }
        )
            .also { it.check() }
    }
        .onFailure {
            println(it.message)
            readLine()
        }
        .getOrNull()
        ?.run {
            copyToClipboard(summaryText.toString())
            val outputFile = File("$currentFolder/VRAM_usage_of_mods.txt")
            outputFile.delete()
            outputFile.createNewFile()
            outputFile.writeText(progressText.toString())
            outputFile.appendText(modTotals.toString())
            outputFile.appendText(summaryText.toString())

            debugOut("\nFile written to ${outputFile.absolutePath}.\nSummary copied to clipboard, ready to paste.")
        }
}


fun getUserEnabledModIds(jsonMapper: JsonMapper, errorOut: StringBuilder): List<String>? {
    val enabledModsJsonFile = currentFolder.parentFile?.listFiles()
        ?.firstOrNull { it.name == "enabled_mods.json" }

    if (enabledModsJsonFile == null || !enabledModsJsonFile.exists()) {
        errorOut.appendAndPrint("Unable to find 'enabled_mods.json'.")
        return null
    }

    return try {
        jsonMapper.readValue(enabledModsJsonFile, EnabledModsJsonModel::class.java).enabledMods
    } catch (e: Exception) {
        errorOut.appendAndPrint(e.toString())
        null
    }
}

private fun askUserForGfxLibConfig(): GraphicsLibConfig {
    println("GraphicsLib increases VRAM usage, which VRAM_Counter accounts for.")
    println("Have you modified the default GraphicsLib config? (y/N)")
    val didUserChangeConfig = parseYesNoInput(readLine(), defaultResultIfBlank = false)
        ?: return askUserForGfxLibConfig()

    var result = GraphicsLibConfig.Disabled

    if (!didUserChangeConfig) {
        return GraphicsLibConfig(
            areAnyEffectsEnabled = true,
            areGfxLibNormalMapsEnabled = true,
            areGfxLibMaterialMapsEnabled = true,
            areGfxLibSurfaceMapsEnabled = true
        )
    } else {
        println("Are normal maps enabled? (Y/n)")
        result = result.copy(
            areGfxLibNormalMapsEnabled = parseYesNoInput(readLine(), defaultResultIfBlank = true)
                ?: return askUserForGfxLibConfig()
        )
        println("Are material maps enabled? (Y/n)")
        result = result.copy(
            areGfxLibMaterialMapsEnabled = parseYesNoInput(readLine(), defaultResultIfBlank = true)
                ?: return askUserForGfxLibConfig()
        )
        println("Are surface maps enabled? (Y/n)")
        result = result.copy(
            areGfxLibSurfaceMapsEnabled = parseYesNoInput(readLine(), defaultResultIfBlank = true)
                ?: return askUserForGfxLibConfig()
        )

        return result
    }
}

private fun parseYesNoInput(input: String?, defaultResultIfBlank: Boolean): Boolean? =
    when {
        input.isNullOrBlank() -> defaultResultIfBlank
        listOf("n", "no").any { it.equals(input, ignoreCase = true) } -> false
        listOf("y", "yes").any { it.equals(input, ignoreCase = true) } -> true
        else -> null
    }

private fun StringBuilder.appendAndPrint(line: String) {
    println(line)
    this.appendLine(line)
}