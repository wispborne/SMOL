package updater

import smol_access.Constants
import java.nio.file.Path

fun main() {
    WriteLocalUpdateConfig.run(
        onlineUrl = Constants.UPDATE_URL_UNSTABLE,
        localPath = Path.of("App\\dist\\main\\app\\SMOL")
    )
}
