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

package utilities

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.ktx.Timber
import java.io.File

suspend fun runCommandInTerminal(
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
    withContext(Dispatchers.IO) {
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