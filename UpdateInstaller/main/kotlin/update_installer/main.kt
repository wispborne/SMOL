package update_installer

import kotlinx.coroutines.*
import org.update4j.Archive
import java.io.File

class Main {
    companion object {
        /**
         * First arg must be the location of update.zip.
         */
        @JvmStatic
        fun main(args: Array<String>) {
            val updateZipUri = args.getOrNull(0)
                ?: kotlin.run {
                    System.err.println("First argument must be the absolute path to update.zip.")
                    return
                }
            val updateZipPath = File(updateZipUri)

            if (!updateZipPath.exists()) {
                System.err.println("Unable to find ${updateZipPath.absolutePath}.")
                return
            }

            println("Found update zip at ${updateZipPath.absolutePath}.")

            CoroutineScope(Job()).launch {
                withContext(Dispatchers.IO) {
                    println("Installing ${updateZipPath.absolutePath}...")
                    Archive.read(updateZipPath.absolutePath).install()
                    println("Done.")
                }
            }
        }
    }
}