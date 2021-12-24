package utilities

import com.google.gson.JsonElement
import smol_access.util.IOLock
import timber.ktx.Timber
import java.nio.file.Path
import java.util.prefs.Preferences
import kotlin.io.path.*
import kotlin.reflect.KProperty
import kotlin.reflect.javaType
import kotlin.reflect.typeOf

class JsonFilePrefStorage(private val gson: Jsanity, private val file: Path) : Config.PrefStorage {
    init {
        if (!file.exists()) {
            file.parent?.createDirectories()
            file.createFile()
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun <T> get(key: String, defaultValue: T, property: KProperty<T>): T =
        IOLock.read {
            (((gson.fromJson<Map<*, JsonElement>>(
                json = file.readText(),
                typeOfT = typeOf<Map<*, JsonElement>?>().javaType,
                shouldStripComments = false
            )
                ?: emptyMap<Any, JsonElement>())
                .get(key))
                ?.run { gson.fromJson<T>(this, property.returnType.javaType) }
                ?: defaultValue)
                .also { Timber.v { "Read '$key' as '$it' in '${file.fileName}'." } }
        }

    @OptIn(ExperimentalStdlibApi::class)
    override fun <T> put(key: String, value: T?, property: KProperty<T>) =
        IOLock.write {
            ((gson.fromJson<Map<*, *>>(
                json = file.readText(),
                typeOfT = typeOf<Map<*, *>?>().javaType,
                shouldStripComments = false
            ) ?: emptyMap<Any, Any>())
                .toMutableMap().apply { this[key] = value as T }
                .run { file.writeText(gson.toJson(this)) })
                .also { Timber.v { "Set '$key' as '$it' in '${file.fileName}'." } }
        }

    override fun clear() = Preferences.userRoot().clear()
}