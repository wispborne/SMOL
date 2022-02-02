package smol_access

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import mod_repo.ModRepoCache
import mod_repo.ScrapedMod
import timber.ktx.Timber
import utilities.Jsanity
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class ModRepo internal constructor(private val jsanity: Jsanity, private val httpClientBuilder: HttpClientBuilder) {
    private val modRepoCache = ModRepoCache(jsanity)
    private val scope = CoroutineScope(Job())

    fun getItems(): List<ScrapedMod> = modRepoCache.items
    fun getLastUpdated(): ZonedDateTime? = modRepoCache.lastUpdated?.let { dateTimeStr ->
        runCatching { ZonedDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_DATE_TIME) }
            .onFailure { Timber.w(it) }
            .getOrNull()
    }

    suspend fun refreshFromInternet() {
        withContext(Dispatchers.IO) {
            httpClientBuilder.invoke().use { client ->
                val freshIndexMods = kotlin.runCatching {
                    client.get<HttpResponse>(Constants.modRepoUrl)
                        .receive<String>()
                        .let { jsanity.fromJson<ScrapedModsRepo>(json = it, shouldStripComments = false) }
                }
                    .onFailure {
                        Timber.w(it) { "Unable to fetch mods from ${Constants.modRepoUrl}." }
                    }
                    .getOrNull()

                if (freshIndexMods != null) {
                    Timber.i { "Updated scraped mods. Previous: ${modRepoCache.items.count()}, now: ${freshIndexMods.items.count()}" }
                    modRepoCache.items = freshIndexMods.items
                    modRepoCache.lastUpdated = freshIndexMods.lastUpdated
                }
            }
        }
    }
}

data class ScrapedModsRepo(
    val items: List<ScrapedMod>,
    val lastUpdated: String,
)