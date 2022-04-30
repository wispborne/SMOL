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

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.update4j.Configuration
import org.update4j.FileMetadata
import smol.utilities.runCommandInTerminal
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.relativeTo
import kotlin.streams.asSequence

class SmolUpdater : BaseAppUpdater() {

    override val configXmlBaseFileNameWithoutExtension: String = "update-config"
    override val versionPropertyKey: String = "smol-version-prop"
    override val updateZipFile: Path = Main.smolUpdateZipFile

    override fun createConfiguration(
        directoryOfFilesToAddToManifest: Path,
        remoteConfigUrl: String
    ): Configuration {
        val dir = directoryOfFilesToAddToManifest

        return Configuration.builder()
            .baseUri(remoteConfigUrl)
            .basePath(Path.of("").absolutePathString())
            //            .property(
            //                Updater.PROP_VERSION_NAME,
            //                kotlin.runCatching {
            //                    // If not running from a Compose app, but running standalone, we won't have the Constant,
            //                    // so just use a hardcoded path because life is short.
            //                    (Constants.VERSION_PROPERTIES_FILE
            //                        ?: Path.of("../App/resources/common/version.properties")).let {
            //                        val props = Properties()
            //                        props.load(it.inputStream())
            //                        props["smol-version"]?.toString()!!
            //                    }
            //                }
            //                    .onFailure {
            //                        Timber.w(it)
            //                        System.err.println(it)
            //                    }
            //                    .getOrElse { "" })
            .files(
                // JCEF isn't copied to the dist folder when building, if I need to update it later,
                // I can add a build step to do that.
                //                    .plus(FileMetadata.streamDirectory(dir.resolve("libs")).asSequence())
                FileMetadata.streamDirectory(dir.resolve("app")).asSequence()
                    .plus(FileMetadata.streamDirectory(dir.resolve("runtime")).asSequence())
                    .plus(FileMetadata.readFrom(dir.resolve("SMOL.exe")))
                    .plus(FileMetadata.readFrom(dir.resolve("SMOL.ico")))
                    .onEach { it.path(it.source.relativeTo(dir)) }
                    .onEach { r -> r.classpath(r.source.toString().endsWith(".jar")) }
                    .toList())
            .build()
    }

    override fun installUpdateInternal() {
        val updateInstallerFilename = "UpdateInstaller-fat.jar"
        val standaloneJrePath = Path.of("jre-min-win")

        val command = standaloneJrePath.resolve("bin/java.exe").absolutePathString()

        GlobalScope.launch {
            runCommandInTerminal(
                workingDirectory = File("."),
                args = listOf(command, "-jar", updateInstallerFilename, "'${updateZipFile.absolutePathString()}'"),
                launchInNewWindow = true,
                newWindowTitle = "Installing SMOL update..."
            )
        }
        runBlocking { delay(300) }
    }
}