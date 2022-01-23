package utilities

import java.io.File

fun openProgramInTerminal(command: String, workingDirectory: File?) {
    val commands = when (currentPlatform) {
        Platform.Windows -> arrayOf("cmd.exe", "/C")
        else -> arrayOf("open")
    }
    Runtime.getRuntime()
        .exec(
            arrayOf(*commands, command),
            null,
            workingDirectory
        )
}

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