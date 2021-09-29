package config

import com.squareup.moshi.Moshi

class AppConfig(moshi: Moshi) : Config(moshi) {
    var gamePath: String? by pref(prefKey = "gamePath", defaultValue = null)
    var archivesPath: String? by pref("archivesPath", defaultValue = null)
    var stagingPath: String? by pref(prefKey = "stagingPath", defaultValue = null)
}
