package smol_app.updater

import org.update4j.Configuration
import org.update4j.FileMetadata
import smol_access.Constants
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.writer
import kotlin.streams.asSequence

fun main() {
    CreateUpdateConfig().writeLocalUpdateConfig()
}

class CreateUpdateConfig {

    fun writeLocalUpdateConfig(): Configuration? {
        val localPath = Path.of("dist\\main\\app\\SMOL")
        val config = Configuration.builder()
            .baseUri(Constants.UPDATE_URL_UNSTABLE)
            .basePath(localPath.toAbsolutePath().toString())
            .files(
                FileMetadata.streamDirectory(localPath)
                    .asSequence()
                    .onEach { r -> r.classpath(r.source.toString().endsWith(".jar")) }
                    .toList())
            .build()


        val updateConfigPath = Path.of("update-config.xml")
        updateConfigPath.writer().use {
            config.write(it)
            println("Wrote config to ${updateConfigPath.absolutePathString()}")
        }
        return config
    }

}