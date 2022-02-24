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