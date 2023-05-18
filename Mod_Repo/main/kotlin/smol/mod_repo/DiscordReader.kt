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

import com.google.gson.GsonBuilder
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import kotlinx.coroutines.delay
import smol.timber.ktx.Timber
import smol.utilities.asList
import smol.utilities.parallelMap
import smol.utilities.prefer
import java.time.Instant
import java.util.*

internal object DiscordReader {
    private const val baseUrl = "https://discord.com/api"
    private const val delayBetweenRequestsMillis = 1500L
    private var timestampOfLastHttpCall: Long = 0L
    private const val serverId = "187635036525166592"
    private const val modUpdatesChannelId = "1104110077075542066"
    private val urlFinderRegex = Regex(
        """(http|ftp|https):\/\/([\w_-]+(?:(?:\.[\w_-]+)+))([\w.,@?^=%&:\/~+#-]*[\w@?^=%&\/~+#-])"""
    )
    private const val noscrapeReaction = "\uD83D\uDD78️"

    internal suspend fun readAllMessages(config: Main.Companion.Config, gsonBuilder: GsonBuilder): List<ScrapedMod>? {
        val authToken = config.discordAuthToken ?: run {
            Timber.w { "No auth token found in ${Main.configFilePath}." }
            return@readAllMessages null
        }

        val httpClient =
            HttpClient(CIO) {
                install(Logging)
                install(HttpTimeout)
                install(ContentNegotiation) {
                    gson()
                }
                this.followRedirects = true
            }

        println("Scraping Discord's #mod_updates...")
        return getMessages(
            httpClient = httpClient,
            authToken = authToken,
            limit = if (config.lessScraping) 50 else Int.MAX_VALUE
        )
            .also { Timber.i { "Checking for mods with $noscrapeReaction reactions." } }
            .filter { msg ->
                val hasNoscrapeReaction = msg.reactions.orEmpty().any { it.emoji.name == noscrapeReaction }

                if (hasNoscrapeReaction) {
                    val isReactionFromPostAuthor = getReacters(
                        httpClient = httpClient,
                        authToken = authToken,
                        emoji = noscrapeReaction,
                        messageId = msg.id
                    ).any { reacter -> reacter.id == msg.author?.id }

                    if (isReactionFromPostAuthor) {
                        Timber.i {
                            "Skipping Discord mod '${msg.content?.lines()?.firstOrNull()}' " +
                                    "because of reaction $noscrapeReaction."
                        }

                        return@filter false
                    }
                }

                return@filter true
            }

            .also { Timber.i { "Done checking reactions." } }
            .parallelMap { message ->
                Timber.i { "Parsing message ${message.content?.lines()?.firstOrNull()}" }
                // Drop any blank lines from the start of the post.
                val messageLines = message.content?.lines().orEmpty().dropWhile { it.isBlank() }
                val forumUrl = messageLines
                    .mapNotNull { urlFinderRegex.find(it)?.value }
                    .firstOrNull { it.contains("fractalsoftworks") }
                    ?.let { kotlin.runCatching { Url(it) }.onFailure { Timber.w(it) }.getOrNull() }

                class DownloadyUrl(val url: String, val isDownloadable: Boolean)

                // Get all urls, remove ones that definitely aren't download urls, then check each to see
                // if there's a file at the other end, as opposed to a website.
                val downloadyUrls = messageLines
                    .mapNotNull { urlFinderRegex.find(it)?.value }
                    .filter { url -> thingsThatAreNotDownloady.none { url.contains(it) } }
                    // Very slow, makes a request for each file to get headers.
                    .parallelMap { DownloadyUrl(it, Common.isDownloadable(it)) }

                val downloadArtifactUrl = downloadyUrls
                    .mapNotNull { if (it.isDownloadable) it.url else null }
                    .let { getBestPossibleDownloadHost(it) }
                    .firstOrNull()
                    ?.let { kotlin.runCatching { Url(it) }.onFailure { Timber.w(it) }.getOrNull() }

                val downloadPageUrl = downloadyUrls
                    .mapNotNull { if (it.isDownloadable) null else it.url }
                    .let { getBestPossibleDownloadHost(it) }
                    .firstOrNull()
                    ?.let { kotlin.runCatching { Url(it) }.onFailure { Timber.w(it) }.getOrNull() }
                    ?: forumUrl

                ScrapedMod(
                    name = messageLines.firstOrNull()?.removeMarkdownFromName() ?: "",
                    summary = messageLines.drop(1).take(2).joinToString(separator = "\n"),
                    description = messageLines.drop(1).joinToString(separator = "\n"),
                    modVersion = null,
                    gameVersionReq = "",
                    authors = message.author?.username ?: "",
                    authorsList = message.author?.username.asList(),
                    forumPostLink = forumUrl,
                    link = downloadArtifactUrl ?: forumUrl,
                    urls = listOfNotNull(
                        forumUrl?.let { ModUrlType.Forum to forumUrl },
                        ModUrlType.Discord to Url("https://discord.com/channels/$serverId/$modUpdatesChannelId/${message.id}"),
                        downloadArtifactUrl?.let { ModUrlType.DirectDownload to downloadArtifactUrl },
                        downloadPageUrl?.let { ModUrlType.DownloadPage to downloadPageUrl },
                    ).toMap(),
                    source = ModSource.Discord,
                    sources = listOf(ModSource.Discord),
                    categories = emptyList(),
                    images = message.attachments
                        ?.filter { it.content_type?.startsWith("image/") ?: false }
                        ?.associate {
                            it.id to Image(
                                id = it.id,
                                filename = it.filename,
                                description = it.description,
                                content_type = it.content_type,
                                size = it.size,
                                url = it.url,
                                proxy_url = it.proxy_url
                            )
                        },
                    dateTimeCreated = message.timestamp,
                    dateTimeEdited = message.edited_timestamp
                )
            }
            .map { cleanUpMod(it) }
            .filter { it.name.isNotBlank() } // Remove any posts that don't contain text.
    }

    /**
     * Taken from actual samples.
     */
    private val thingsThatAreNotDownloady =
        listOf(
            "imgur.com",
            "cdn.discordapp.com",
            "https://fractalsoftworks.com/forum/index.php?topic=",
            "http://fractalsoftworks.com/forum/index.php?topic=",
            "https://www.nexusmods.com/starsector/mods",
            "https://www.patreon.com/posts",
            "https://www.youtube.com"
        )

    private fun getBestPossibleDownloadHost(urls: List<String>) =
        urls
            .prefer { it.contains("/releases/download") }
            .prefer { it.contains("/releases") }
            .prefer { it.contains("dropbox") }
            .prefer { it.contains("drive.google") }
            .prefer { it.contains("patreon") }
            .prefer { it.contains("bitbucket") }
            .prefer { it.contains("github") }
            .prefer { it.contains("mediafire") } // ugh

    private val markdownStyleSymbols = listOf("_", "*", "~", "`")
    private val surroundingMarkdownRegexes = markdownStyleSymbols.map { symbol ->
        Regex("""(.*)[$symbol](.*)[$symbol](.*)""")
    }

    private fun String.removeMarkdownFromName(): String {
        var str = this

        while (true) {
            val replaced: String = surroundingMarkdownRegexes.fold(str) { prev, regex ->
                regex.matchEntire(prev)?.groupValues?.drop(1)?.joinToString(separator = "") ?: prev
            }

            if (str == replaced) return str
            str = replaced
        }
    }

    private suspend fun getMessages(
        httpClient: HttpClient,
        authToken: String,
        limit: Int = Int.MAX_VALUE
    ): List<Message> {
        val messages = mutableListOf<Message>()
        var runs = 0 // Limit number of runs in case other breaks don't trigger.
        val perRequestLimit = 100

        while (messages.count() < limit && runs < 25) {
            val newMessages = makeHttpRequestWithRateLimiting(httpClient) {
                httpClient.get("$baseUrl/channels/$modUpdatesChannelId/messages") {
                    parameter("limit", perRequestLimit.toString())
                    header("Authorization", "Bot $authToken")
                    accept(ContentType.Application.Json)

                    if (messages.isNotEmpty()) {
                        // Grab results from before the oldest message we've gotten so far.
                        parameter("before", messages.last().id)
                    }
                }
            }.body<List<Message>>()
//                .run { jsanity.fromJson<List<Message>>(this, shouldStripComments = false) }
                .sortedByDescending { it.timestamp }

            Timber.i { "Found ${newMessages.count()} posts in Discord #mod_updates." }
            messages += newMessages
            Timber.v { newMessages.joinToString(separator = "\n") }
            runs++

            if (newMessages.isEmpty() || newMessages.size < perRequestLimit) {
                Timber.i { "Found all ${messages.count()} posts in Discord #mod_updates." }
                break
            } else {
//                delay(delayBetweenRequestsMillis)
            }
        }

        return messages
    }

    private suspend fun getReacters(
        httpClient: HttpClient,
        authToken: String,
        emoji: String,
        messageId: String
    ): List<User> {
        return makeHttpRequestWithRateLimiting(httpClient) {
            Timber.i { "Checking to see who reacted to $messageId." }
            httpClient.get("$baseUrl/channels/$modUpdatesChannelId/messages/$messageId/reactions/$emoji") {
                header("Authorization", "Bot $authToken")
                accept(ContentType.Application.Json)
            }.body()
        }
    }

    private suspend fun <T> makeHttpRequestWithRateLimiting(
        client: HttpClient,
        call: suspend (client: HttpClient) -> T
    ): T {
        delay(((timestampOfLastHttpCall + delayBetweenRequestsMillis) - Instant.now().toEpochMilli()).coerceAtLeast(0))
        timestampOfLastHttpCall = Instant.now().toEpochMilli()
        return call(client)
    }

    private val discordUnrecognizedEmojiRegex = Regex("""(<:.+?:.+?>)""")

    private fun cleanUpMod(mod: ScrapedMod): ScrapedMod =
        mod.copy(
            name = mod.name.replace(discordUnrecognizedEmojiRegex, "").trim(),
            description = mod.description?.replace(discordUnrecognizedEmojiRegex, "")?.trim(),
        )

    private data class Message(
        val id: String,
        val author: User?,
        val content: String?,
        val timestamp: Date?,
        val edited_timestamp: Date?,
        val attachments: List<Attachment>?,
        val embeds: List<Embed>?,
        val reactions: List<Reaction>?
    )

    private data class User(
        val id: String,
        val username: String?,
        val discriminator: String?,
    )

    private data class Attachment(
        val id: String,
        val filename: String?,
        val description: String?,
        val content_type: String?,
        val size: Int?,
        val url: String?,
        val proxy_url: String?
    )

    private data class Embed(
        val title: String?,
        val description: String?,
        val url: String?
    )

    private data class Reaction(
        val count: Int,
        val emoji: Emoji
    )

    private data class Emoji(
        val id: String?,
        val name: String?,
        val user: User?
    )
}