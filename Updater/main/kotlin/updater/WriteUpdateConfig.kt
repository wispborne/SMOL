package updater

import org.update4j.Configuration
import org.update4j.FileMetadata
import smol_access.Constants
import timber.ktx.Timber
import java.nio.file.Path
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.inputStream
import kotlin.io.path.pathString
import kotlin.io.path.writer
import kotlin.streams.asSequence


object WriteLocalUpdateConfig {
    fun run(onlineUrl: String, directoryOfFilesToAddToManifest: Path): Configuration? {
        val excludes = listOf(".git", ".log")

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
                FileMetadata.streamDirectory(directoryOfFilesToAddToManifest)
                    .filter { file -> excludes.none { exclude -> file.source.pathString.contains(exclude) } }
                    .asSequence()
                    .onEach { r -> r.classpath(r.source.toString().endsWith(".jar")) }
                    .toList())
            .build()


        println("Creating config based on files in ${directoryOfFilesToAddToManifest.absolutePathString()}.")
        directoryOfFilesToAddToManifest.resolve(Updater.UPDATE_CONFIG_XML).run {
            this.writer().use {
                config.write(it)
                println("Wrote config to ${this.absolutePathString()}")
            }
        }
        return config
    }
}