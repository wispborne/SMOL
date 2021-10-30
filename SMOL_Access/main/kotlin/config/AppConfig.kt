package config

import APPCONFIG_PATH
import com.google.gson.Gson
import model.UserProfile

class AppConfig(gson: Gson) :
    Config(
        gson, JsonFilePrefStorage(
            gson = gson,
            file = APPCONFIG_PATH
        )
    ) {
    var gamePath: String? by pref(prefKey = "gamePath", defaultValue = null)
    var archivesPath: String? by pref("archivesPath", defaultValue = null)
    var stagingPath: String? by pref(prefKey = "stagingPath", defaultValue = null)
    internal var userProfile: UserProfile? by pref(prefKey = "userProfile", defaultValue = null)
}
