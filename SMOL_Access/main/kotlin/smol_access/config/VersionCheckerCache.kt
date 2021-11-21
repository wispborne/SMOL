package smol_access.config

import smol_access.VERCHECK_CACHE_PATH
import com.google.gson.Gson
import smol_access.model.ModId
import smol_access.model.VersionCheckerInfo
import utilities.Config

class VersionCheckerCache(gson: Gson) : Config(gson, JsonFilePrefStorage(gson, VERCHECK_CACHE_PATH)) {
    var onlineVersions: Map<ModId, VersionCheckerInfo.Version> by pref(defaultValue = emptyMap())
}