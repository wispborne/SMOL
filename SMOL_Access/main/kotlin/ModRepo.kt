import mod_repo.ModIndexCache
import mod_repo.ModdingSubforumCache

class ModRepo internal constructor(
) {
    private val modIndexCache = ModIndexCache()
    private val moddingSubforumCache = ModdingSubforumCache()

    fun getModIndexItems() = modIndexCache.items
    fun getModdingSubforumItems() = moddingSubforumCache.items
}