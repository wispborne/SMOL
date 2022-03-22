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

package mod_repo

import com.github.androidpasswordstore.sublimefuzzy.Fuzzy
import com.github.salomonbrys.kotson.registerTypeAdapter
import com.github.salomonbrys.kotson.string
import com.github.salomonbrys.kotson.toJson
import com.google.gson.GsonBuilder
import io.ktor.http.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import timber.LogLevel
import timber.ktx.Timber
import utilities.Jsanity
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.io.path.absolutePathString
import kotlin.io.path.bufferedWriter
import kotlin.io.path.createFile
import kotlin.io.path.deleteIfExists


class Main {
    companion object {
        internal val CONFIG_FOLDER_DEFAULT = Path.of("")
        internal val FORUM_BASE_URL = "https://fractalsoftworks.com/forum/index.php"
        internal val configFilePath = Path.of("config.properties")
        internal const val verboseOutput = true

        /**
         * Reduces the amount of scraping done for a faster runtime and less load on the server.
         */
        private const val DEV_MODE = false
        private val logLevel = LogLevel.DEBUG

        @JvmStatic
        fun main(args: Array<String>) {
            val logFile = Path.of("ModRepo.log")
            val logOut = logFile
                .apply {
                    deleteIfExists()
                    createFile()
                }
                .bufferedWriter()

            Timber.plant(
                timber.Timber.DebugTree(
                    logLevel = logLevel, appenders = listOf { level, log ->
                        if (level >= logLevel)
                            logOut.appendLine(log)
                    }
                )
            )

            val jsanity = Jsanity(
                GsonBuilder()
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
                    .create()
            )
            val modRepoCache = ModRepoCache(jsanity)

            val scrapeJob = GlobalScope.async {
                kotlin.runCatching {
                    ForumScraper(
                        moddingForumPagesToScrape = if (DEV_MODE) 3 else 15,
                        modForumPagesToScrape = if (DEV_MODE) 3 else 12
                    ).run()
                }
                    .onFailure { Timber.e(it) }
                    .getOrNull()
            }

            val discordJob = GlobalScope.async {
                kotlin.runCatching {
                    DiscordReader().readAllMessages()
                }
                    .onFailure { Timber.e(it) }
                    .getOrNull()
            }

            runBlocking {
                val forumMods = scrapeJob.await() ?: emptyList()
                val discordMods = discordJob.await() ?: emptyList()

                ModMerger()
                    .merge(forumMods + discordMods)
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

        fun compareToFindBestMatch(
            leftList: List<String>,
            rightList: List<String>,
            stopAtFirstMatch: Boolean = true,
            scoreThreshold: Int = 100
        ): MatchResult {
            Timber.v { "Comparing left: ${leftList.joinToString()} to right: ${rightList.joinToString()}." }
            return leftList
                .flatMap { i ->
                    // Generate all pairs
                    rightList
                        .map { j ->
                            i to j
                        }
                }
                .map { pair ->
                    val fuzzyMatch = Fuzzy.fuzzyMatch(pair.first, pair.second)
                    val obj = MatchResult(
                        leftMatch = pair.first,
                        rightMatch = pair.second,
                        isMatch = fuzzyMatch.first,
                        score = fuzzyMatch.second
                    )

                    Timber.v { "Compared: $obj." }

                    if (stopAtFirstMatch && fuzzyMatch.second > scoreThreshold)
                        return obj
                    obj
                }
                .maxByOrNull { it.score }
                ?.let { highestMatch -> if (highestMatch.score > scoreThreshold) highestMatch else null }
                ?: MatchResult(leftMatch = "", rightMatch = "", isMatch = false, score = 0)
        }

        data class MatchResult(val leftMatch: String, val rightMatch: String, val isMatch: Boolean, val score: Int)
    }
}