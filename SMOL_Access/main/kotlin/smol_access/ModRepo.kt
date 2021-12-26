package smol_access

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import mod_repo.ModIndexCache
import mod_repo.ModdingSubforumCache
import mod_repo.ScrapedMod
import timber.ktx.Timber
import utilities.Jsanity

class ModRepo internal constructor(private val jsanity: Jsanity, private val httpClientBuilder: HttpClientBuilder) {
    private val modIndexCache = ModIndexCache(jsanity)
    private val moddingSubforumCache = ModdingSubforumCache(jsanity)
    private val scope = CoroutineScope(Job())

    fun getModIndexItems(): List<ScrapedMod> = modIndexCache.items
    fun getModdingSubforumItems(): List<ScrapedMod> = moddingSubforumCache.items

    fun refreshFromInternet() {

        scope.launch(Dispatchers.IO) {
            httpClientBuilder.invoke().use { client ->
                val freshIndexMods = kotlin.runCatching {
                    client.get<HttpResponse>(Constants.indexUrl)
                        .receive<String>()
                        .let { jsanity.fromJson<ScrapedModsRepo>(json = it, shouldStripComments = false) }
                }
                    .onFailure {
                        Timber.w(it) { "Unable to fetch Mod Index mods from ${Constants.indexUrl}." }
                    }
                    .getOrNull()

                if (freshIndexMods != null) {
                    Timber.i { "Updated Index mods. Previous: ${modIndexCache.items.count()}, now: ${freshIndexMods.items.count()}" }
                    modIndexCache.items = freshIndexMods.items
                }
            }
        }

        scope.launch(Dispatchers.IO) {
            httpClientBuilder.invoke().use { client ->
                val freshModdingMods = kotlin.runCatching {
                    client.get<HttpResponse>(Constants.moddingForumUrl)
                        .receive<String>()
                        .let { jsanity.fromJson<ScrapedModsRepo>(json = it, shouldStripComments = false) }
                }
                    .onFailure {
                        Timber.w(it) { "Unable to fetch Modding Forum mods from ${Constants.moddingForumUrl}." }
                    }
                    .getOrNull()

                if (freshModdingMods != null) {
                    Timber.i { "Updated Modding Forum mods. Previous: ${moddingSubforumCache.items.count()}, now: ${freshModdingMods.items.count()}" }
                    moddingSubforumCache.items = freshModdingMods.items
                }
            }
        }
    }
}

data class ScrapedModsRepo(
    val items: List<ScrapedMod>
)