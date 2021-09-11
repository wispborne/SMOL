import com.squareup.moshi.adapter
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import org.hjson.JsonValue
import org.tinylog.Logger
import java.io.File


class Loader {
    fun getStarsectorPath(): File? {
        return kotlin.runCatching {
            Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, "SOFTWARE\\Fractal Softworks\\Starsector", "")
        }
            .mapCatching { File(it) }
            .onFailure {
                Logger.debug { it.message ?: "" }
                it.printStackTrace()
            }
            .onSuccess { Logger.debug { "Product Name: ${it.absolutePath}" } }
            .getOrNull()
    }

    fun getModsPath(starsectorPath: File = getStarsectorPath()!!): File {
        val mods = File(starsectorPath, "mods")

        if (!mods.exists()) {
            mods.mkdirs()
        }

        return mods
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun getMods(modsPath: File = getModsPath()): List<Mod> {
        return modsPath
            .walkTopDown().maxDepth(1)
            .mapNotNull { modFolder ->
                Logger.debug { "Folder: ${modFolder.name}" }
                val modInfo = modFolder
                    .walkTopDown().maxDepth(1)
                    .firstOrNull {
                        Logger.debug { "  File: ${it.name}" }
                        it.name.equals("mod_info.json")
                    } ?: return@mapNotNull null

                val json = JsonValue.readHjson(modInfo.readText())
                val jsonStr = json.toString()
                    .also { Logger.debug { it.toString() } }

                Mod(
                    modInfo = SL.moshi.run {
                        // Check for 0.95 format
                        if (json.asObject().get("version").isObject) {
                            this.adapter<ModInfo.v095>()
                        } else {
                            this.adapter<ModInfo.v091>()
                        }
                    }
                        .fromJson(jsonStr)!!
                )
            }
            .toList()
    }
}