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

package smol.access.business

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import smol.access.Constants
import smol.access.HttpClientBuilder
import smol.access.config.VersionCheckerCache
import smol.access.config.VersionCheckerCachedInfo
import smol.access.model.Mod
import smol.access.model.ModId
import smol.access.model.VersionCheckerInfo
import smol.timber.ktx.Timber
import smol.timber.ktx.i
import smol.utilities.Jsanity
import smol.utilities.parallelMap
import smol.utilities.trace
import java.time.Instant

interface IVersionChecker {
    /**
     * Gets the cached online version for the [mod].
     */
    fun getOnlineVersion(modId: ModId): VersionCheckerInfo?

    val onlineVersions: StateFlow<Map<ModId, VersionCheckerCachedInfo>>

    @Suppress("ConvertCallChainIntoSequence")
    /**
     * Look up the version checker file online.
     */
    suspend fun lookUpVersions(mods: List<Mod>, forceLookup: Boolean)
}

/**
 * I think I made an interface so that the internal stuff here wasn't exposed outside of the module.
 */
internal class VersionChecker(
    private val gson: Jsanity,
    private val versionCheckerCache: VersionCheckerCache,
    private val userManager: UserManager,
    private val modsCache: ModsCache,
    private val httpClientBuilder: HttpClientBuilder
) : IVersionChecker {
    companion object {
        const val DEFAULT_CHECK_INTERVAL_MILLIS: Long = 1000 * 60 * 5 // 5 mins
    }

    override fun getOnlineVersion(modId: ModId) =
        versionCheckerCache.onlineVersions.value[modId]?.info

    override val onlineVersions: StateFlow<Map<ModId, VersionCheckerCachedInfo>>
        get() = versionCheckerCache.onlineVersions

    @Suppress("ConvertCallChainIntoSequence")
    override suspend fun lookUpVersions(mods: List<Mod>, forceLookup: Boolean) {
        withContext(Dispatchers.IO) {
            httpClientBuilder.invoke().use { client ->
                val results =
                    trace({ _, millis ->
                        Timber.tag(Constants.TAG_TRACE).i { "Version checked ${mods.count()} mods in ${millis}ms" }
                    }) {
                        mods
                            .distinctBy { it.id }
                            .mapNotNull { it.findHighestVersion }
                            .filter { !it.versionCheckerInfo?.masterVersionFile.isNullOrBlank() }
                            .filter {
                                val msSinceLastCheck = Instant.now().toEpochMilli()
                                    .minus(
                                        (versionCheckerCache.onlineVersions.value[it.modInfo.id]?.lastLookupTimestamp
                                            ?: 0L)
                                    )
                                val checkIntervalMillis =
                                    userManager.activeProfile.value.versionCheckerIntervalMillis
                                        ?: DEFAULT_CHECK_INTERVAL_MILLIS

                                return@filter if (!forceLookup && msSinceLastCheck < checkIntervalMillis) {
                                    Timber.d { "Skipping version check for '${it.modInfo.id}', it has only been ${msSinceLastCheck / 1000}s of ${checkIntervalMillis / 1000}s." }
                                    false
                                } else
                                    true
                            }
                            .parallelMap { modVariant ->
                                val fixedUrl = fixUrl(modVariant.versionCheckerInfo?.masterVersionFile ?: "")
                                kotlin.runCatching {
                                    client.get(fixedUrl)
                                        .body<String>()
                                        .let {
                                            modVariant.mod(modsCache) to gson.fromJson<VersionCheckerInfo>(
                                                json = it,
                                                file = modVariant.modInfo.name.orEmpty(),
                                                shouldStripComments = true
                                            )
                                        }
                                }
                                    .onFailure {
                                        fun message(error: String?) =
                                            "Version check failed for ${modVariant.modInfo.name}: $error (url: ${fixedUrl})"
                                        if (it is ClientRequestException) {
                                            Timber.w {
                                                // API errors tend to include the entire webpage html in the error message,
                                                // so only show the first line.
                                                message(it.message.lines().firstOrNull())
                                            }
                                        } else {
                                            Timber.w { message(it.message) }
                                        }
                                    }
                                    .getOrNull()
                            }
                            .filterNotNull()
                            .filter { it.first != null }
                            .map { it.first!! to it.second }
                            .onEach {
                                Timber.d {
                                    "Version checked ${it.first.findHighestVersion?.modInfo?.name}: " +
                                            "existing: ${it.first.findHighestVersion?.versionCheckerInfo?.modVersion}" +
                                            ", online: ${it.second}" +
                                            " | (url: ${it.first.findHighestVersion?.versionCheckerInfo?.masterVersionFile})"
                                }
                            }
                    }

                versionCheckerCache.onlineVersions.value =
                    versionCheckerCache.onlineVersions.value
                        .plus(results
                            .associate {
                                it.first.id to VersionCheckerCachedInfo(
                                    info = it.second,
                                    lastLookupTimestamp = Instant.now().toEpochMilli()
                                )
                            })


            }
        }
    }

    /**
     * User linked to the page for their version file on github instead of to the raw file.
     */
    private val githubFilePageRegex =
        Regex("""https://github.com/.+/blob/.+/assets/.+.version""", RegexOption.IGNORE_CASE)

    /**
     * User set dl=0 instead of dl=1 when hosted on dropbox.
     */
    private val dropboxDlPageRegex = Regex("""https://www.dropbox.com/s/.+/.+.version\?dl=0""", RegexOption.IGNORE_CASE)


    private fun fixUrl(urlString: String): String {
        return when {
            urlString.matches(githubFilePageRegex) -> {
                urlString
                    .replace("github.com", "raw.githubusercontent.com", ignoreCase = true)
                    .replace("blob/", "", ignoreCase = true)
            }

            urlString.matches(dropboxDlPageRegex) -> {
                urlString
                    .replace("dl=0", "dl=1", ignoreCase = true)
            }

            else -> urlString
        }
            .also {
                if (urlString != it) {
                    Timber.i { "Fixed Version Checker url from '$urlString' to '$it'." }
                }
            }
    }
}