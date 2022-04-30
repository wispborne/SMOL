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

import com.google.gson.Gson
import java.util.prefs.Preferences
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.javaType

interface IConfig {
//    val prefStorage: PrefStorage
    fun clear()
    fun reload()
    interface PrefStorage {
        fun <T> get(key: String, defaultValue: T, type: KType): T
        fun <T> put(key: String, value: T?, type: KType)
        fun clear()
        fun reload()
    }
}

abstract class Config(
    protected val prefStorage: IConfig.PrefStorage
) : IConfig {

    @OptIn(ExperimentalStdlibApi::class)
    open inner class pref<T>(val prefKey: String? = null, val defaultValue: T) {
        open operator fun getValue(thisRef: Any?, property: KProperty<*>): T =
            prefStorage.get(
                key = getPrefKey(prefKey, property),
                defaultValue = defaultValue,
                type = property.returnType
            )

        open operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) =
            prefStorage.put(getPrefKey(prefKey, property), value ?: defaultValue, property.returnType)

        private fun getPrefKey(prefKey: String? = null, property: KProperty<*>) = prefKey ?: property.name
    }

    override fun clear() = prefStorage.clear()

    override fun reload() = prefStorage.reload()

    class JavaRegistryPrefStorage(private val gson: Gson) : IConfig.PrefStorage {
        @OptIn(ExperimentalStdlibApi::class)
        override fun <T> get(key: String, defaultValue: T, type: KType): T =
            when (type) {
                String::class ->
                    Preferences.userRoot().get(key, defaultValue as String) as T
                Int::class ->
                    Preferences.userRoot().getInt(key, defaultValue as Int) as T
                Float::class ->
                    Preferences.userRoot().getFloat(key, defaultValue as Float) as T
                Boolean::class ->
                    Preferences.userRoot().getBoolean(key, defaultValue as Boolean) as T
                Long::class ->
                    Preferences.userRoot().getLong(key, defaultValue as Long) as T
                else ->
                    (Preferences.userRoot().get(key, defaultValue as String? ?: ""))
                        .let {
                            gson.fromJson<T>(it, type.javaType)
                        } ?: defaultValue
            }

        override fun <T> put(key: String, value: T?, type: KType) =
            when (type) {
                String::class ->
                    Preferences.userRoot().put(key, value as String)
                Int::class ->
                    Preferences.userRoot().putInt(key, value as Int)
                Float::class ->
                    Preferences.userRoot().putFloat(key, value as Float)
                Boolean::class ->
                    Preferences.userRoot().putBoolean(key, value as Boolean)
                Long::class ->
                    Preferences.userRoot().putLong(key, value as Long)
                else ->
                    Preferences.userRoot()
                        .put(key, value?.let { gson.toJson(value) })
            }

        override fun clear() = Preferences.userRoot().clear()
        override fun reload() = Unit
    }
}
