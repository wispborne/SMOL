package smol_access.config

import smol_access.Constants
import smol_access.model.ModId
import smol_access.model.VersionCheckerInfo
import utilities.Config
import utilities.InMemoryPrefStorage
import utilities.Jsanity
import utilities.JsonFilePrefStorage

class VersionCheckerCache(gson: Jsanity) :
    Config(InMemoryPrefStorage(JsonFilePrefStorage(gson, Constants.VERCHECK_CACHE_PATH))) {
    var onlineVersions: Map<ModId, VersionCheckerInfo.Version> by pref(defaultValue = emptyMap())
    var lastCheckTimestamp: Long by pref(defaultValue = 0L)
}