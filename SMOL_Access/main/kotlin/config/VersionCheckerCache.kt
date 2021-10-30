package config

import VERCHECK_CACHE_PATH
import com.google.gson.Gson
import model.SmolId
import model.VersionCheckerInfo

class VersionCheckerCache(gson: Gson) : Config(gson, JsonFilePrefStorage(gson, VERCHECK_CACHE_PATH)) {
    var onlineVersions: Map<SmolId, VersionCheckerInfo.Version> by pref(defaultValue = emptyMap())
}