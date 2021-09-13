import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import model.Version
import org.tinylog.configuration.Configuration
import util.AppConfig
import util.GamePath
import util.Installer

var SL = ServiceLocator()

@OptIn(ExperimentalStdlibApi::class)
class ServiceLocator(
    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
//        .add(object {
//            @FromJson
//            fun fromJson(str: String): Installer.ManifestItemKey? {
//                val strSplit = str.split('-')
//                return Installer.ManifestItemKey(strSplit[0], Version.parse(strSplit[1]))
//            }
//
//            @ToJson
//            fun toJson(key: Installer.ManifestItemKey?) {
//                return "${key.copy("")}"
//            }
//        })
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