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
import timber.ktx.Timber
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
    private val urlFinderRegex = Regex(
        """(http|ftp|https):\/\/([\w_-]+(?:(?:\.[\w_-]+)+))([\w.,@?^=%&:\/~+#-]*[\w@?^=%&\/~+#-])"""
    )

    suspend internal fun readAllMessages(): List<ScrapedMod>? {
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
                ScrapedMod(
                    name = message.content?.lines()?.firstOrNull()?.trim('*', '_') ?: "(Discord Mod)",
                    description = message.content,
                    gameVersionReq = "",
                    authors = message.author?.username ?: "",
                    forumPostLink = message.content?.lines()
                        ?.mapNotNull { urlFinderRegex.find(it)?.value }
                        ?.prefer { it.contains("fractal") }
                        ?.firstOrNull()
                        ?.let { kotlin.runCatching { Url(it) }.onFailure { Timber.w(it) }.getOrNull() },
                    source = ModSource.Discord,
                    categories = emptyList()
                )
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

    private suspend fun getMessages(httpClient: HttpClient, authToken: String): List<Message> {
        val modUpdatesId = "825068217361760306"

        return httpClient.request<List<Message>>("$baseUrl/channels/$modUpdatesId/messages") {
            parameter("limit", "100")
            header("Authorization", "Bot $authToken")
            accept(ContentType.Application.Json)
        }
    }

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