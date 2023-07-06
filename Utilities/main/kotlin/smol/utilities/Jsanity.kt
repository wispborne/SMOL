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

package smol.utilities

import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.typeToken
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonIOException
import com.google.gson.JsonSyntaxException
import org.hjson.JsonValue
import org.hjson.Stringify
import smol.timber.ktx.Timber
import java.lang.reflect.Type


class Jsanity constructor(
    val gson: Gson
) {
    /**
     * @param filename Just used for logging.
     */
    @Throws(JsonSyntaxException::class)
    fun <T> fromJson(json: String, filename: String, typeOfT: Type, shouldStripComments: Boolean): T {
        return fromJsonString(json, filename, typeOfT, shouldStripComments)
    }

    /**
     * @param filename Just used for logging.
     */
    @Throws(JsonSyntaxException::class)
    fun <T> fromJson(json: String, filename: String, classOfT: Class<T>, shouldStripComments: Boolean): T {
        return fromJson(json, filename, classOfT as Type, shouldStripComments)
    }

    @Throws(JsonSyntaxException::class)
    fun <T> fromJson(json: JsonElement, classOfT: Class<T>?): T {
        return gson.fromJson(json, classOfT)
    }

    @Throws(JsonSyntaxException::class)
    fun <T> fromJson(json: JsonElement, typeOfT: Type): T =
        gson.fromJson(json, typeOfT)

//    fun <T> fromJson(json: Reader, typeToken: Type): T = gson.fromJson(json, typeToken)

//    fun <T> fromJson(json: JsonReader, typeToken: Type): T = gson.fromJson(json, typeToken)

    /**
     * @param filename Just used for logging.
     */
    inline fun <reified T : Any> fromJson(json: String, filename: String, shouldStripComments: Boolean): T =
        fromJson(json, filename, typeToken<T>(), shouldStripComments)

//    inline fun <reified T : Any> fromJson(json: Reader): T = fromJson(json, typeToken<T>())

//    inline fun <reified T : Any> fromJson(json: JsonReader): T = fromJson(json, typeToken<T>())

//    inline fun <reified T : Any> fromJson(json: JsonElement): T = fromJson(json, typeToken<T>())

    /**
     * @param filename Just used for logging.
     */
    private fun <T> fromJsonString(json: String, filename: String, typeOfT: Type, shouldStripComments: Boolean): T {
        val strippedJson = if (shouldStripComments) stripJsonComments(json) else json
        // HJson
        val hjson = kotlin.runCatching {
            JsonValue.readHjson(strippedJson).toString(Stringify.FORMATTED)
        }
            .onFailure {
                Timber.w {
                    "HJson error parsing of $filename: \n${
                        strippedJson
                            .lines()
                            .take(10)
                            .joinToString(separator = "\n") { it.take(100) }
                    }"
                }
                Timber.d(it) { "Full stacktrace:" }
            }
            .getOrThrow()

        // Jackson
//        val jacksonJson = kotlin.runCatching {
//            jackson.readValue<T>(hjson, jackson.typeFactory.constructType(typeOfT))
//        }
//            .recover { jackson.readTree(hjson) }
//            .recover { jackson.readValue(hjson, typeOfT.javaClass) }
//            .getOrNull()
//            ?.let { jackson.writeValueAsString(it) } ?: hjson

//        val jsonStr = hjson.toString()
//            .also { Timber.v { it } }
        val jsonStr = hjson
            .also { Timber.v { it } }

        // Gson
        return kotlin.runCatching { gson.fromJson<T>(jsonStr, typeOfT) }
            .onFailure { Timber.e(it) { "Gson failed to parse:\n$jsonStr" } }
            .getOrThrow()
    }

    fun toJson(obj: Any?): String {
        return gson.toJson(obj)
    }

    @Throws(JsonIOException::class)
    fun toJson(src: Any?, writer: Appendable?) = gson.toJson(src, writer)

    fun stripJsonComments(json: String): String {
        // Fast but chokes on
        // "#t"# comment
        return json.replace(Regex("(\"*?\")*((?<!\")#.*(?!\"))", RegexOption.MULTILINE), "")
    }
}

fun JsonElement.getNullable(key: String): JsonElement? = kotlin.runCatching { this[key] }.getOrNull()