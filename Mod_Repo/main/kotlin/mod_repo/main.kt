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

import com.github.salomonbrys.kotson.registerTypeAdapter
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


class Main {
    companion object {
        internal val CONFIG_FOLDER_DEFAULT = Path.of("")
        internal val FORUM_BASE_URL = "https://fractalsoftworks.com/forum/index.php"
        internal val configFilePath = Path.of("config.properties")
        internal val isDebugMode = true

        @JvmStatic
        fun main(args: Array<String>) {
            Timber.plant(timber.Timber.DebugTree(logLevel = LogLevel.INFO, appenders = emptyList()))
            val jsanity = Jsanity(
                GsonBuilder()
                    .setPrettyPrinting()
                    .disableHtmlEscaping()
                    .registerTypeAdapter<Url> {
                        serialize { it.src.toString().toJson() }
                    }
                    .create()
            )
            val modRepoCache = ModRepoCache(jsanity)

            val scrapeJob = GlobalScope.async {
                kotlin.runCatching {
                    ForumScraper().run()// TODO
                }
                    .onFailure { Timber.e(it) }
                    .getOrThrow()
            }

            val discordJob = GlobalScope.async {
                kotlin.runCatching {
                    DiscordReader().readAllMessages()
                }
                    .onFailure { Timber.e(it) }
                    .getOrThrow()
            }

            runBlocking {
                val forumMods = scrapeJob.await() ?: emptyList()
                val discordMods = discordJob.await() ?: emptyList()

                ModMerger()
                    .merge(forumMods + discordMods)
                    .onEach { println(it.toString()) }
                    .run {
                        println("Saving ${this.count()} mods to ${ModRepoCache.location.toAbsolutePath()}")
                        modRepoCache.items = this
                        modRepoCache.lastUpdated = Instant.now().truncatedTo(ChronoUnit.MINUTES).toString()
                    }

                delay(1000)
            }
        }
    }
}