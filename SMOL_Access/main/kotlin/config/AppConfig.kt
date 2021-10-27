package config

import com.squareup.moshi.Moshi
import model.UserProfile

internal class AppConfig(moshi: Moshi) : Config(moshi) {
    var gamePath: String? by pref(prefKey = "gamePath", defaultValue = null)
    var archivesPath: String? by pref("archivesPath", defaultValue = null)
    var stagingPath: String? by pref(prefKey = "stagingPath", defaultValue = null)
    var userProfile: UserProfile? by pref(prefKey = "userProfile", defaultValue = null)
}
