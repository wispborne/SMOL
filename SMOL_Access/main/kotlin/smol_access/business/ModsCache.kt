package smol_access.business

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.ktx.Timber

internal class ModsCache {
    val modsMutable = MutableStateFlow<ModListUpdate?>(null)
    val mods = modsMutable.asStateFlow()
        .also {
            GlobalScope.launch(Dispatchers.Default) {
                it.collect { Timber.i { "Mod list updated: ${it?.mods?.size} mods (${it?.added?.joinToString { it.smolId }} added, ${it?.removed?.joinToString { it.smolId }} removed)." } }
            }
        }
}