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

import timber.ktx.Timber
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString

/**
 * Runs a command in the OS's command line.
 *
 * Warning: This blocks until the spawned process exits (or is launched, if `launchInNewWindow` is `true`).
 * Run in a coroutine to avoid this.
 */
fun runCommandInTerminal(
    args: List<String> = emptyList(),
    workingDirectory: File?,
    launchInNewWindow: Boolean = false,
    newWindowTitle: String? = null
) {
    val launcherCommand = when (currentPlatform) {
        Platform.Windows -> {
            if (launchInNewWindow) {
                listOf("cmd", "/C", "start", "\"${newWindowTitle ?: "Running..."}\"")
            } else {
                emptyList()
            }
        }
        else -> listOf("open")
    }.toTypedArray()

    val finalCommands = launcherCommand
//    withContext(Dispatchers.IO) {
    kotlin.runCatching {
        ProcessBuilder(*finalCommands, *args.toTypedArray())
            .directory(workingDirectory)
//            .apply {
//                environment().clear()
//            }
            .also {
                Timber.i {
                    "Running terminal command: '${
                        it.command().joinToString()
                    }' in working dir '${it.directory().absolutePath}' with environment '${it.environment().entries.joinToString()}'."
                }
            }
            .start()
            .apply {
                this.inputStream.transferTo(System.out)
                this.errorStream.transferTo(System.err)
            }
//            Runtime.getRuntime()
//                .also {
//                    Timber.i {
//                        "Running terminal command: '${finalCommand}' in working dir '${workingDirectory}' with environment '${envArgs.joinToString()}'."
//                    }
//                }
//                .exec(
//                    finalCommand.toTypedArray() + args.toTypedArray(),
//                    emptyArray(),
//                    workingDirectory
//                )
//                .apply {
////                if (!runAsync) {
//                    this.inputStream.transferTo(System.out)
//                    this.errorStream.transferTo(System.err)
////                }
//                }
    }
        .onFailure { Timber.e(it) }
//    }
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