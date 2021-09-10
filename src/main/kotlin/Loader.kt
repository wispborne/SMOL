import com.beust.klaxon.Klaxon
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import org.hjson.JsonValue
import java.io.File


object Loader {
    fun getStarsectorPath(): File? {
        return kotlin.runCatching {
            Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, "SOFTWARE\\Fractal Softworks\\Starsector", "")
        }
            .mapCatching { File(it) }
            .onFailure {
                println(it.message)
                it.printStackTrace()
            }
            .onSuccess { println("Product Name: ${it.absolutePath}") }
            .getOrNull()
    }

    fun getModsPath(starsectorPath: File = getStarsectorPath()!!): File {
        val mods = File(starsectorPath, "mods")

        if (!mods.exists()) {
            mods.mkdirs()
        }

        return mods
    }

    fun getMods(modsPath: File = getModsPath()): List<Mod> {
        return modsPath
            .walkTopDown().maxDepth(1)
            .mapNotNull { modFolder ->
                println("Folder: ${modFolder.name}")
                val modInfo = modFolder
                    .walkTopDown().maxDepth(1)
                    .firstOrNull {
                        println("  File: ${it.name}")
                        it.name.equals("mod_info.json")
                    } ?: return@mapNotNull null

                val json = JsonValue.readHjson(modInfo.readText()).toString()
                    .also { println(it.toString()) }

                Mod(
                    modInfo = Klaxon().parse(json)!!
                )
            }
            .toList()
    }
}