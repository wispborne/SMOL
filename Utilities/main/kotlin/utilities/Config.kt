package utilities

import com.google.gson.Gson
import java.util.prefs.Preferences
import kotlin.reflect.KProperty
import kotlin.reflect.javaType

abstract class Config(
    @Transient private val prefStorage: PrefStorage
) {

    @OptIn(ExperimentalStdlibApi::class)
    inner class pref<T>(val prefKey: String? = null, val defaultValue: T) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): T =
            prefStorage.get(
                key = prefKey ?: property.name,
                defaultValue = defaultValue,
                property = property as KProperty<T>
            )

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) =
            prefStorage.put(prefKey ?: property.name, value ?: defaultValue, property)
    }

    fun clear() = prefStorage.clear()

    fun reload() = prefStorage.reload()

    interface PrefStorage {
        fun <T> get(key: String, defaultValue: T, property: KProperty<T>): T
        fun <T> put(key: String, value: T?, property: KProperty<T>)
        fun clear()
        fun reload()
    }

    class JavaRegistryPrefStorage(private val gson: Gson) : PrefStorage {
        @OptIn(ExperimentalStdlibApi::class)
        override fun <T> get(key: String, defaultValue: T, property: KProperty<T>): T =
            when (property.returnType) {
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
                            gson.fromJson<T>(it, property.returnType.javaType)
                        } ?: defaultValue
            }

        override fun <T> put(key: String, value: T?, property: KProperty<T>) =
            when (property.returnType) {
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
