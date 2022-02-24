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

package smol_app

import smol_access.Constants
import smol_app.util.SmolWindowState
import utilities.Config
import utilities.InMemoryPrefStorage
import utilities.Jsanity
import utilities.JsonFilePrefStorage

class UIConfig(gson: Jsanity) : Config(
    InMemoryPrefStorage(
        JsonFilePrefStorage(
        gson = gson,
        file = Constants.UI_CONFIG_PATH
    ))
) {
    var windowState: SmolWindowState? by pref(prefKey = "windowState", defaultValue = null)
    var modBrowserState: ModBrowserState? by pref(prefKey = "modBrowserState", defaultValue = null)
    var logPanelWidthPercentage: Float by pref(prefKey = "logPanelWidthPercentage", defaultValue = 0.5f)
}

data class ModBrowserState(
    val modListWidthPercent: Float
)