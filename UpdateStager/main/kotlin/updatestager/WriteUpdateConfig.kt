package updatestager

import org.update4j.Configuration
import org.update4j.FileMetadata
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.relativeTo
import kotlin.io.path.writer
import kotlin.streams.asSequence


object WriteLocalUpdateConfig {
    fun run(onlineUrl: String, directoryOfFilesToAddToManifest: Path): Configuration? {
        val dir = directoryOfFilesToAddToManifest

        val config = Configuration.builder()
            .baseUri(onlineUrl)
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
                // JRE for the updater isn't copied to the dist folder until after building, if I need to update it later,
                // I can add a build step to do that.
//                    .plus(FileMetadata.streamDirectory(dir.resolve("jre-min-win")).asSequence())
                // JCEF isn't copied to the dist folder when building, if I need to update it later,
                // I can add a build step to do that.
//                    .plus(FileMetadata.streamDirectory(dir.resolve("libs")).asSequence())
//                    .plus(FileMetadata.readFrom(dir.resolve("update-config.xml")))
                FileMetadata.streamDirectory(dir.resolve("app")).asSequence()
                    .plus(FileMetadata.streamDirectory(dir.resolve("runtime")).asSequence())
                    .plus(FileMetadata.readFrom(dir.resolve("SMOL.exe")))
                    .plus(FileMetadata.readFrom(dir.resolve("SMOL.ico")))
                    .plus(FileMetadata.readFrom(dir.resolve("UpdateInstaller-fat.jar")))
                    .onEach { it.path(it.source.relativeTo(dir)) }
                    .onEach { r -> r.classpath(r.source.toString().endsWith(".jar")) }
                    .toList())
            .build()


        println("Creating config based on files in ${dir.absolutePathString()}.")
        dir.resolve(Updater.UPDATE_CONFIG_XML).run {
            this.writer().use {
                config.write(it)
                println("Wrote config to ${this.absolutePathString()}")
            }
        }
        return config
    }
}