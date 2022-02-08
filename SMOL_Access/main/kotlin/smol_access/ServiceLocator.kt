package smol_access

import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.features.logging.*
import smol_access.business.*
import smol_access.config.AppConfig
import smol_access.config.GamePathManager
import smol_access.config.VersionCheckerCache
import smol_access.config.VramCheckerCache
import smol_access.themes.ThemeManager
import smol_access.util.ManualReloadTrigger
import utilities.Jsanity

var SL = ServiceLocator()

//private val basicMoshi = Moshi.Builder()
//    .addLast(KotlinJsonAdapterFactory()).build()

typealias HttpClientBuilder = () -> HttpClient

@OptIn(ExperimentalStdlibApi::class)
class ServiceLocator internal constructor(
    val manualReloadTrigger: ManualReloadTrigger = ManualReloadTrigger(),
    val httpClientBuilder: HttpClientBuilder = {
        HttpClient(CIO) {
            install(Logging)
            install(HttpTimeout)
            this.followRedirects = true
        }
    },
//    val moshi: Moshi = Moshi.Builder()
//        .add(ModInfoAdapter())
//        .addLast(KotlinJsonAdapterFactory())
//        .build(),
    val jsanity: Jsanity = Jsanity(gson = GsonBuilder.buildGson()),
    internal val versionCheckerCache: VersionCheckerCache = VersionCheckerCache(gson = jsanity),
    val appConfig: AppConfig = AppConfig(gson = jsanity),
    val userManager: UserManager = UserManager(
        appConfig = appConfig
    ),
    internal val modInfoLoader: ModInfoLoader = ModInfoLoader(gson = jsanity),
    val gamePathManager: GamePathManager = GamePathManager(appConfig = appConfig),
    val saveReader: SaveReader = SaveReader(gamePathManager = gamePathManager),
    val vramChecker: VramCheckerManager = VramCheckerManager(
        gamePathManager = gamePathManager,
        vramCheckerCache = VramCheckerCache(gson = jsanity)
    ),
    internal val gameEnabledMods: GameEnabledMods = GameEnabledMods(jsanity, gamePathManager),
    val archives: Archives = Archives(
        modInfoLoader = modInfoLoader
    ),
    val jreManager: JreManager = JreManager(
        gamePathManager = gamePathManager,
        appConfig = appConfig,
        httpClientBuilder = httpClientBuilder,
        archives = archives
    ),
    internal val modLoader: ModLoader = ModLoader(
        gamePathManager = gamePathManager,
        modInfoLoader = modInfoLoader,
        gameEnabledMods = gameEnabledMods
    ),
    val dependencyFinder: DependencyFinder = DependencyFinder(modLoader = modLoader),
    val versionChecker: IVersionChecker = VersionChecker(
        gson = jsanity,
        versionCheckerCache = versionCheckerCache,
        userManager = userManager,
        modLoader = modLoader,
        httpClientBuilder = httpClientBuilder
    ),
    internal val staging: Staging = Staging(
        gamePathManager = gamePathManager,
        modLoader = modLoader,
        gameEnabledMods = gameEnabledMods,
        manualReloadTrigger = manualReloadTrigger
    ),
    val access: Access = Access(
        staging = staging,
        modLoader = modLoader,
        archives = archives,
        appConfig = appConfig,
        gamePathManager = gamePathManager
    ),
    val userModProfileManager: UserModProfileManager = UserModProfileManager(
        userManager = userManager, access = access, modLoader = modLoader
    ),
    val themeManager: ThemeManager = ThemeManager(userManager = userManager, jsanity = jsanity),
    val modRepo: ModRepo = ModRepo(jsanity = jsanity, httpClientBuilder = httpClientBuilder)
)

private fun buildJackson(): ObjectMapper =
    JsonMapper.builder().defaultLeniency(true)
        .enable(
            JsonReadFeature.ALLOW_JAVA_COMMENTS, JsonReadFeature.ALLOW_SINGLE_QUOTES,
            JsonReadFeature.ALLOW_YAML_COMMENTS, JsonReadFeature.ALLOW_MISSING_VALUES,
            JsonReadFeature.ALLOW_TRAILING_COMMA, JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES,
            JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS,
            JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER,
            JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS
        )
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()
        .registerKotlinModule()

//@ExperimentalStdlibApi
//class ModInfoAdapter {
//    @ToJson
//    fun toJson(obj: ModInfo): String {
//        return when (obj) {
//            is ModInfo.v091 -> basicMoshi.adapter<ModInfo.v091>().toJson(obj)
//            is ModInfo.v095 -> basicMoshi.adapter<ModInfo.v095>().toJson(obj)
//        }
//    }
//
//    @FromJson
//    fun fromJson(jsonAsMap: Map<String, String>): ModInfo {
//        val json = JsonValue.readHjson(basicMoshi.adapter<Map<String, String>>().toJson(jsonAsMap))
//        return basicMoshi.modInfoJsonAdapter(json).fromJson(json.toString())!!
//    }
//}

//class ModInfoJsonAdapter2 : JsonAdapter<ModInfo>() {
//    override fun fromJson(reader: JsonReader): ModInfo? {
//        return JsonValue.readHjson(reader.nextSource())
//    }
//
//    @OptIn(ExperimentalStdlibApi::class)
//    override fun toJson(writer: JsonWriter, value: ModInfo?) {
//        return when (value) {
//            is ModInfo.v091 -> SMOL_Access.basicMoshi.adapter<ModInfo.v091>().toJson(writer, value)
//            is ModInfo.v095 -> SMOL_Access.basicMoshi.adapter<ModInfo.v095>().toJson(writer, value)
//            null -> throw NullPointerException()
//        }
//    }
//}

//@OptIn(ExperimentalStdlibApi::class)
//fun Moshi.modInfoJsonAdapter(json: JsonValue) =
//    // Check for 0.95 format
//    if (json.asObject().get("version").isObject) {
//        this.adapter<ModInfo.v095>()
//    } else {
//        this.adapter<ModInfo.v091>()
//    }