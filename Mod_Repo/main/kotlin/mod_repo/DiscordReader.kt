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

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import timber.ktx.Timber
import utilities.asList
import utilities.exists
import utilities.prefer
import java.util.*
import kotlin.io.path.bufferedReader

/**
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
internal class DiscordReader {
    private val baseUrl = "https://discord.com/api"
    val serverId = "187635036525166592"
    val modUpdatesChannelId = "825068217361760306"
    private val urlFinderRegex = Regex(
        """(http|ftp|https):\/\/([\w_-]+(?:(?:\.[\w_-]+)+))([\w.,@?^=%&:\/~+#-]*[\w@?^=%&\/~+#-])"""
    )

    internal suspend fun readAllMessages(): List<ScrapedMod>? {
        val props = getProperties() ?: return null
        val authToken = props["auth_token"]?.toString() ?: run {
            Timber.w { "No auth token found in ${Main.configFilePath}." }
            return@readAllMessages null
        }
        val httpClient =
            HttpClient(CIO) {
                install(Logging)
                install(HttpTimeout)
                install(JsonFeature) {
                    serializer = GsonSerializer()
                }
                this.followRedirects = true
            }

        println("Scraping Discord's #mod_updates...")
        return getMessages(httpClient, authToken)
            .map { message ->
                val link = message.content?.lines()
                    ?.mapNotNull { urlFinderRegex.find(it)?.value }
                    ?.prefer { it.contains("fractalsoftworks") }
                    ?.prefer { it.contains("/releases/download") }
                    ?.prefer { it.contains("/releases") }
                    ?.prefer { it.contains("dropbox") }
                    ?.prefer { it.contains("drive.google") }
                    ?.prefer { it.contains("patreon") }
                    ?.prefer { it.contains("bitbucket") }
                    ?.prefer { it.contains("github") }
                    ?.firstOrNull()
                    ?.let { kotlin.runCatching { Url(it) }.onFailure { Timber.w(it) }.getOrNull() }
                ScrapedMod(
                    name = message.content?.lines()?.firstOrNull()?.removeSurroundingMarkdown() ?: "(Discord Mod)",
                    description = message.content,
                    gameVersionReq = "",
                    authors = message.author?.username ?: "",
                    authorsList = message.author?.username.asList(),
                    forumPostLink = link,
                    link = link,
                    discordMessageLink = Url("https://discord.com/channels/$serverId/$modUpdatesChannelId/${message.id}"),
                    source = ModSource.Discord,
                    sources = listOf(ModSource.Discord),
                    categories = emptyList()
                )
            }
            .map { cleanUpMod(it) }
            .filter { it.name.isNotBlank() } // Remove any posts that don't contain text.
    }

    private val markdownStyleSymbols = "_*~`"
    private val surroundingMarkdownRegex = Regex("""^[$markdownStyleSymbols](.*)[$markdownStyleSymbols]$""")

    private fun String.removeSurroundingMarkdown(): String {
        var str = this

        while (true) {
            val replaced = surroundingMarkdownRegex.matchEntire(str)?.groupValues?.get(1) ?: str
            if (str == replaced) return str
            str = replaced
        }
    }

    private fun getProperties() =
        if (kotlin.runCatching { Main.configFilePath.exists() }
                .onFailure { Timber.w(it) }
                .getOrNull() == true)
            Properties().apply { this.load(Main.configFilePath.bufferedReader()) }
        else {
            Timber.w { "Unable to find ${Main.configFilePath}." }
            null
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
            val newMessages = httpClient.request<List<Message>>("$baseUrl/channels/$modUpdatesChannelId/messages") {
                parameter("limit", perRequestLimit.toString())
                header("Authorization", "Bot $authToken")
                accept(ContentType.Application.Json)

                if (messages.isNotEmpty()) {
                    // Grab results from before the oldest message we've gotten so far.
                    parameter("before", messages.last().id)
                }
            }
                .sortedBy { it.timeStamp }

            Timber.i { "Found ${newMessages.count()} posts in Discord #mod_updates." }
            messages += newMessages
            runs++

            if (newMessages.isEmpty() || newMessages.size < perRequestLimit) {
                Timber.i { "Found all ${messages.count()} posts in Discord #mod_updates, finishing scrape." }
                break
            } else {
                delay(200)
            }
        }

        return messages
    }

    private val discordUnrecognizedEmojiRegex = Regex("""(<:.+?:.+?>)""")

    private fun cleanUpMod(mod: ScrapedMod): ScrapedMod =
        mod.copy(
            name = mod.name.replace(discordUnrecognizedEmojiRegex, "").trim(),
            description = mod.description?.replace(discordUnrecognizedEmojiRegex, "")?.trim(),
        )

    internal data class Message(
        val id: String,
        val author: User?,
        val content: String?,
        val timeStamp: Date?,
        val edited_timestamp: Date?,
        val attachments: List<Attachment>?,
        val embeds: List<Embed>?,
    )

    internal data class User(
        val id: String,
        val username: String?,
        val discriminator: String?,
    )

    internal data class Attachment(
        val id: String,
        val filename: String?,
        val description: String?,
        val content_type: String?,
        val size: Int?,
        val url: String?,
        val proxy_url: String?
    )

    internal data class Embed(
        val title: String?,
        val description: String?,
        val url: String?
    )
}