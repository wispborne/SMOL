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

import smol.access.Constants
import smol.access.model.ModId
import smol.access.model.SmolId
import smol.utilities.Config
import smol.utilities.InMemoryPrefStorage
import smol.utilities.Jsanity
import smol.utilities.JsonFilePrefStorage

class VramCheckerCache(gson: Jsanity) :
    Config(
        InMemoryPrefStorage(
            JsonFilePrefStorage(
                gson = gson,
                file = Constants.VRAM_CHECKER_RESULTS_PATH
            )
        )
    ) {
    var bytesPerVariant: Map<SmolId, Result>? by pref(prefKey = "bytesPerVariant", defaultValue = emptyMap())

    data class Result(
        val modId: ModId,
        val version: String,
        val bytesForMod: Long,
        val imageCount: Int,
    )
}