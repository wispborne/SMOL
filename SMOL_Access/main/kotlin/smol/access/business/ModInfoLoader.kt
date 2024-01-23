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
import kotlinx.coroutines.runBlocking
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import smol.access.Constants
import smol.access.model.ModInfo
import smol.access.model.VersionCheckerInfo
import smol.timber.ktx.Timber
import smol.utilities.*
import java.nio.file.FileVisitOption
import java.nio.file.Path
import kotlin.io.path.*

class ModInfoLoader(
    private val gson: Jsanity
) {

    fun readModDataFilesFromFolderOfMods(
        folderWithMods: Path,
        desiredFiles: List<DataFile>
    ): Sequence<Pair<Path, DataFiles>> =
        IOLock.read {
            // Making the method suspending causes it to try unlocking lock from wrong thread.
            runBlocking {
                folderWithMods.walk(maxDepth = 1, FileVisitOption.FOLLOW_LINKS)
                    .plus(listOf(folderWithMods))
                    .filter { it.isDirectory() }
                    .toList()
                    .parallelMap(Dispatchers.IO) { modFolder ->
                        var modInfo: ModInfo? = null

                        Timber.v {
                            "Looking for mod_info.json[.disabled] and ${
                                if (desiredFiles.isEmpty())
                                    "nothing else"
                                else desiredFiles.joinToString()
                            } in folder: ${modFolder.absolutePathString()}"
                        }
                        modFolder.walk(maxDepth = 1, FileVisitOption.FOLLOW_LINKS)
                            .filter { it != folderWithMods }
                            .forEach { file ->
                                Timber.v { "  File: ${file.name}" }

                                if (modInfo == null
                                    && (file.name.equals(Constants.UNBRICKED_MOD_INFO_FILE, ignoreCase = true)
                                            || file.name.equalsAny(
                                        *Constants.MOD_INFO_FILE_DISABLED_NAMES,
                                        ignoreCase = true
                                    ))
                                ) {
                                    modInfo =
                                        runCatching { deserializeModInfoFile(file.readText(), file.name) }.getOrNull()
                                }
                            }

                        if (modInfo == null) {
                            return@parallelMap null
                        } else {
                            var versionCheckerInfo: VersionCheckerInfo? = null

                            if (desiredFiles.contains(DataFile.VERSION_CHECKER)) {
                                val verCheckerCsv = modFolder.resolve(Constants.VERSION_CHECKER_CSV_PATH)

                                if (verCheckerCsv.exists()) {
                                    runCatching {
                                        val versionFilePath =
                                            CSVParser(
                                                verCheckerCsv.bufferedReader(),
                                                CSVFormat.DEFAULT
                                            ).records[1][0]

                                        versionCheckerInfo =
                                            deserializeVersionCheckerFile(
                                                versionFilePath,
                                                modFolder.resolve(versionFilePath).readText()
                                            )

                                        if (versionCheckerInfo?.modThreadId != null) {
                                            versionCheckerInfo = versionCheckerInfo!!.copy(
                                                modThreadId = versionCheckerInfo!!.modThreadId!!.replace(
                                                    regex = Regex("[^0-9]"),
                                                    replacement = ""
                                                )
                                            )
                                            if (versionCheckerInfo?.modThreadId?.trimStart('0')
                                                    ?.isEmpty() == true
                                            ) {
                                                versionCheckerInfo = versionCheckerInfo!!.copy(modThreadId = null)
                                            }
                                        }
                                    }
                                        .onFailure { Timber.w { "This mod has defined a `data/config/version/version_files.csv`, but there was an error reading version checker file:\n  ${it.message}" } }
                                        .getOrNull()
                                }
                            }

                            return@parallelMap modFolder to DataFiles(modInfo!!, versionCheckerInfo)
                        }
                    }
                    .filterNotNull()
                    .asSequence()
            }
        }

    fun deserializeModInfoFile(modInfoJson: String, file: String): ModInfo {
        try {
            // Uses [GsonBuilder] to deserialize the mod info file.
            return gson.fromJson(
                json = modInfoJson,
                filename = file,
                classOfT = ModInfo::class.java,
                shouldStripComments = true
            )
//            val json = JsonValue.readHjson(modInfoJson)
//            val jsonStr = json.toString()
//                .also { Timber.v { it } }
//
//            return gson.fromJson(jsonStr, smol.ModInfo::class.java)
//            return moshi.adapter<smol.ModInfo>().fromJson(jsonStr)!!
        } catch (ex: Exception) {
            Timber.w(ex) { "Error reading mod_info.json: $modInfoJson" }
            throw ex
        }
    }

    fun deserializeVersionCheckerFile(file: String, vcJson: String): VersionCheckerInfo {
        try {
            return gson.fromJson(
                json = vcJson,
                filename = file,
                classOfT = VersionCheckerInfo::class.java,
                shouldStripComments = true
            )
//            val json = JsonValue.readHjson(vcJson)
//            val jsonStr = json.toString()
//                .also { Timber.v { it } }
//
//            return gson.fromJson(jsonStr, VersionCheckerInfo::class.java)
        } catch (ex: Exception) {
            Timber.w { "Error reading version checker file: $file" }
            Timber.d(ex) { "$file error: $vcJson" }
            throw ex
        }
    }

    enum class DataFile {
        VERSION_CHECKER
    }

    data class DataFiles(
        val modInfo: ModInfo,
        val versionCheckerInfo: VersionCheckerInfo?
    )
}