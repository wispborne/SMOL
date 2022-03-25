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

package smol_access

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import utilities.Config
import utilities.IConfig
import kotlin.reflect.KProperty
import kotlin.reflect.KType

open class StateFlowWrapper(wrapped: IConfig.PrefStorage) : Config(wrapped) {
    private val scope = CoroutineScope(Job())

    @Suppress("ClassName")
    inner class stateFlowPref<T>(prefKey: String, defaultValue: T, type: KType) :
        MutableStateFlow<T> by MutableStateFlow(
            prefStorage.get(
                prefKey,
                defaultValue,
                type
            )
        ) {

        init {
            scope.launch(Dispatchers.IO) {
                this@stateFlowPref.collectLatest {
                    prefStorage.put(prefKey, value, type)
                }
            }
        }

        operator fun getValue(thisRef: Any?, property: KProperty<*>): MutableStateFlow<T> {
            return this
        }
    }

//    override val prefStorage: IConfig.PrefStorage
//        get() = wrapped
//
//    override fun clear() = wrapped.clear()
//
//    override fun reload() = wrapped.reload()


//    @OptIn(ExperimentalStdlibApi::class)
//    open inner class pref<T>(val prefKey: String? = null, val defaultValue: T) {
//        open operator fun getValue(thisRef: Any?, property: KProperty<*>): T = wrapped.get()
//
//        open operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) =
//            prefStorage.put(prefKey ?: property.name, value ?: defaultValue, property)
//    }

//    @OptIn(ExperimentalStdlibApi::class)
//    open inner class pref<K, T : MutableStateFlow<K>>(private val prefKey: String? = null, private val defaultValue: T) {
//        private val value = MutableStateFlow(null)
//        private val wrappedPref by
//
//        operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
//            wrapped.get(prefKey, defaultValue, property)
//        }
//
//        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
//            super.setValue(thisRef, property, value)
//        }
//    }
}