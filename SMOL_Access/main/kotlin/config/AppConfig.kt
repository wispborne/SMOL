package config

import com.google.gson.Gson
import model.UserProfile
import java.nio.file.Paths

class AppConfig(gson: Gson) :
    Config(
        gson, JsonFilePrefStorage(
            gson = gson,
            file = Paths.get("").toFile().resolve("SMOL_AppConfig.json")
        )
    ) {
    var gamePath: String? by pref(prefKey = "gamePath", defaultValue = null)
    var archivesPath: String? by pref("archivesPath", defaultValue = null)
    var stagingPath: String? by pref(prefKey = "stagingPath", defaultValue = null)
    internal var userProfile: UserProfile? by pref(prefKey = "userProfile", defaultValue = null)
}
