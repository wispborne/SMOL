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

import io.ktor.http.*
import java.time.ZonedDateTime

/**
 * @param authors The higher prioritized author name (ie from Index).
 */
data class ScrapedMod(
    val name: String,
    val summary: String?,
    val description: String?,
    val modVersion: String?,
    val gameVersionReq: String?,
    @Deprecated("Use `authorsList` instead.")
    internal val authors: String,
    internal val authorsList: List<String>?,
    @Deprecated("Use `urls` instead.")
    val link: Url?,
    @Deprecated("Use `urls` instead.")
    val forumPostLink: Url?,
    internal val urls: Map<ModUrlType, Url>?,
    @Deprecated("Use `sources` because similar mods are now merged.")
    internal val source: ModSource?,
    internal val sources: List<ModSource>?,
    internal val categories: List<String>?,
    internal val images: Map<String, Image>?,
    val dateTimeCreated: ZonedDateTime?,
    val dateTimeEdited: ZonedDateTime?,
) {
    fun authors() = authorsList.orEmpty()
    fun authorsWithAliases() = authorsList.orEmpty()
        .flatMap { ModRepoUtils.getOtherMatchingAliases(it) }
        .distinct()

    fun categories() = categories.orEmpty()
    fun sources() = sources.orEmpty()
    fun images() = images.orEmpty()
    fun urls() = urls.orEmpty()
}

enum class ModSource {
    Index,
    ModdingSubforum,
    Discord,
    NexusMods,
}

enum class ModUrlType {
    Forum,
    Discord,
    NexusMods,
    Download
}

data class Image(
    val id: String,
    val filename: String?,
    val description: String?,
    val content_type: String?,
    val size: Int?,
    val url: String?,
    val proxy_url: String?
)