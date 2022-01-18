package smol_app.updater
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

fun main() {
    WriteLocalUpdateConfig.run(
        onlineUrl = Constants.UPDATE_URL_UNSTABLE,
        localPath = Path.of("App\\dist\\main\\app\\SMOL")
    )
}

object WriteLocalUpdateConfig {
    const val PROP_VERSION = "smol-version"

    fun run(onlineUrl: String, localPath: Path): Configuration? {
        val excludes = listOf(".git", ".log")

        val config = Configuration.builder()
            .baseUri(onlineUrl)
            .basePath(Path.of("").absolutePathString())
            .property(PROP_VERSION,
                kotlin.runCatching {
                    Path.of("App", "version.properties").let {
                        val props = Properties()
                        props.load(it.inputStream())
                        props["smol-version"]?.toString()!!
                    }
                }
                    .onFailure { Timber.w(it) }
                    .getOrElse { "" })
            .files(
                FileMetadata.streamDirectory(localPath)
                    .filter { file -> excludes.none { exclude -> file.source.pathString.contains(exclude) } }
                    .asSequence()
                    .onEach { r -> r.classpath(r.source.toString().endsWith(".jar")) }
                    .toList())
            .build()


        localPath.resolve(Updater.UPDATE_CONFIG_XML).run {
            this.writer().use {
                config.write(it)
                println("Wrote config to ${this.absolutePathString()}")
            }
        }
        return config
    }
}