package utilities

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.salomonbrys.kotson.typeToken
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonIOException
import com.google.gson.JsonSyntaxException
import org.hjson.JsonValue
import org.hjson.Stringify
import timber.ktx.Timber
import java.lang.reflect.Type


class Jsanity(
    private val gson: Gson,
    private val jackson: ObjectMapper
) {
    @Throws(JsonSyntaxException::class)
    fun <T> fromJson(json: String, typeOfT: Type): T {
        return fromJsonString(json, typeOfT)
    }

    @Throws(JsonSyntaxException::class)
    fun <T> fromJson(json: String, classOfT: Class<T>): T {
        return fromJson(json, classOfT as Type)
    }

    @Throws(JsonSyntaxException::class)
    fun <T> fromJson(json: JsonElement, classOfT: Class<T>?): T {
        return gson.fromJson(json, classOfT)
    }

    @Throws(JsonSyntaxException::class)
    fun <T> fromJson(json: JsonElement, typeOfT: Type): T = gson.fromJson(json, typeOfT)

//    fun <T> fromJson(json: Reader, typeToken: Type): T = gson.fromJson(json, typeToken)

//    fun <T> fromJson(json: JsonReader, typeToken: Type): T = gson.fromJson(json, typeToken)

    inline fun <reified T : Any> fromJson(json: String): T = fromJson(json, typeToken<T>())

//    inline fun <reified T : Any> fromJson(json: Reader): T = fromJson(json, typeToken<T>())

//    inline fun <reified T : Any> fromJson(json: JsonReader): T = fromJson(json, typeToken<T>())

//    inline fun <reified T : Any> fromJson(json: JsonElement): T = fromJson(json, typeToken<T>())

    private fun <T> fromJsonString(json: String, typeOfT: Type): T {
        val strippedJson = stripJsonComments(json)
        // HJson
        val hjson = JsonValue.readHjson(strippedJson).toString(Stringify.FORMATTED)

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
        return gson.fromJson(jsonStr, typeOfT)
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