package business

import com.google.gson.Gson
import com.squareup.moshi.Moshi
import model.ModInfo
import model.VersionCheckerInfo
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.hjson.JsonValue
import org.tinylog.Logger
import util.IOLock
import util.MOD_INFO_FILE
import util.VERSION_CHECKER_CSV_PATH
import java.io.File

class ModInfoLoader(
    private val moshi: Moshi,
    private val gson: Gson
) {

    @OptIn(ExperimentalStdlibApi::class)
    fun readModDataFilesFromFolderOfMods(
        folderWithMods: File,
        desiredFiles: List<DataFile>
    ): Sequence<Pair<File, DataFiles>> =
        IOLock.read {
            folderWithMods
                .walkTopDown().maxDepth(1)
                .filter { it.isDirectory }
                .mapNotNull { modFolder ->
                    var modInfo: ModInfo? = null

                    Logger.debug {
                        "Looking for mod_info.json and ${
                            if (desiredFiles.isEmpty())
                                "nothing else"
                            else desiredFiles.joinToString()
                        } in folder: ${modFolder.absolutePath}"
                    }
                    modFolder
                        .walkTopDown().maxDepth(1)
                        .filter { it != folderWithMods }
                        .forEach { file ->
                            Logger.trace { "  File: ${file.name}" }

                            if (modInfo == null && file.name.equals(MOD_INFO_FILE)) {
                                modInfo = deserializeModInfoFile(file.readText())
                            }
                        }

                    if (modInfo == null) {
                        return@mapNotNull null
                    } else {
                        var versionCheckerInfo: VersionCheckerInfo? = null

                        if (desiredFiles.contains(DataFile.VERSION_CHECKER)) {
                            val verCheckerCsv = File(modFolder, VERSION_CHECKER_CSV_PATH)

                            if (verCheckerCsv.exists()) {
                                kotlin.runCatching {
                                    val versionFilePath =
                                        CSVParser(verCheckerCsv.bufferedReader(), CSVFormat.DEFAULT).records[1][0]

                                    versionCheckerInfo =
                                        deserializeVersionCheckerFile(File(modFolder, versionFilePath).readText())
                                }
                                    .onFailure { Logger.warn(it) }
                                    .getOrNull()
                            }
                        }

                        return@mapNotNull modFolder to DataFiles(modInfo!!, versionCheckerInfo)
                    }
                }
        }

    @OptIn(ExperimentalStdlibApi::class)
    fun deserializeModInfoFile(modInfoJson: String): ModInfo {
        try {
            val json = JsonValue.readHjson(modInfoJson)
            val jsonStr = json.toString()
                .also { Logger.trace { it } }

            return gson.fromJson(jsonStr, ModInfo::class.java)
//            return moshi.adapter<ModInfo>().fromJson(jsonStr)!!
        } catch (ex: Exception) {
            Logger.warn(ex) { "Error reading mod_info.json: $modInfoJson" }
            throw ex
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun deserializeVersionCheckerFile(vcJson: String): VersionCheckerInfo {
        try {
            val json = JsonValue.readHjson(vcJson)
            val jsonStr = json.toString()
                .also { Logger.trace { it } }

            return gson.fromJson(jsonStr, VersionCheckerInfo::class.java)
        } catch (ex: Exception) {
            Logger.warn(ex) { "Error reading version checker file: $vcJson" }
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