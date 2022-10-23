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

package smol.mod_repo

import com.github.salomonbrys.kotson.registerTypeAdapter
import com.github.salomonbrys.kotson.string
import com.github.salomonbrys.kotson.toJson
import com.google.gson.GsonBuilder
import io.ktor.http.*
import kotlinx.coroutines.*
import smol.timber.LogLevel
import smol.timber.Timber
import smol.timber.ktx.i
import smol.timber.ktx.w
import smol.utilities.Jsanity
import smol.utilities.exists
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.io.path.*


class Main {
    companion object {
        internal val CONFIG_FOLDER_DEFAULT = Path.of("")
        internal const val FORUM_BASE_URL = "https://fractalsoftworks.com/forum/index.php"
        internal val configFilePath = Path.of("config.properties")
        internal const val verboseOutput = true

        data class Config(
            val lessScraping: Boolean,
            val enableForums: Boolean,
            val enableDiscord: Boolean,
            val enableNexus: Boolean,
            val logLevel: String,
            val discordAuthToken: String?,
            val nexusApiToken: String?
        )

        @JvmStatic
        fun main(args: Array<String>) {
            val config = readConfig() ?: return
            val logLevel = LogLevel.valueOf(config.logLevel)

            val logFile = Path.of("ModRepo.log")
            val logOut = logFile
                .apply {
                    deleteIfExists()
                    createFile()
                }
                .bufferedWriter()

            Timber.plant(
                Timber.DebugTree(
                    logLevel = logLevel, appenders = listOf { level, log ->
                        if (level >= logLevel)
                            logOut.appendLine(log)
                    }
                )
            )

            val dateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .withLocale(Locale.US)
                .withZone(ZoneId.of("UTC"))

            val gsonBuilder = GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .registerTypeAdapter<Url> {
                    deserialize { arg ->
                        kotlin.runCatching { Url(arg.json.string) }
                            .onFailure { Timber.w(it) { arg.json.toString() } }
                            .getOrNull()
                    }
                    serialize { it.src.toString().toJson() }
                }
                .registerTypeAdapter<Date> {
                    serialize {
                        dateTimeFormatter.format(it.src.toInstant()).toJson()
                    }
                }

            val jsanity = Jsanity(gsonBuilder.create())
            val modRepoCache = ModRepoCache(jsanity)

            val scrapeJob = CoroutineScope(Job()).async {
                kotlin.runCatching {
                    withTimeout(90000) {
                        if (config.enableForums) {
                            ForumScraper.run(
                                config = config,
                                moddingForumPagesToScrape = if (config.lessScraping) 3 else 15,
                                modForumPagesToScrape = if (config.lessScraping) 3 else 12
                            )
                        } else emptyList()
                    }
                }
                    .onFailure { Timber.e(it) }
                    .getOrNull()
            }

            val discordJob = CoroutineScope(Job()).async {
                kotlin.runCatching {
                    // isDownloadable takes forever, don't use a timeout.
//                    withTimeout(90000) {
                        if (config.enableDiscord) {
                            DiscordReader.readAllMessages(
                                config = config,
                                gsonBuilder = gsonBuilder
                            )
                        } else emptyList()
//                    }
                }
                    .onFailure { Timber.e(it) }
                    .getOrNull()
            }

            val nexusModsJob = CoroutineScope(Job()).async {
                kotlin.runCatching {
                    withTimeout(180000) {
                        if (config.enableNexus) {
                            NexusReader.readAllMessages(
                                config = config
                            )
                        } else emptyList()
                    }
                }
                    .onFailure { Timber.e(it) }
                    .getOrNull()
            }

            runBlocking {
                val forumMods = scrapeJob.await() ?: emptyList()
                val discordMods = discordJob.await() ?: emptyList()
                val nexusMods = nexusModsJob.await() ?: emptyList()

                Timber.i { "Found ${forumMods.size} forum mods, ${discordMods.size} Discord mods, and ${nexusMods.size} Nexus mods." }
                Timber.i { "Starting merge..." }

                ModMerger()
                    .merge(forumMods + discordMods + nexusMods)
                    .run {
                        println("Saving ${this.count()} mods to ${ModRepoCache.location.toAbsolutePath()}...")
                        modRepoCache.items = this
                        modRepoCache.totalCount = this.count()
                        modRepoCache.lastUpdated = Instant.now().truncatedTo(ChronoUnit.MINUTES).toString()
                        println("Saved ${this.count()} mods to ${ModRepoCache.location.toAbsolutePath()}.")
                    }

                delay(1000)
                println("Wrote log to ${logFile.absolutePathString()}.")
                kotlin.runCatching {
                    logOut.close()
                }
            }
        }

        private fun readConfig(): Config? =
            if (kotlin.runCatching { configFilePath.exists() }
                    .onFailure { System.err.println(it) }
                    .getOrNull() == true)
                Properties().apply { this.load(configFilePath.bufferedReader()) }
                    .let {
                        Config(
                            lessScraping = it["less_scraping"].toString().toBoolean(),
                            enableForums = it["enable_forums"].toString().toBoolean(),
                            enableDiscord = it["enable_discord"].toString().toBoolean(),
                            enableNexus = it["enable_nexus"].toString().toBoolean(),
                            logLevel = it["log_level"].toString(),
                            discordAuthToken = it["auth_token"]?.toString(),
                            nexusApiToken = it["nexus_api_token"]?.toString(),
                        )
                    }
            else {
                System.err.println("Unable to find ${configFilePath.absolutePathString()}.")
                null
            }
    }
}