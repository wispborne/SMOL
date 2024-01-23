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

import com.github.salomonbrys.kotson.*
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.ktor.http.*
import smol.access.model.Dependency
import smol.access.model.ModInfo
import smol.access.model.Tip
import smol.access.model.Version
import smol.timber.ktx.Timber
import smol.utilities.getNullable
import java.time.ZonedDateTime

private val basicGson = GsonBuilder().create()

object GsonBuilder {
    fun buildGson() = GsonBuilder()
        .setPrettyPrinting()
        .setLenient()
        .serializeNulls()
        .registerTypeAdapter {
            deserialize { arg: DeserializerArg ->
                val json = if (arg.json.isJsonObject)
                    arg.json
                else JsonParser().parse(arg.json.asString)
                Timber.d { "Parsing " + json["id"].string + " mod_info.json" }

                ModInfo(
                    id = json["id"].string,
                    name = json.getNullable("name")?.nullString,
                    author = json.getNullable("author")?.nullString,
                    description = json.getNullable("description")?.nullString,
                    requiredMemoryMB = json.getNullable("requiredMemoryMB")?.nullString,
                    gameVersion = json.getNullable("gameVersion")?.nullString,
                    isUtilityMod = runCatching { json["utility"].bool }
                        .onFailure { Timber.d { it.message ?: "" } }
                        .getOrElse { false },
                    isTotalConversion = runCatching { json["totalConversion"].bool }
                        .onFailure { Timber.d { it.message ?: "" } }
                        .getOrElse { false },
                    jars = runCatching { json.getNullable("jars")!!.array.map { it.string } }
                        .onFailure { Timber.d(it) }
                        .getOrElse { emptyList() },
                    modPlugin = json.getNullable("modPlugin")?.nullString ?: "",
                    version = parseVersion(json) ?: Version.parse("0.0.0"),
                    dependencies = runCatching {
                        val deps = json.getNullable("dependencies")?.asJsonArray!!
                        deps.mapNotNull { dep ->
                            runCatching {
                                Dependency(
                                    id = dep["id"].nullString,
                                    name = dep.getNullable("name")?.nullString,
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
        .registerTypeAdapter<Url> {
            deserialize { arg ->
                runCatching { Url(arg.json.string) }
                    .onFailure { Timber.w(it) { arg.json.toString() } }
                    .getOrNull()
            }
            serialize { it.src.toString().toJson() }
        }
        .registerTypeAdapter<ZonedDateTime> {
            deserialize { arg ->
                runCatching { ZonedDateTime.parse(arg.json.string) }
                    .onFailure { Timber.w(it) { arg.json.toString() } }
                    .getOrNull()
            }
            serialize { it.src.toString().toJson() }
        }
        .registerTypeAdapter<Tip> {
            deserialize { arg ->
                val json = arg.json
                // Tips can either be "{ freq: "1", tip: "something" }" or just "something"
                val isShortFormat = runCatching { json["tip"] }.isFailure
                if (isShortFormat) Tip(
                    "1",
                    json.asString
                ) else Tip(json["freq"].asString, json["tip"].asString)
            }
        }
        .create()

    private fun parseVersion(json: JsonElement): Version? {
        return runCatching { json["version"].string.let { Version.parse(it) } }
            .recoverCatching {
                val versionObj = json["version"].asJsonObject
                val major by versionObj.byString("major") { "0" }
                val minor by versionObj.byString("minor") { "0" }
                val patch by versionObj.byString("patch") { "0" }
                val build by versionObj.byNullableString("build")
                Version(
                    raw = listOfNotNull(major, minor, patch, build).joinToString(separator = "."),
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