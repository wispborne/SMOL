///*
// * This file is distributed under the GPLv3. An informal description follows:
// * - Anyone can copy, modify and distribute this software as long as the other points are followed.
// * - You must include the license and copyright notice with each and every distribution.
// * - You may this software for commercial purposes.
// * - If you modify it, you must indicate changes made to the code.
// * - Any modifications of this code base MUST be distributed with the same license, GPLv3.
// * - This software is provided without warranty.
// * - The software author or license can not be held liable for any damages inflicted by the software.
// * The full license is available from <https://www.gnu.org/licenses/gpl-3.0.txt>.
// */
//
//package mod_repo
//
//import com.github.salomonbrys.kotson.toJson
//import com.google.gson.JsonElement
//import com.squareup.moshi.FromJson
//import com.squareup.moshi.Moshi
//import com.squareup.moshi.ToJson
//import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
//import io.ktor.http.*
//import okio.buffer
//import okio.sink
//import timber.ktx.Timber
//import utilities.IJsanity
//import java.io.OutputStream
//import java.lang.reflect.Type
//
//
//class Moshanity(
//    private val moshi: Moshi = Moshi.Builder()
////        .add(KotlinFeature)
//        .add(UrlAdapter())
//        .add(JsonElementAdapter())
//        .addLast(KotlinJsonAdapterFactory())
//        .build()
//) : IJsanity() {
//    override fun <T> fromJson(json: String, typeOfT: Type, shouldStripComments: Boolean): T {
//        return fromJsonString(json, typeOfT, shouldStripComments)
//    }
//
//    override fun <T> fromJson(json: String, classOfT: Class<T>, shouldStripComments: Boolean): T {
//        return fromJsonString(json, classOfT, shouldStripComments)
//    }
//
//    override fun <T> fromJson(json: JsonElement, typeOfT: Type): T {
//        return fromJsonString(json.toString(), typeOfT, false)
//    }
//
//    override fun <T> fromJson(json: Any, typeOfT: Type): T {
//        return fromJsonString(json.toString(), typeOfT, false)
//    }
//
//    override fun <T> toJson(obj: T, typeOfT: Type): String {
//        return moshi.adapter<T>(typeOfT).toJson(obj)!!
//    }
//
//    override fun <T> toJson(obj: T, typeOfT: Type, writer: OutputStream) {
//        moshi.adapter<T>(typeOfT).toJson(writer.sink().buffer(), obj)
//    }
//
//    private fun <T> fromJsonString(json: String, typeOfT: Type, shouldStripComments: Boolean): T {
//        val strippedJson = if (shouldStripComments) stripJsonComments(json) else json
//
//        return kotlin.runCatching { moshi.adapter<T>(typeOfT).fromJson(strippedJson)!! }
//            .onFailure { Timber.e(it) { "Moshi failed to parse:\n$strippedJson" } }
//            .getOrThrow()
//    }
//
//    class UrlAdapter {
//        @ToJson
//        fun toJson(url: Url) = url.toString()
//
//        @FromJson
//        fun fromJson(url: String): Url = Url(url)
//    }
//
//    class JsonElementAdapter {
//        @ToJson
//        fun toJson(jsonElement: JsonElement) = jsonElement.toString()
//
////        @FromJson
////        fun fromJson(json: String): JsonElement = json.toJson()
//    }
//}