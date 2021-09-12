package util

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import java.util.prefs.Preferences
import kotlin.reflect.KProperty

class AppConfig(private val moshi: Moshi) {
    var gamePath: String? by pref(prefKey = "gamePath", defaultValue = null)
    var stagingPath: String? by pref("stagingPath", defaultValue = null)

    @OptIn(ExperimentalStdlibApi::class)
    inner class pref<T>(val prefKey: String? = null, val defaultValue: T?) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): T? =
            when (property.returnType) {
                String::class ->
                    Preferences.userRoot().get(prefKey ?: property.name, defaultValue as String) as T?
                Int::class ->
                    Preferences.userRoot().getInt(prefKey ?: property.name, defaultValue as Int) as T?
                Float::class ->
                    Preferences.userRoot().getFloat(prefKey ?: property.name, defaultValue as Float) as T?
                Boolean::class ->
                    Preferences.userRoot().getBoolean(prefKey ?: property.name, defaultValue as Boolean) as T?
                Long::class ->
                    Preferences.userRoot().getLong(prefKey ?: property.name, defaultValue as Long) as T?
                else ->
                    (Preferences.userRoot().get(prefKey ?: property.name, defaultValue as String?))
                        ?.let {
                            moshi.adapter<T>(property.returnType).fromJson(it)
                        } ?: defaultValue
            }

        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
            when (property.returnType) {
                String::class ->
                    Preferences.userRoot().put(prefKey ?: property.name, value as String)
                Int::class ->
                    Preferences.userRoot().putInt(prefKey ?: property.name, value as Int)
                Float::class ->
                    Preferences.userRoot().putFloat(prefKey ?: property.name, value as Float)
                Boolean::class ->
                    Preferences.userRoot().putBoolean(prefKey ?: property.name, value as Boolean)
                Long::class ->
                    Preferences.userRoot().putLong(prefKey ?: property.name, value as Long)
                else ->
                    Preferences.userRoot()
                        .put(prefKey ?: property.name, value
                            ?.let { moshi.adapter<T>(property.returnType).toJson(value) })
            }
        }
    }
}
