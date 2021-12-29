package smol_access.business

import kotlinx.coroutines.runBlocking
import smol_access.config.GamePath
import timber.ktx.Timber
import utilities.IOLock
import utilities.IOLocks
import utilities.awaitWrite
import utilities.moveDirectory
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.io.path.*

class JreManager(private val gamePath: GamePath) {
    companion object {
        const val gameJreFolderName = "jre"
        private val versionRegex = Regex("""(\d+\.\d+\.\d+[\d_\-.+\w]*)""")
    }

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

    fun changeJre(newJre: JreEntry) {
        IOLock.write(IOLocks.gameMainFolderLock) {
            runBlocking {
                kotlin.runCatching {
                    val gamePath = gamePath.get()!!
                    val currentJreSource = findJREs().firstOrNull { it.isUsedByGame }
                    var currentJreDest: Path? = null
                    val gameJrePath = kotlin.runCatching { gamePath.resolve(gameJreFolderName).awaitWrite() }
                        .onFailure { Timber.w(it) }
                        .getOrNull() ?: return@runBlocking

                    // Move current JRE to a new folder.
                    kotlin.runCatching {
                        if (currentJreSource != null && currentJreSource.path.exists()) {
                            currentJreDest =
                                Path.of(gameJrePath.absolutePathString() + "-${currentJreSource.versionString}")

                            if (currentJreDest!!.exists()) {
                                currentJreDest =
                                    Path.of(
                                        currentJreDest!!.absolutePathString() + UUID.randomUUID().toString().take(6)
                                    )
                            }

                            Timber.i { "Moving JRE ${currentJreSource.versionString} from '${currentJreSource.path}' to '$currentJreDest'." }
                            currentJreSource.path.awaitWrite().moveDirectory(currentJreDest!!)
                        }
                    }
                        .onFailure {
                            Timber.w(it) { "Unable to move currently used JRE. Make sure the game is not running." }
                            return@onFailure
                        }

                    // Rename target JRE to "jre".
                    kotlin.runCatching {
//                        gameJrePath.awaitWrite(5000)
                        newJre.path.awaitWrite()
                        Timber.i { "Moving JRE ${newJre.versionString} from '${newJre.path}' to '$gameJrePath'." }
                        newJre.path.awaitWrite().moveDirectory(gameJrePath)
                    }
                        .onFailure {
                            Timber.w(it) { "Unable to move new JRE $newJre to '$gameJrePath'." }
                            // If we failed to move the new JRE into place but we did rename the one used by the game,
                            // move the old one back into place so we don't have no "jre" folder at all.
                            if (!gameJrePath.exists() && currentJreDest != null && currentJreDest!!.exists()) {
                                Timber.w(it) { "Rolling back JRE change. Moving '$currentJreDest' to '$gameJrePath'." }
                                currentJreDest!!.moveDirectory(gameJrePath)
                            }
                        }
                }
            }
        }
    }
}

data class JreEntry(
    val versionString: String,
    val path: Path
) {
    val isUsedByGame = path.name == JreManager.gameJreFolderName
}