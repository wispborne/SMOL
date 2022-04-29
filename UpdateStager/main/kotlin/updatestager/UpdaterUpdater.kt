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

package updatestager

import org.update4j.Archive
import org.update4j.Configuration
import org.update4j.FileMetadata
import timber.ktx.Timber
import update_installer.BaseAppUpdater
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteIfExists
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence

class UpdaterUpdater : BaseAppUpdater() {
    override val configXmlBaseFileNameWithoutExtension: String = "updater-config"
    override val versionPropertyKey: String = "updater-version-prop"
    override val updateZipFile: Path = Path.of("updater-update.zip")
    val standaloneJreFolderName = "jre-min-win"

    override fun createConfiguration(
        directoryOfFilesToAddToManifest: Path,
        remoteConfigUrl: String
    ): Configuration {
        val dir = directoryOfFilesToAddToManifest
        val standaloneJreFolder = dir.resolve(standaloneJreFolderName)

        return Configuration.builder()
            .baseUri(remoteConfigUrl)
            .basePath(Path.of("").absolutePathString())
            .files(
                FileMetadata.streamDirectory(standaloneJreFolder).asSequence()
                    .plus(FileMetadata.readFrom(dir.resolve("UpdateInstaller-fat.jar")))
                    .onEach { it.path(it.source.relativeTo(dir)) }
                    .onEach { r -> r.classpath(r.source.toString().endsWith(".jar")) }
                    .toList())
            .build()
    }

    override fun installUpdateInternal() {
        val archive = kotlin.runCatching {
            Archive.read(updateZipFile)
        }
            .onFailure { t ->
                Timber.e(t) { "Reading $updateZipFile failed, deleting it to set up for a redownload." }
                kotlin.runCatching {
                    updateZipFile.deleteIfExists()
                }
                    .onFailure { Timber.e(it) { "Error deleting $updateZipFile." } }
            }
            .getOrThrow()

        kotlin.runCatching {
            // For some reason, the installer fails if this parent folder exists.
            Path.of(standaloneJreFolderName).deleteIfExists()
            archive.install(true)

        }
            .onFailure { Timber.e(it) }
            .getOrThrow()
    }
}