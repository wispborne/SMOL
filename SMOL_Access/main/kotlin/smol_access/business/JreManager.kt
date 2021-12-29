package smol_access.business

import smol_access.config.GamePath
import utilities.IOLock
import utilities.IOLocks
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class JreManager(private val gamePath: GamePath) {
    private val versionRegex = Regex("""(\d+\.\d+\.\d+[\d_\-.+\w]*)""")

    fun findJREs(): List<JreEntry> {
        IOLock.read(IOLocks.gameMainFolderLock) {
            return gamePath.get()?.listDirectoryEntries()
                ?.mapNotNull { path ->
                    val javaExe = path.resolve("bin/java.exe")
                    if (!javaExe.exists()) return@mapNotNull null

                    val versionString = kotlin.runCatching {
                        ProcessBuilder()
                            .command(javaExe.absolutePathString(), "-version")
                            .directory(path.toFile().resolve("bin"))
                            .redirectErrorStream(true)
                            .start()
                            .inputStream
                            .bufferedReader()
                            .readLines()
                            .let { lines ->
                                lines.firstNotNullOfOrNull { versionRegex.find(it) }?.value
                                    ?: lines.firstOrNull()
                            }
                    }
                        .onFailure { timber.ktx.Timber.e(it) { "Error getting java version from '$path'." } }
                        .getOrNull() ?: return@mapNotNull null

                    return@mapNotNull JreEntry(versionString = versionString, path = path)
                }
                ?.toList() ?: emptyList()
        }
    }

    fun changeJre(jreEntry: JreEntry) {
        IOLock.write(IOLocks.gameMainFolderLock) {
            kotlin.runCatching {
                val currentJrePath = findJREs().firstOrNull { it.isUsedByGame }

                if (currentJrePath != null && currentJrePath.path.exists()) {

                }
            }
        }
    }
}

data class JreEntry(
    val versionString: String,
    val path: Path
) {
    val isUsedByGame = path.name == "jre"
}