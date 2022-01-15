package smol_access.config

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import utilities.IOLock
import timber.ktx.Timber
import utilities.mountOf
import utilities.toPathOrNull
import utilities.walk
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension


class GamePath internal constructor(
    private val appConfig: AppConfig
) {
    fun get() = appConfig.gamePath.toPathOrNull()

    fun getDefaultStarsectorPath(platform: Platform): File? =
        kotlin.runCatching {
            when (platform) {
                Platform.Windows ->
                    Advapi32Util.registryGetStringValue(
                        WinReg.HKEY_CURRENT_USER,
                        "SOFTWARE\\Fractal Softworks\\Starsector",
                        ""
                    )
                Platform.MacOS -> "" // TODO
                Platform.Linux -> "" // TODO
                else -> "" // TODO
            }
        }
            .mapCatching { File(it) }
            .onFailure {
                Timber.d { it.message ?: "" }
                it.printStackTrace()
            }
            .getOrNull()

    fun getModsPath(): Path {
        val starsectorPathStr = appConfig.gamePath
            ?: kotlin.run {
                val ex = NullPointerException("Game path not found. AppConfig: $appConfig")
                Timber.e(ex)
                throw ex
            }
        val starsectorPath: Path = starsectorPathStr.toPathOrNull()
            ?: kotlin.run {
                val ex = NullPointerException("Game path cannot be converted to a Path. AppConfig: $appConfig")
                Timber.e(ex)
                throw ex
            }
        val mods = starsectorPath.resolve("mods")

        IOLock.write {
            mods.createDirectories()
        }

        return mods
    }
}