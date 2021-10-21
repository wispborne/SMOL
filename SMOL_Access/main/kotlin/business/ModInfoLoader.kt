package business

import com.google.gson.Gson
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import model.ModInfo
import org.hjson.JsonValue
import org.tinylog.Logger
import util.IOLock
import util.MOD_INFO_FILE
import java.io.File
import kotlin.concurrent.withLock

class ModInfoLoader(
    private val moshi: Moshi,
    private val gson: Gson
) {

    @OptIn(ExperimentalStdlibApi::class)
    fun readModInfosFromFolderOfMods(
        folderWithMods: File
    ): Sequence<Pair<File, ModInfo>> =
        IOLock.withLock {
            folderWithMods
                .walkTopDown().maxDepth(1)
                .filter { it.isDirectory }
                .mapNotNull { modFolder ->
                    Logger.debug { "Looking for mod_info.json file in folder: ${modFolder.absolutePath}" }
                    val modInfoFile = modFolder
                        .walkTopDown().maxDepth(1)
                        .filter { it != folderWithMods }
                        .firstOrNull {
                            Logger.trace { "  File: ${it.name}" }
                            it.name.equals(MOD_INFO_FILE)
                        } ?: return@mapNotNull null

                    modFolder to readModInfoFile(modInfoFile.readText())
                    //moshi.adapter<ModInfo>().fromJson(jsonStr)!!
                }
        }

    @OptIn(ExperimentalStdlibApi::class)
    fun readModInfoFile(modInfoJson: String): ModInfo {
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
}