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
import io.ktor.client.statement.*
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
    private const val delayBetweenRequestsMillis = 40L // Allowed to do 50 requests per second
    private var timestampOfLastHttpCall: Long = 0L
    private const val serverId = "187635036525166592"

    @Deprecated("Moved to forums")
    private const val modUpdatesChannelId = "1104110077075542066"
    private const val modUpdatesForumChannelId = "1115946075262550016"
    private val urlFinderRegex = Regex(
        """(http|ftp|https):\/\/([\w_-]+(?:(?:\.[\w_-]+)+))([\w.,@?^=%&:\/~+#-]*[\w@?^=%&\/~+#-])"""
    )
    private const val noscrapeReaction = "\uD83D\uDD78️"
    var apiCallsLastRun = 0

    class DownloadyUrl(val url: String, val isDownloadable: Boolean)

    internal suspend fun readAllMessages(config: Main.Companion.Config, gsonBuilder: GsonBuilder): List<ScrapedMod>? {
        apiCallsLastRun = 0

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

        val modUpdatesChannel = getChannel(
            channelId = modUpdatesForumChannelId,
            httpClient = httpClient,
            authToken = authToken
        )

        val categoriesLookup = modUpdatesChannel.available_tags.orEmpty().associate { it.id to it.name }

        return getThreads(
            channelId = modUpdatesForumChannelId,
            httpClient = httpClient,
            authToken = authToken,
            getFullChannelInfo = true
        )
            .map { thread ->
                // For threads, take the first 100 messages of the thread.
                getMessages(channelId = thread.id, thread.name, httpClient, authToken, limit = 100)
                    .map { it.copy(parentThread = thread) }
            }
            .also { Timber.i { "Checking for mods with $noscrapeReaction reactions." } }
            .filter { msgs ->
                msgs.forEach { msg ->
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
                }

                return@filter true
            }
            .also { Timber.i { "Done checking reactions." } }
            .parallelMap { message ->
                return@parallelMap if (message.count() == 1 && !message.first().isInThread()) {
                    parseAsSingleMessage(message.first())
                } else {
                    parseAsThread(message, categoriesLookup)
                }
            }
            .mapNotNull { it?.let { cleanUpMod(it) } }
            .filter { it.name.isNotBlank() } // Remove any posts that don't contain text.
            .also { posts -> Timber.i { "Found ${posts.size} Discord mods. API calls used: $apiCallsLastRun." } }
    }

    private suspend fun parseAsSingleMessage(
        message: Message
    ): ScrapedMod {
        Timber.i { "Parsing message ${message.content?.lines()?.firstOrNull()}" }
        // Drop any blank lines from the start of the post.
        val name = message.content?.lines().orEmpty()
            .dropWhile { it.isBlank() }
            .firstOrNull()
            ?.removeMarkdownFromName()

        val messageLines = message.content?.lines().orEmpty()
            .let { it.dropWhile { it.isBlank() }.drop(1) }
            .dropWhile { it.isBlank() }

        val (forumUrl, downloadArtifactUrl, downloadPageUrl) = getUrlsFromMessage(messageLines)

        return ScrapedMod(
            name = name ?: "",
            summary = messageLines.take(2).joinToString(separator = "\n"),
            description = messageLines.joinToString(separator = "\n"),
            modVersion = null,
            gameVersionReq = "",
            authors = message.author?.username ?: "",
            authorsList = message.author?.username.asList(),
            forumPostLink = forumUrl,
            link = downloadArtifactUrl ?: forumUrl,
            urls = listOfNotNull(
                forumUrl?.let { ModUrlType.Forum to forumUrl },
                ModUrlType.Discord to
                        Url("https://discord.com/channels/$serverId/$modUpdatesForumChannelId/${message.id}"),
                downloadArtifactUrl?.let { ModUrlType.DirectDownload to downloadArtifactUrl },
                downloadPageUrl?.let { ModUrlType.DownloadPage to downloadPageUrl },
            ).toMap(),
            source = ModSource.Discord,
            sources = listOf(ModSource.Discord),
            categories = emptyList(), // message.parentThread?.applied_tags.orEmpty().mapNotNull { categoriesLookup[it] },
            images = getImagesFromMessage(message),
            dateTimeCreated = message.timestamp,
            dateTimeEdited = message.edited_timestamp
        )
    }

    private suspend fun parseAsThread(
        messages: List<Message>,
        categoriesLookup: Map<String, String?>
    ): ScrapedMod? {
        if (messages.isEmpty()) return null

        val messagesOrdered = messages.sortedBy { it.timestamp }
        val message = messagesOrdered.first { !it.content.isNullOrBlank() }
        Timber.i { "Parsing message ${message.content?.lines()?.firstOrNull()}" }
        // Drop any blank lines from the start of the post.
        // Use thread title for mod name if it's a thread.
        val name = message.parentThread?.name?.removeMarkdownFromName()

        val messageLines = message.content?.lines().orEmpty()
            .dropWhile { it.isBlank() }
        val allMessageLines = messagesOrdered.flatMap { it.content?.lines().orEmpty() }
            .dropWhile { it.isBlank() }

        val (forumUrl, downloadArtifactUrl, downloadPageUrl) = getUrlsFromMessage(allMessageLines)

        return ScrapedMod(
            name = name ?: "",
            summary = messageLines.take(2).joinToString(separator = "\n"),
            description = messageLines.joinToString(separator = "\n"),
            modVersion = null,
            gameVersionReq = "",
            authors = message.author?.username ?: "",
            authorsList = message.author?.username.asList(),
            forumPostLink = forumUrl,
            link = downloadArtifactUrl ?: forumUrl,
            urls = listOfNotNull(
                forumUrl?.let { ModUrlType.Forum to forumUrl },
                ModUrlType.Discord to
                        Url("https://discord.com/channels/$serverId/${message.parentThread?.id}/${message.id}"),
                downloadArtifactUrl?.let { ModUrlType.DirectDownload to downloadArtifactUrl },
                downloadPageUrl?.let { ModUrlType.DownloadPage to downloadPageUrl },
            ).toMap(),
            source = ModSource.Discord,
            sources = listOf(ModSource.Discord),
            categories = message.parentThread?.applied_tags.orEmpty().mapNotNull { categoriesLookup[it] },
            images = messagesOrdered.map { getImagesFromMessage(it) }
                .reduceOrNull { acc, map -> acc.orEmpty().plus(map.orEmpty()) },
            dateTimeCreated = message.timestamp,
            dateTimeEdited = message.edited_timestamp
        )
    }

    private suspend fun getUrlsFromMessage(messageLines: List<String>): Triple<Url?, Url?, Url?> {
        val forumUrl = messageLines
            .mapNotNull { urlFinderRegex.find(it)?.value }
            .firstOrNull { it.contains("fractalsoftworks") }
            ?.let { kotlin.runCatching { Url(it) }.onFailure { Timber.w(it) }.getOrNull() }

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
        return Triple(forumUrl, downloadArtifactUrl, downloadPageUrl)
    }

    private fun getImagesFromMessage(message: Message) = message.attachments
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

    private suspend fun getChannel(
        channelId: String,
        httpClient: HttpClient,
        authToken: String
    ): Channel {
        val channel = makeHttpRequestWithRateLimiting(httpClient) {
            httpClient.get("$baseUrl/channels/$channelId") {
                header("Authorization", "Bot $authToken")
                accept(ContentType.Application.Json)
            }
                .also {
                    Timber.v { it.bodyAsText() }
                }
        }.body<Channel>()

        Timber.i { "Found ${channel.name} in Discord." }

        return channel
    }

    /**
     * @param getFullChannelInfo If true, will make a second request for each Thread to get the full channel info, including tags.
     */
    private suspend fun getThreads(
        channelId: String,
        httpClient: HttpClient,
        authToken: String,
        getFullChannelInfo: Boolean = false,
        includeArchived: Boolean = true
    ): List<Channel> {
        val threads = mutableListOf<Channel>()

        val allThreads = run {
            // Active threads
            makeHttpRequestWithRateLimiting(httpClient) {
                httpClient.get("$baseUrl/guilds/$serverId/threads/active") {
                    header("Authorization", "Bot $authToken")
                    accept(ContentType.Application.Json)
                }
                    .also {
                        Timber.v { it.bodyAsText() }
                    }
            }.body<Threads>()
                .threads
        }
            .plus(
                run {
                    // Archived threads
                    if (!includeArchived) emptyList()
                    else makeHttpRequestWithRateLimiting(httpClient) {
                        httpClient.get("$baseUrl/channels/$channelId/threads/archived/public") {
                            header("Authorization", "Bot $authToken")
                            accept(ContentType.Application.Json)
                        }
                            .also {
                                Timber.v { it.bodyAsText() }
                            }
                    }.body<Threads>()
                        .threads
                }
            )
            .filter { it.parent_id == channelId }
            .sortedByDescending { it.timestamp }

        Timber.i { "Found ${allThreads.count()} active and archived threads in Discord #mod_updates." }
        threads += allThreads
        Timber.v { allThreads.joinToString(separator = "\n") }

        val channels = if (getFullChannelInfo) {
            Timber.i { "Getting full channel info for ${allThreads.count()} threads." }
            allThreads.map { getChannel(it.id, httpClient, authToken) }
                .also { Timber.v { it.joinToString(separator = "\n") } }
        } else allThreads

        return channels
    }

    private suspend fun getMessages(
        channelId: String,
        channelName: String?,
        httpClient: HttpClient,
        authToken: String,
        limit: Int = Int.MAX_VALUE
    ): List<Message> {
        val messages = mutableListOf<Message>()
        var runs = 0 // Limit number of runs in case other breaks don't trigger.
        val perRequestLimit = 100

        while (messages.count() < limit && runs < 25) {
            val newMessages = makeHttpRequestWithRateLimiting(httpClient) {
                httpClient.get("$baseUrl/channels/$channelId/messages") {
                    parameter("limit", perRequestLimit.toString())
                    header("Authorization", "Bot $authToken")
                    accept(ContentType.Application.Json)

                    if (messages.isNotEmpty()) {
                        // Grab results from before the oldest message we've gotten so far.
                        parameter("before", messages.last().id)
                    }
                }
                    .also {
                        Timber.v { it.bodyAsText() }
                    }
            }.body<List<Message>>()
//                .run { jsanity.fromJson<List<Message>>(this, shouldStripComments = false) }
                // Reverse chronological order, last message first.
                .sortedByDescending { it.timestamp }

            Timber.i { "Found ${newMessages.count()} posts in Discord channel $channelName." }
            messages += newMessages
            Timber.v { newMessages.joinToString(separator = "\n") }
            runs++

            if (newMessages.isEmpty() || newMessages.size < perRequestLimit) {
                Timber.i { "Found all ${messages.count()} posts in channel $channelName." }
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
            httpClient.get("$baseUrl/channels/$modUpdatesForumChannelId/messages/$messageId/reactions/$emoji") {
                header("Authorization", "Bot $authToken")
                accept(ContentType.Application.Json)
            }.body()
        }
    }

    private suspend fun <T> makeHttpRequestWithRateLimiting(
        client: HttpClient,
        call: suspend (client: HttpClient) -> T
    ): T {
        apiCallsLastRun++
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

    private data class Channel(
        val id: String,
        val parent_id: String?,
        val name: String?,
        val timestamp: Date?,
        val last_message_id: String?,
        val owner_id: String?,
        val message_count: Int?,
        val available_tags: List<Tag>?,
        val applied_tags: List<String>?
    )

    private data class Threads(
        val threads: List<Channel>
    )

    private data class Tag(
        val id: String,
        val name: String?
    )

    private data class Message(
        val id: String,
        val author: User?,
        val content: String?,
        val timestamp: Date?,
        val edited_timestamp: Date?,
        val attachments: List<Attachment>?,
        val embeds: List<Embed>?,
        val reactions: List<Reaction>?,
        val parentThread: Channel? // Not part of the Discord API, added by me afterwards.
    ) {
        fun isInThread() = parentThread != null
    }

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