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
    var updateChannel: String? by pref(prefKey = "updateChannel", defaultValue = "unstable")
    var gamePath: String? by pref(prefKey = "gamePath", defaultValue = null)
    var archivesPath: String? by pref("archivesPath", defaultValue = null)
    var stagingPath: String? by pref(prefKey = "stagingPath", defaultValue = null)
    var lastFilePickerDirectory: String? by pref(prefKey = "lastFilePickerDirectory", defaultValue = null)
    var jre8Url: String by pref(
        prefKey = "jre8Url",
        defaultValue = "https://drive.google.com/uc?id=155Lk0ml9AUGp5NwtTZGpdu7e7Ehdyeth&export=download"
    )
    internal var userProfile: UserProfile? by pref(prefKey = "userProfile", defaultValue = null)

    override fun toString(): String {
        return "AppConfig(" +
                "updateChannel=$updateChannel, " +
                "gamePath=$gamePath, " +
                "archivesPath=$archivesPath, " +
                "stagingPath=$stagingPath, " +
                "lastFilePickerDirectory=$lastFilePickerDirectory, " +
                "jre8Url=$jre8Url, " +
                "userProfile=$userProfile" +
                ")"
    }
}
