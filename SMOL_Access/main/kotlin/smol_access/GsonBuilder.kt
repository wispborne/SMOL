package smol_access

import com.github.salomonbrys.kotson.*
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import smol_access.model.Dependency
import smol_access.model.ModInfo
import smol_access.model.Version
import timber.ktx.Timber
import utilities.getNullable

private val basicGson = GsonBuilder().create()

object GsonBuilder {
    fun buildGson() = GsonBuilder()
        .setPrettyPrinting()
        .setLenient()
        .serializeNulls()
        .registerTypeAdapter<ModInfo> {
            deserialize { arg: DeserializerArg ->
                val json = if (arg.json.isJsonObject)
                    arg.json
                else JsonParser().parse(arg.json.asString)


                ModInfo(
                    id = json["id"].string,
                    name = json.getNullable("name")?.string,
                    author = json.getNullable("author")?.string,
                    description = json.getNullable("description")?.string,
                    requiredMemoryMB = json.getNullable("requiredMemoryMB")?.string,
                    gameVersion = json.getNullable("gameVersion")?.string,
                    isUtilityMod = kotlin.runCatching { json["utility"].bool }
                        .onFailure { Timber.d(it) }
                        .getOrElse { false },
                    jars = kotlin.runCatching { json.getNullable("jars")!!.array.map { it.string } }
                        .onFailure { Timber.d(it) }
                        .getOrElse { emptyList() },
                    modPlugin = json.getNullable("modPlugin")?.string ?: "",
                    version = parseVersion(json) ?: Version.parse("0.0.0"),
                    dependencies = kotlin.runCatching {
                        val deps = json.getNullable("dependencies")?.asJsonArray!!
                        deps.mapNotNull { dep ->
                            kotlin.runCatching {
                                Dependency(
                                    id = dep["id"].string,
                                    name = dep.getNullable("name")?.string,
                                    version = parseVersion(dep)
                                )
                            }
                                .onFailure { Timber.d(it) }
                                .getOrNull()
                        }
                    }
                        .onFailure { Timber.d(it) }
                        .getOrElse { emptyList() }
                )
            }
        }
        .create()

    private fun parseVersion(json: JsonElement): Version? {
        return kotlin.runCatching { json["version"].string.let { Version.parse(it) } }
            .recoverCatching {
                val versionObj = json["version"].asJsonObject
                val major by versionObj.byString("major") { "0" }
                val minor by versionObj.byString("minor") { "0" }
                val patch by versionObj.byString("patch") { "0" }
                val build by versionObj.byString("build") { "0" }
                Version(
                    raw = listOf(major, minor, patch, build).joinToString(separator = "."),
                    major = major,
                    minor = minor,
                    patch = patch,
                    build = build
                )
            }
            .onFailure { Timber.d(it) }
            .getOrNull()
    }
}