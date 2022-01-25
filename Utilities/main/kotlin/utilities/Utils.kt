package utilities

import timber.ktx.Timber
import java.io.File

fun runCommandInTerminal(
    command: String,
    workingDirectory: File?,
    runAsync: Boolean = false,
    launchInNewWindow: Boolean = false,
    newWindowTitle: String? = null
) {
    val launcherCommand = when (currentPlatform) {
        Platform.Windows -> {
            "cmd /C ${
                if (launchInNewWindow) {
                    "start \"${newWindowTitle ?: "Running..."}\" "
                } else {
                    ""
                }
            }"
        }
        else -> "open "
    }

    val finalCommand = launcherCommand + command
    Timber.i { "Running terminal command: '$finalCommand'." }
    Runtime.getRuntime()
        .exec(
            finalCommand,
            null,
            workingDirectory
        ).apply {
            if (!runAsync) {
                this.inputStream.transferTo(System.out)
                this.errorStream.transferTo(System.err)
            }
        }
}

//Runtime.getRuntime()
//.exec(
//"cmd /C start \"Installing SMOL update...\" \"F:\\Code\\Starsector\\SMOL\\App\\jre-min-wi n\\bin\\java.exe\" -jar UpdateInstaller-fat.jar \"smol-update.zip\"",
//null,
//workingDirectory
//)

/**
 * From [https://github.com/JetBrains/skija/blob/ebd63708b35e23667c1bf65845182430d0cf0860/shared/java/impl/Platform.java].
 */
val currentPlatform: Platform
    get() {
        val os = System.getProperty("os.name").lowercase()

        return if (os.contains("mac") || os.contains("darwin")) {
            if ("aarch64" == System.getProperty("os.arch"))
                Platform.MacOS
            else Platform.MacOS
        } else if (os.contains("windows"))
            Platform.Windows
        else if (os.contains("nux") || os.contains("nix"))
            Platform.Linux
        else throw RuntimeException(
            "Unsupported platform: $os"
        )
    }