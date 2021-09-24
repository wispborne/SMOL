package business

import com.google.gson.Gson
import com.squareup.moshi.Moshi
import model.ModInfo
import org.hjson.JsonValue
import org.tinylog.Logger
import util.IOLock
import java.io.File
import kotlin.concurrent.withLock

class ModInfoLoader(
    private val moshi: Moshi,
    private val gson: Gson
) {

    @OptIn(ExperimentalStdlibApi::class)
    fun readModInfosFromFolderOfMods(
        folderWithMods: File,
        onlySmolManagedMods: Boolean
    ): Sequence<Pair<File, ModInfo>> =
        IOLock.withLock {
            folderWithMods
                .walkTopDown().maxDepth(1)
                .mapNotNull { modFolder ->
                    Logger.trace { "Folder: ${modFolder.name}" }
                    val modInfoFile = modFolder
                        .walkTopDown().maxDepth(1)
                        .firstOrNull {
                            Logger.trace { "  File: ${it.name}" }
                            it.name.equals("mod_info.json")
                        } ?: return@mapNotNull null

                    if (onlySmolManagedMods && modInfoFile.parentFile?.let { isManagedBySmol(it) } != true) {
                        return@mapNotNull null
                    }

                    val json = JsonValue.readHjson(modInfoFile.readText())
                    val jsonStr = json.toString()
                        .also { Logger.trace { it } }

                    modFolder to gson.fromJson(jsonStr, ModInfo::class.java)
                    //moshi.adapter<ModInfo>().fromJson(jsonStr)!!
                }
        }

    private fun isManagedBySmol(modFolder: File) =
        IOLock.withLock {
            modFolder.walkTopDown().maxDepth(1).any { it.isSmolStagingMarker() }
        }
}