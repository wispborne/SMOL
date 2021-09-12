import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.tinylog.configuration.Configuration
import util.AppConfig
import util.GamePath
import util.Installer

var SL = ServiceLocator()

class ServiceLocator(
    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build(),
    val appConfig: AppConfig = AppConfig(moshi),
    val gamePath: GamePath = GamePath(appConfig, moshi),
    val installer: Installer = Installer(appConfig, gamePath, moshi)
) {
    init {
        // Logger
        Configuration.replace(
            mapOf(
                "writer.format" to "{date} {class}.{method}:{line} {level}: {message}",
                "level" to "debug"
            )
        )
    }
}