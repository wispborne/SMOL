package util

import model.Mod
import model.ModInfo
import SL
import com.squareup.moshi.adapter
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import org.hjson.JsonValue
import org.jetbrains.skija.impl.Platform
import org.tinylog.Logger
import java.io.File


class GamePath {
    fun isValidGamePath(path: String): Boolean {
        val file = File(path)

        if (!file.exists()) return false

        var hasGameExe = false
        var hasGameCoreExe = false

        file.walkTopDown().maxDepth(1)
            .forEach {
                if (it.nameWithoutExtension == "starsector") hasGameExe = true
                if (it.nameWithoutExtension == "starsector-core") hasGameCoreExe = true
            }

        return hasGameExe && hasGameCoreExe
    }

    fun getStarsectorPath(): File? {
        return kotlin.runCatching {
            when (Platform.CURRENT) {
                Platform.WINDOWS ->
                    Advapi32Util.registryGetStringValue(
                        WinReg.HKEY_CURRENT_USER,
                        "SOFTWARE\\Fractal Softworks\\Starsector",
                        ""
                    )
                Platform.MACOS_X64,
                Platform.MACOS_ARM64 -> "" // TODO
                Platform.LINUX -> "" // TODO
                else -> "" // TODO
            }
        }
            .mapCatching { File(it) }
            .onFailure {
                Logger.debug { it.message ?: "" }
                it.printStackTrace()
            }
            .onSuccess { Logger.debug { "Product Name: ${it.absolutePath}" } }
            .getOrNull()
            .also {
                if (SL.appConfig.gamePath == null) {
                    SL.appConfig.gamePath = it?.absolutePath
                }

                Logger.debug { "Game path: ${SL.appConfig.gamePath}" }
            }
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
                Logger.trace { "Folder: ${modFolder.name}" }
                val modInfo = modFolder
                    .walkTopDown().maxDepth(1)
                    .firstOrNull {
                        Logger.trace { "  File: ${it.name}" }
                        it.name.equals("mod_info.json")
                    } ?: return@mapNotNull null

                val json = JsonValue.readHjson(modInfo.readText())
                val jsonStr = json.toString()
                    .also { Logger.trace { it.toString() } }

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