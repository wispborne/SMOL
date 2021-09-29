import config.AppConfig
import config.Platform
import org.tinylog.Logger
import toothpick.ktp.KTP
import toothpick.ktp.extension.getInstance
import util.toFileOrNull
import java.io.File

class Access {


    fun checkAndSetDefaultPaths(platform: Platform) {
        val uiConfig: AppConfig = SL.appConfig // KTP.openRootScope().getInstance()

        if (!SL.gamePath.isValidGamePath(uiConfig.gamePath ?: "")) {
            uiConfig.gamePath = SL.gamePath.getDefaultStarsectorPath(platform)?.absolutePath
        }

        if (uiConfig.archivesPath.toFileOrNull()?.exists() != true) {
            uiConfig.archivesPath = File(System.getProperty("user.home"), "SMOL/archives").absolutePath
        }

        SL.archives.getArchivesManifest()
            .also { Logger.debug { "Archives folder manifest: ${it?.manifestItems?.keys?.joinToString()}" } }

        if (uiConfig.stagingPath.toFileOrNull()?.exists() != true) {
            uiConfig.stagingPath = File(System.getProperty("user.home"), "SMOL/staging").absolutePath
        }

        Logger.debug { "Game: ${uiConfig.gamePath}" }
        Logger.debug { "Archives: ${uiConfig.archivesPath}" }
        Logger.debug { "Staging: ${uiConfig.stagingPath}" }
    }
}