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

package smol_access.config

import smol_access.Constants
import smol_access.model.UserProfile
import utilities.Config
import utilities.InMemoryPrefStorage
import utilities.Jsanity
import utilities.JsonFilePrefStorage

class AppConfig(gson: Jsanity) :
    Config(
        InMemoryPrefStorage(
            JsonFilePrefStorage(
                gson = gson,
                file = Constants.APP_CONFIG_PATH
            )
        )
    ) {
    var updateChannel: UpdateChannel by pref(prefKey = "updateChannel", defaultValue = UpdateChannel.Unstable)
    internal var gamePath: String? by pref(prefKey = "gamePath", defaultValue = null)
    var lastFilePickerDirectory: String? by pref(prefKey = "lastFilePickerDirectory", defaultValue = null)
    var jre8Url: String by pref(
        prefKey = "jre8Url",
        defaultValue = "https://drive.google.com/uc?id=155Lk0ml9AUGp5NwtTZGpdu7e7Ehdyeth&export=download"
    )
    internal var userProfile: UserProfile? by pref(prefKey = "userProfile", defaultValue = null)
    var showGameLauncherWarning: Boolean by pref(prefKey = "showGameLauncherWarning", defaultValue = true)

    override fun toString(): String {
        return "AppConfig(" +
                "updateChannel=$updateChannel, " +
                "gamePath=$gamePath, " +
                "lastFilePickerDirectory=$lastFilePickerDirectory, " +
                "jre8Url=$jre8Url, " +
                "userProfile=$userProfile" +
                ")"
    }

    enum class UpdateChannel {
        Stable,
        Unstable,
        Test,
    }
}
