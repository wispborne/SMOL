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

package smol.update_installer

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.update4j.Archive
import smol.timber.LogLevel
import smol.timber.ktx.Timber
import smol.utilities.bytesAsShortReadableMB
import smol.utilities.bytesToMB
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists

class Main {
    companion object {
        val smolUpdateZipFile: Path = Path.of("smol-update.zip")

        /**
         * First arg must be the location of update.zip.
         */
        @JvmStatic
        fun main(args: Array<String>) {
            Timber.plant(Timber.DebugTree(LogLevel.WARN))
            val updateZipUri = args.getOrNull(0)?.removeSurrounding("\'")?.ifBlank { null }
                ?: smolUpdateZipFile.toString()

            var updateZipPath = Path.of(updateZipUri)

            if (!updateZipPath.exists()) {
                Timber.i { "Unable to find ${updateZipPath.absolutePathString()}, we have to download it." }

                val updater = SmolUpdater()

                println("Which channel would you like to update using?")
                UpdateChannel.values().forEachIndexed { i, channel ->
                    println("${i + 1}) ${channel.name}")
                }
                println("Enter a number:")
                val channel = readln().toInt().let { UpdateChannel.values()[it - 1] }

                GlobalScope.launch(Dispatchers.Default) {
                    updater.currentFileDownload.collectLatest {
                        it ?: return@collectLatest
                        println("Download status: ${it.name} (${"%.2f".format(updater.totalDownloadedBytes.value.bytesToMB)}/${updater.totalDownloadBytes.value?.bytesAsShortReadableMB}).                                           ")
                        delay(500)
                    }
                }

                runBlocking(Dispatchers.IO) {
                    kotlin.runCatching {
                        updateZipPath = updater.downloadUpdateZip(updater.fetchRemoteConfig(channel))!!
                    }
                        .onFailure {
                            Timber.e(it)
                            updateZipPath.deleteIfExists()
                        }
                }

                if (!updateZipPath.exists()) {
                    Timber.e { "Unable to find ${updateZipPath.absolutePathString()}." }
                    pause()
                    return
                }
            }

            println("Found update zip at ${updateZipPath.absolutePathString()}.")
            var success = false

            try {
                println("Waiting 5 seconds for SMOL to quit and release file locks.")
                Thread.sleep(5000)
                var timesToRepeat = 3

                println("Installing ${updateZipPath.absolutePathString()}...")
                while (timesToRepeat > 0) {
                    kotlin.runCatching {
                        Archive.read(updateZipPath.absolutePathString()).install()
                        success = true
                    }
                        .onFailure {
                            timesToRepeat--
                            Timber.e { "Error installing $updateZipPath." }
                            Timber.e { it.message ?: "" }
                            it.printStackTrace()

                            if (timesToRepeat > 0) {
                                Timber.e { "Retrying $timesToRepeat more time(s)." }
                                Thread.sleep(3000)
                            }
                        }
                        .onSuccess {
                            timesToRepeat = 0
                            println("Done. Please relaunch SMOL to continue.")
                        }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Timber.e { "If updating is not working, try deleting $updateZipPath and then trying again." }
            }

            val pathOfAppToStartAfterUpdating = args.getOrNull(1)?.removeSurrounding("\'")?.ifBlank { null }

            if (pathOfAppToStartAfterUpdating != null) {
                println("Launching '$pathOfAppToStartAfterUpdating'...")

                Runtime.getRuntime().exec("cmd /C \"$pathOfAppToStartAfterUpdating\"")
            }

            if (!success) {
                Timber.e { "Failed to update." }
            }

            pause()
        }

        fun pause() {
            println("Press enter to continue...")
            readln()
        }
    }
}