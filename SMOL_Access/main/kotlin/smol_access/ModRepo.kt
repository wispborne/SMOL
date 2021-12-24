package smol_access

import mod_repo.ModIndexCache
import mod_repo.ModdingSubforumCache
import utilities.Jsanity

class ModRepo internal constructor(jsanity: Jsanity) {
    private val modIndexCache = ModIndexCache(jsanity)
    private val moddingSubforumCache = ModdingSubforumCache(jsanity)

    fun getModIndexItems() = modIndexCache.items
    fun getModdingSubforumItems() = moddingSubforumCache.items
}