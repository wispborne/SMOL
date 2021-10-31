package config

import VERCHECK_CACHE_PATH
import com.google.gson.Gson
import model.ModId
import model.VersionCheckerInfo

class VersionCheckerCache(gson: Gson) : Config(gson, JsonFilePrefStorage(gson, VERCHECK_CACHE_PATH)) {
    var onlineVersions: Map<ModId, VersionCheckerInfo.Version> by pref(defaultValue = emptyMap())
}