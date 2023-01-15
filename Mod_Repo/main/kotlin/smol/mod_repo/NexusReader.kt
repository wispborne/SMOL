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

import com.google.gson.annotations.SerializedName
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import smol.timber.ktx.Timber
import smol.utilities.asList
import smol.utilities.nullIfBlank
import java.time.Instant
import java.util.*

internal object NexusReader {
    val baseUrl = "https://api.nexusmods.com"
    val gameId = "starsector"
    val websiteBaseUrl = "https://www.nexusmods.com/$gameId/mods"

    suspend fun readAllMessages(config: Main.Companion.Config): List<ScrapedMod>? {
        val authToken = config.nexusApiToken ?: run {
            Timber.w { "No NexusMods auth token found in ${Main.configFilePath}." }
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

        val gameInfo = kotlin.runCatching {
            getGameInfo(httpClient = httpClient, authToken = authToken)
        }
            .onFailure { Timber.w(it) }
            .getOrNull()

        val mods = mutableListOf<ScrapedMod>()
        // Once there are over 1000 mods on NexusMods, update this lol.
        // Rate limit is 2,500 requests per 24 hours.
        for (modId in (1 until 1000)) {
            try {
                getModById(
                    httpClient = httpClient,
                    modId = modId,
                    categories = gameInfo?.categories ?: emptyList(),
                    authToken = authToken
                )
                    ?.also { mods += it }
                    ?.also { Timber.v { it.toString() } }
                    ?.also { Timber.i { it.name } }
            } catch (e: Exception) {
                Timber.w(e)
                break
            }
        }

        return mods
    }


    private suspend fun getGameInfo(httpClient: HttpClient, authToken: String): GameInfo {
        return httpClient.request("$baseUrl/v1/games/$gameId.json") {
            header("apikey", authToken)
            accept(ContentType.Application.Json)
        }.body()
    }

    private suspend fun getModById(
        httpClient: HttpClient,
        modId: Int,
        categories: List<Category>,
        authToken: String
    ): ScrapedMod? {
        return httpClient.request("$baseUrl/v1/games/$gameId/mods/$modId.json") {
            header("apikey", authToken)
            accept(ContentType.Application.Json)
            timeout {
                this.connectTimeoutMillis = 5000
                this.requestTimeoutMillis = 5000
                this.socketTimeoutMillis = 5000
            }
        }.body<NexusMod>()
            .let { mod ->
                if (mod.available != true) {
                    return@let null
                }

                val author = mod.author?.nullIfBlank() ?: mod.uploadedBy?.nullIfBlank() ?: mod.user?.name ?: ""
                val nexusModsUrl = kotlin.runCatching { getWebLinkForModId(modId) }.getOrNull()
                ScrapedMod(
                    name = mod.name ?: "(no name)",
                    summary = mod.summary,
                    description = mod.description,
                    modVersion = mod.version,
                    gameVersionReq = null,
                    authors = author,
                    authorsList = author.asList(),
                    link = nexusModsUrl,
                    forumPostLink = null,
                    urls = nexusModsUrl?.let { mapOf(ModUrlType.NexusMods to nexusModsUrl) } ?: emptyMap(),
                    source = ModSource.NexusMods,
                    sources = ModSource.NexusMods.asList(),
                    categories = (mod.categoryId?.let { categories.getOrNull(it) }?.name)?.asList(),
                    images = mod.pictureUrl?.nullIfBlank()
                        ?.let {
                            mapOf(
                                "banner" to Image(
                                    id = it,
                                    filename = it,
                                    description = null,
                                    content_type = null,
                                    size = null,
                                    url = it,
                                    proxy_url = null
                                )
                            )
                        }
                        ?: emptyMap(),
                    dateTimeCreated = kotlin.runCatching { Date.from(Instant.parse(mod.createdTime)) }
                        .onFailure { Timber.w(it) }.getOrNull(),
                    dateTimeEdited = kotlin.runCatching { Date.from(Instant.parse(mod.updatedTime)) }
                        .onFailure { Timber.w(it) }.getOrNull(),
                )
            }
    }

    private fun getWebLinkForModId(modId: Int) = Url("$websiteBaseUrl/$modId")

    private data class GameInfo(
        @SerializedName("id") val id: Int?,
        @SerializedName("name") val name: String?,
        @SerializedName("forum_url") val forumUrl: String?,
        @SerializedName("nexusmods_url") val nexusmodsUrl: String?,
        @SerializedName("genre") val genre: String?,
        @SerializedName("file_count") val fileCount: Int?,
        @SerializedName("downloads") val downloads: Int?,
        @SerializedName("domain_name") val domainName: String?,
        @SerializedName("approved_date") val approvedDate: Int?,
        @SerializedName("file_views") val fileViews: Int?,
        @SerializedName("authors") val authors: Int?,
        @SerializedName("file_endorsements") val fileEndorsements: Int?,
        @SerializedName("mods") val mods: Int?,
        @SerializedName("categories") val categories: List<Category>
    )

    private data class Category(
        @SerializedName("category_id") val categoryId: String?,
        @SerializedName("name") val name: String?,
        @SerializedName("parent_category") val parentCategory: String?
    )

    private data class NexusMod(
        @SerializedName("name") val name: String?,
        @SerializedName("summary") val summary: String?,
        @SerializedName("description") val description: String?,
        @SerializedName("picture_url") val pictureUrl: String?,
        @SerializedName("mod_id") val modId: Int?,
        @SerializedName("allow_rating") val allowRating: Boolean?,
        @SerializedName("domain_name") val domainName: String?,
        @SerializedName("category_id") val categoryId: Int?,
        @SerializedName("version") val version: String?,
        @SerializedName("endorsement_count") val endorsementCount: Int?,
        @SerializedName("created_timestamp") val createdTimestamp: Long?,
        @SerializedName("created_time") val createdTime: String?,
        @SerializedName("updated_timestamp") val updatedTimestamp: Int?,
        @SerializedName("updated_time") val updatedTime: String?,
        @SerializedName("author") val author: String?,
        @SerializedName("uploaded_by") val uploadedBy: String?,
        @SerializedName("uploaded_users_profile_url") val uploadedUsersProfileUrl: String?,
        @SerializedName("contains_adult_content") val containsAdultContent: Boolean?,
        @SerializedName("status") val status: String?,
        @SerializedName("available") val available: Boolean?,
        @SerializedName("user") val user: User?,
        @SerializedName("endorsement") val endorsement: Endorsement?
    )

    private data class User(
        @SerializedName("member_id") val memberId: Int?,
        @SerializedName("member_group_id") val memberGroupId: Int?,
        @SerializedName("name") val name: String?
    )

    private data class Endorsement(
        @SerializedName("endorse_status") val endorseStatus: String?,
        @SerializedName("timestamp") val timestamp: Int?,
        @SerializedName("version") val version: String?

    )
}