package utilities

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.ktx.Timber
import java.util.prefs.Preferences
import kotlin.reflect.KProperty

class InMemoryPrefStorage(private val wrapped: Config.PrefStorage) : Config.PrefStorage {
    private val memory = mutableMapOf<String, Any?>()
    private val scope = CoroutineScope(Job())

    @OptIn(ExperimentalStdlibApi::class)
    override fun <T> get(key: String, defaultValue: T, property: KProperty<T>): T =
        if (memory.containsKey(key)) {
            (memory[key] as? T) ?: defaultValue
        } else {
            wrapped.get(key, defaultValue, property)
                .also {
                    if (it != null) memory[key] = it
                }
        }

    @OptIn(ExperimentalStdlibApi::class)
    override fun <T> put(key: String, value: T?, property: KProperty<T>) {
        memory[key] = value
        scope.launch(Dispatchers.IO) {
            kotlin.runCatching {
                wrapped.put(key, value, property)
            }
                .onFailure { Timber.w(it) }
        }
    }

    override fun clear() = Preferences.userRoot().clear()

    override fun reload() = memory.clear()
}