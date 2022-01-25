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
                            println("Done.")
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