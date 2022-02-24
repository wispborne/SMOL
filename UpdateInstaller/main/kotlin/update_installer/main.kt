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

package update_installer

import org.update4j.Archive
import java.io.File
import kotlin.concurrent.thread

class Main {
    companion object {
        /**
         * First arg must be the location of update.zip.
         */
        @JvmStatic
        fun main(args: Array<String>) {
            val updateZipUri = args.getOrNull(0)?.removeSurrounding("\'")?.ifBlank { null }
                ?: kotlin.run {
                    System.err.println("First argument must be the relative path to the update zip.")
                    pause()
                    return
                }
            val updateZipPath = File(updateZipUri)

            if (!updateZipPath.exists()) {
                System.err.println("Unable to find ${updateZipPath.absolutePath}.")
                pause()
                return
            }

            println("Found update zip at ${updateZipPath.absolutePath}.")


            var isThreadDone = false

            thread {
                try {
                    println("Waiting 5 seconds for SMOL to quit and release file locks.")
                    Thread.sleep(5000)

                    println("Installing ${updateZipPath.absolutePath}...")
                    kotlin.runCatching {
                        Archive.read(updateZipPath.absolutePath).install()
                    }
                        .onFailure {
                            System.err.println("Error installing $updateZipPath.")
                            System.err.println(it.message)
                            it.printStackTrace()
                        }
                        .onSuccess {
                            println("Done. SMOL does not automatically relaunch.")
                        }
                } finally {
                    isThreadDone = true
                }
            }

            while (!isThreadDone) {
                Thread.sleep(500)
            }

            val pathOfAppToStartAfterUpdating = args.getOrNull(1)?.removeSurrounding("\'")?.ifBlank { null }

            if (pathOfAppToStartAfterUpdating != null) {
                println("Launching '$pathOfAppToStartAfterUpdating'...")

                Runtime.getRuntime().exec("cmd /C \"$pathOfAppToStartAfterUpdating\"")
            }

            pause()
        }

        fun pause() {
            println("Press enter to continue...")
            readln()
        }
    }
}