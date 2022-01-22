package updatestager

import smol_access.Constants
import utilities.toPathOrNull
import java.nio.file.Path

class Main {
    companion object {
        /**
         * First arg must be `directoryOfFilesToAddToManifest`.
         */
        @JvmStatic
        fun main(args: Array<String>) {
            WriteLocalUpdateConfig.run(
                onlineUrl = Constants.UPDATE_URL_UNSTABLE,
                directoryOfFilesToAddToManifest = args[0].toPathOrNull()!!
            )
        }
    }
}