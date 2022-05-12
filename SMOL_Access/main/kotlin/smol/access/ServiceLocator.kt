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

package smol.access

import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.features.logging.*
import smol.access.business.*
import smol.access.config.*
import smol.access.themes.ThemeManager
import smol.access.util.ManualReloadTrigger
import smol.utilities.Jsanity

lateinit var SL: ServiceLocator

//private val basicMoshi = Moshi.Builder()
//    .addLast(KotlinJsonAdapterFactory()).build()

typealias HttpClientBuilder = () -> HttpClient

@OptIn(ExperimentalStdlibApi::class)
class ServiceLocator internal constructor(
) {
    companion object {
        fun init() {
            SL = ServiceLocator()
        }
    }

    val manualReloadTrigger: ManualReloadTrigger by lazy { ManualReloadTrigger() }
    val httpClientBuilder: HttpClientBuilder by lazy {
        {
            HttpClient(CIO) {
                install(Logging)
                install(HttpTimeout)
                this.followRedirects = true
            }
        }
    }
    private val modsCache: ModsCache by lazy { ModsCache() }
    val jsanity: Jsanity by lazy { Jsanity(gson = GsonBuilder.buildGson()) }
    private val versionCheckerCache: VersionCheckerCache by lazy { VersionCheckerCache(gson = jsanity) }
    private val modMetadataStore: ModMetadataStore by lazy { ModMetadataStore(gson = jsanity) }
    val modMetadata: ModMetadataManager by lazy { ModMetadataManager(modMetadataStore = modMetadataStore) }
    val appConfig: AppConfig by lazy { AppConfig(gson = jsanity) }
    val userManager: UserManager by lazy {
        UserManager(
            appConfig = appConfig
        )
    }
    private val modInfoLoader: ModInfoLoader by lazy { ModInfoLoader(gson = jsanity) }
    val gamePathManager: GamePathManager by lazy { GamePathManager(appConfig = appConfig) }
    val saveReader: SaveReader by lazy { SaveReader(gamePathManager = gamePathManager) }
    val vramChecker: VramCheckerManager by lazy {
        VramCheckerManager(
            gamePathManager = gamePathManager,
            vramCheckerCache = VramCheckerCache(gson = jsanity)
        )
    }
    private val gameEnabledMods: GameEnabledMods by lazy { GameEnabledMods(jsanity, gamePathManager) }
    private val archives: Archives by lazy {
        Archives(
            modInfoLoader = modInfoLoader,
            jsanity = jsanity
        )
    }
    val jreManager: JreManager by lazy {
        JreManager(
            gamePathManager = gamePathManager,
            appConfig = appConfig,
            httpClientBuilder = httpClientBuilder,
            archives = archives
        )
    }
    private val staging: Staging by lazy {
        Staging(
            gamePathManager = gamePathManager,
            modsCache = modsCache,
            gameEnabledMods = gameEnabledMods,
            manualReloadTrigger = manualReloadTrigger
        )
    }
    private val modLoader: ModLoader by lazy {
        ModLoader(
            gamePathManager = gamePathManager,
            modInfoLoader = modInfoLoader,
            gameEnabledMods = gameEnabledMods,
            modsCache = modsCache,
            staging = staging
        )
    }
    val dependencyFinder: DependencyFinder by lazy { DependencyFinder(modsCache = modsCache) }
    val versionChecker: IVersionChecker by lazy {
        VersionChecker(
            gson = jsanity,
            versionCheckerCache = versionCheckerCache,
            userManager = userManager,
            modsCache = modsCache,
            httpClientBuilder = httpClientBuilder
        )
    }
    val access: Access by lazy {
        Access(
            staging = staging,
            modLoader = modLoader,
            archives = archives,
            appConfig = appConfig,
            gamePathManager = gamePathManager,
            modsCache = modsCache
        )
    }
    val userModProfileManager: UserModProfileManager by lazy {
        UserModProfileManager(
            userManager = userManager, access = access, modsCache = modsCache
        )
    }
    val themeManager: ThemeManager by lazy { ThemeManager(userManager = userManager, jsanity = jsanity) }

    //    val moshanity: Moshanity = Moshanity(),
    val modRepo: ModRepo by lazy { ModRepo(jsanity = jsanity, httpClientBuilder = httpClientBuilder) }
}

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
//    fun toJson(obj: smol.ModInfo): String {
//        return when (obj) {
//            is smol.ModInfo.v091 -> basicMoshi.adapter<smol.ModInfo.v091>().toJson(obj)
//            is smol.ModInfo.v095 -> basicMoshi.adapter<smol.ModInfo.v095>().toJson(obj)
//        }
//    }
//
//    @FromJson
//    fun fromJson(jsonAsMap: Map<String, String>): smol.ModInfo {
//        val json = JsonValue.readHjson(basicMoshi.adapter<Map<String, String>>().toJson(jsonAsMap))
//        return basicMoshi.modInfoJsonAdapter(json).fromJson(json.toString())!!
//    }
//}

//class ModInfoJsonAdapter2 : JsonAdapter<smol.ModInfo>() {
//    override fun fromJson(reader: JsonReader): smol.ModInfo? {
//        return JsonValue.readHjson(reader.nextSource())
//    }
//
//    @OptIn(ExperimentalStdlibApi::class)
//    override fun toJson(writer: JsonWriter, value: smol.ModInfo?) {
//        return when (value) {
//            is smol.ModInfo.v091 -> SMOL_Access.basicMoshi.adapter<smol.ModInfo.v091>().toJson(writer, value)
//            is smol.ModInfo.v095 -> SMOL_Access.basicMoshi.adapter<smol.ModInfo.v095>().toJson(writer, value)
//            null -> throw NullPointerException()
//        }
//    }
//}

//@OptIn(ExperimentalStdlibApi::class)
//fun Moshi.modInfoJsonAdapter(json: JsonValue) =
//    // Check for 0.95 format
//    if (json.asObject().get("version").isObject) {
//        this.adapter<smol.ModInfo.v095>()
//    } else {
//        this.adapter<smol.ModInfo.v091>()
//    }