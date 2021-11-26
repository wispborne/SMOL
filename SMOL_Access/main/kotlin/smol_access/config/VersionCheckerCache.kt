package smol_access.config

import com.google.gson.Gson
import smol_access.Constants
import smol_access.model.ModId
import smol_access.model.VersionCheckerInfo
import utilities.Config

class VersionCheckerCache(gson: Gson) : Config(gson, JsonFilePrefStorage(gson, Constants.VERCHECK_CACHE_PATH)) {
    var onlineVersions: Map<ModId, VersionCheckerInfo.Version> by pref(defaultValue = emptyMap())
}