import com.github.salomonbrys.kotson.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.adapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import model.ModInfo
import org.hjson.JsonValue
import util.*

var SL = ServiceLocator()
private val basicMoshi = Moshi.Builder().build()
private val basicGson = GsonBuilder().create()

@OptIn(ExperimentalStdlibApi::class)
class ServiceLocator(
    val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .add(ModInfoAdapter())
        .build(),
    val gson: Gson = buildGson(),
    val appConfig: AppConfig = AppConfig(moshi = moshi),
    val modInfoLoader: ModInfoLoader = ModInfoLoader(moshi = moshi, gson = gson),
    val gamePath: GamePath = GamePath(appConfig = appConfig, moshi = moshi),
    val gameEnabledMods: GameEnabledMods = GameEnabledMods(gson, gamePath),
    val archives: Archives = Archives(
        config = appConfig,
        gamePath = gamePath,
        moshi = moshi,
        modInfoLoader = modInfoLoader,
        gson = gson
    ),
    val modLoader: ModLoader = ModLoader(
        gamePath = gamePath,
        archives = archives,
        modInfoLoader = modInfoLoader,
        config = appConfig,
        gameEnabledMods = gameEnabledMods
    ),
    val staging: Staging = Staging(
        config = appConfig,
        gamePath = gamePath,
        modLoader = modLoader,
        gameEnabledMods = gameEnabledMods
    ),
) {
}

private fun buildGson() = GsonBuilder()
    .setPrettyPrinting()
    .setLenient()
    .serializeNulls()
    .registerTypeAdapter<ModInfo> {
        serialize { (src, _, _) ->
            when (src) {
                is ModInfo.v091 -> basicGson.toJson(src, ModInfo.v091::class.java).toJson()
                is ModInfo.v095 -> basicGson.toJson(src, ModInfo.v095::class.java).toJson()
            }
        }
        deserialize { arg: DeserializerArg ->
            val json = if (arg.json.isJsonObject)
                arg.json
            else JsonParser.parseString(arg.json.asString)

            // Check for 0.95 format
            if (json["version"].isJsonObject) {
                basicGson.fromJson<ModInfo.v095>(json)
            } else {
                basicGson.fromJson<ModInfo.v091>(json)
            }
        }
    }
    .create()

@ExperimentalStdlibApi
private class ModInfoAdapter {

    //        .add(
//            PolymorphicJsonAdapterFactory.of(ModInfo::class.java, "ModInfo")
//                .withSubtype(ModInfo.v091::class.java, "ModInfo.v091")
//                .withSubtype(ModInfo.v095::class.java, "ModInfo.v095")
//        )
//        .add(JsonAdapter<WindowState>())
    @ToJson
    fun toJson(obj: ModInfo): String {
        return when (obj) {
            is ModInfo.v091 -> basicMoshi.adapter<ModInfo.v091>().toJson(obj)
            is ModInfo.v095 -> basicMoshi.adapter<ModInfo.v095>().toJson(obj)
        }
    }

    @FromJson
    fun fromJson(str: String): ModInfo? {
        val json = JsonValue.readHjson(str)
        return basicMoshi.modInfoJsonAdapter(json).fromJson(json.toString())
    }
}

@OptIn(ExperimentalStdlibApi::class)
private fun Moshi.modInfoJsonAdapter(json: JsonValue) =
    // Check for 0.95 format
    if (json.asObject().get("version").isObject) {
        this.adapter<ModInfo.v095>()
    } else {
        this.adapter<ModInfo.v091>()
    }