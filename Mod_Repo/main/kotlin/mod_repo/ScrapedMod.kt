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

/**
 * @param authors The higher prioritized author name (ie from Index).
 */
data class ScrapedMod(
    val name: String,
    val description: String?,
    val gameVersionReq: String?,
    val authors: String,
    val authorsList: List<String>?,
    val link: Url?,
    @Deprecated("Use `link` instead, as the mod isn't necessarily on the forum.") val forumPostLink: Url?,
    val discordMessageLink: Url?,
    @Deprecated("Use `sources` because similar mods are now merged.")
    val source: ModSource?,
    val sources: List<ModSource>?,
    val categories: List<String>?
) {
    fun authors() = authorsList.orEmpty()
    fun authorsWithAliases() = authorsList.orEmpty()
        .flatMap { ModRepoUtils.getOtherMatchingAliases(it) }
        .distinct()

    fun sources() = sources.orEmpty()
}

enum class ModSource {
    Index,
    ModdingSubforum,
    Discord
}