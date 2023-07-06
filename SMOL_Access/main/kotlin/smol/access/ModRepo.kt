/*
 * This file is distributed under the GPLv3. An informal description follows:
 * - Anyone can copy, modify and distribute this software as long as the other points are followed.
 * - You must include the license and copyright notice with each and every distribution.
 * - You may this software for commercial purposes.
 * - If you modify it, you must indicate changes made to the code.
 * - Any modifications of this code base MUST be distributed with the same license, GPLv3.
 * - This software is provided without warranty.
 * - The software author or license can not be held liable for any damages inflicted by the software.
 * The full license is available from <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package smol.access

import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import smol.access.config.AppConfig
import smol.mod_repo.ModRepoCache
import smol.mod_repo.ScrapedMod
import smol.timber.ktx.Timber
import smol.utilities.Jsanity
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.name
import kotlin.io.path.readText

class ModRepo internal constructor(private val jsanity: Jsanity, private val httpClientBuilder: HttpClientBuilder) {
    private val modRepoCache = ModRepoCache(jsanity)
    private val items_ = MutableStateFlow(emptyList<ScrapedMod>())
    val items = items_.asStateFlow()
    private val lastUpdated_ = MutableStateFlow<ZonedDateTime?>(null)
    val lastUpdated = lastUpdated_.asStateFlow()

    private fun getLastUpdated(): ZonedDateTime? = modRepoCache.lastUpdated?.let { dateTimeStr ->
        runCatching { ZonedDateTime.parse(dateTimeStr, DateTimeFormatter.ISO_DATE_TIME) }
            .onFailure { Timber.w(it) }
            .getOrNull()
    }

    suspend fun refreshFromInternet(updateChannel: AppConfig.UpdateChannel) {
        val modRepoUrl = when (updateChannel) {
            AppConfig.UpdateChannel.Stable -> Constants.modRepoUrlStable
            AppConfig.UpdateChannel.Unstable -> Constants.modRepoUrlUnstable
            AppConfig.UpdateChannel.Test -> Constants.modRepoUrlTest
        }

        withContext(Dispatchers.IO) {
            val localModRepo = Path.of("../Mod_Repo/ModRepo.json")
            val freshIndexMods =
                kotlin.runCatching {
                    // Try to read from local file first, for debugging, in ./App/ModRepo.json.
                    if (true) {
                        localModRepo.readText()
                    } else
                        throw NotImplementedError()
                }
                    .onFailure {
                        Timber.d(it) { "No local mod repo found at $localModRepo for testing, fetching latest online." }
                    }
                    .recoverCatching {
                        httpClientBuilder.invoke().use { client ->
                            client.get(modRepoUrl).body()
                        }
                    }
                    .onSuccess { Timber.i { "Fetched mod repo from $modRepoUrl." } }
                    .onFailure { Timber.w(it) { "Unable to fetch mods from $modRepoUrl." } }
                    .getOrNull()
                    ?.let {
                        jsanity.fromJson<ScrapedModsRepo>(
                            json = it,
                            filename = localModRepo.name,
                            shouldStripComments = false
                        )
                    }

            if (freshIndexMods != null) {
                Timber.i { "Updated scraped mods. Previous: ${modRepoCache.items.count()}, now: ${freshIndexMods.items.count()}" }
                modRepoCache.items = freshIndexMods.items
                modRepoCache.lastUpdated = freshIndexMods.lastUpdated
                lastUpdated_.value = getLastUpdated()
                items_.value = modRepoCache.items
            }
        }
    }
}

data class ScrapedModsRepo(
    val items: List<ScrapedMod>,
    val lastUpdated: String,
)