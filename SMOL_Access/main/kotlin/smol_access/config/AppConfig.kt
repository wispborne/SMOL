package smol_access.config

import com.google.gson.Gson
import smol_access.Constants
import smol_access.model.UserProfile
import utilities.Config
import utilities.JsonFilePrefStorage

class AppConfig(gson: Gson) :
    Config(
        JsonFilePrefStorage(
            gson = gson,
            file = Constants.APP_CONFIG_PATH
        )
    ) {
    var gamePath: String? by pref(prefKey = "gamePath", defaultValue = null)
    var archivesPath: String? by pref("archivesPath", defaultValue = null)
    var stagingPath: String? by pref(prefKey = "stagingPath", defaultValue = null)
    var lastFilePickerDirectory: String? by pref(prefKey = "lastFilePickerDirectory", defaultValue = null)
    internal var userProfile: UserProfile? by pref(prefKey = "userProfile", defaultValue = null)

    override fun toString(): String {
        return "AppConfig(" +
                "gamePath=$gamePath, " +
                "archivesPath=$archivesPath, " +
                "stagingPath=$stagingPath, " +
                "lastFilePickerDirectory=$lastFilePickerDirectory, " +
                "userProfile=$userProfile" +
                ")"
    }

}
