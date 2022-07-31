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

package smol.access.config

import kotlinx.coroutines.flow.MutableStateFlow
import smol.access.Constants
import smol.access.StateFlowWrapper
import smol.access.model.ModId
import smol.access.model.VersionCheckerInfo
import smol.utilities.InMemoryPrefStorage
import smol.utilities.Jsanity
import smol.utilities.JsonFilePrefStorage
import kotlin.reflect.typeOf

class VersionCheckerCache(gson: Jsanity) :
    StateFlowWrapper(InMemoryPrefStorage(JsonFilePrefStorage(gson, Constants.VERCHECK_CACHE_PATH))) {

    var onlineVersions: MutableStateFlow<Map<ModId, VersionCheckerCachedInfo>> = stateFlowPref(
        prefKey = "onlineVersions",
        defaultValue = emptyMap(),
        type = typeOf<Map<ModId, VersionCheckerCachedInfo>>()
    )
}

data class VersionCheckerCachedInfo(
    val lastLookupTimestamp: Long,
    val info: VersionCheckerInfo
)