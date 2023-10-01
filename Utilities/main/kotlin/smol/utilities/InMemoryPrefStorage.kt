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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import smol.timber.ktx.Timber
import java.util.prefs.Preferences
import kotlin.reflect.KProperty
import kotlin.reflect.KType

class InMemoryPrefStorage(private val wrapped: IConfig.PrefStorage) : IConfig.PrefStorage {
    private val memory = mutableMapOf<String, Any?>()
    private val scope = CoroutineScope(Job())

    @OptIn(ExperimentalStdlibApi::class)
    override fun <T> get(key: String, defaultValue: T, type: KType): T =
        if (memory.containsKey(key)) {
            (memory[key] as? T) ?: defaultValue
        } else {
            wrapped.get(key, defaultValue, type)
                .also {
                    if (it != null) memory[key] = it
                }
        }

    @OptIn(ExperimentalStdlibApi::class)
    override fun <T> put(key: String, value: T?, type: KType) {
        memory[key] = value
        scope.launch(Dispatchers.IO) {
            runCatching {
                wrapped.put(key, value, type)
            }
                .onFailure { Timber.w(it) }
        }
    }

    override fun clear() = Preferences.userRoot().clear()

    override fun reload() = memory.clear()
}