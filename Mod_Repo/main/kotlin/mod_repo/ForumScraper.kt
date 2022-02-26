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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import timber.ktx.Timber

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
class ForumScraper {

    fun run(): List<ScrapedMod>? {
        return (scrapeModIndexLinks() ?: emptyList())
            .plus(scrapeModdingForumLinks() ?: emptyList())
            .plus(scrapeModForumLinks() ?: emptyList())
            .ifEmpty { null }
    }

    internal fun scrapeModIndexLinks(): List<ScrapedMod>? {
        Timber.i { "Scraping Mod Index..." }
        return kotlin.runCatching {
            val doc: Document = Jsoup.connect("https://fractalsoftworks.com/forum/index.php?topic=177.0").get()
//        Jsoup.parse(
//            Path.of("C:/Users/whitm/SMOL/web/Starsector_Index/fractalsoftworks.com/forum/indexebd2.html").toFile(), null
//        )
            val categories: Elements = doc.select("ul.bbc_list")

            categories
                .flatMap { categoryElement ->
                    val category =
                        categoryElement.previousElementSibling()?.previousElementSibling()?.previousElementSibling()
                            ?.text()
                            ?.trimEnd(':') ?: ""

                    categoryElement.select("li").map { modElement ->
                        val link = modElement.select("a.bbc_link")

                        ScrapedMod(
                            name = link.text(),
                            description = null,
                            gameVersionReq = modElement.select("strong span").text(),
                            authors = modElement.select("em strong").text(),
                            forumPostLink = link.attr("href").ifBlank { null }?.let { Url(it) },
                            discordMessageLink = null,
                            source = ModSource.Index,
                            categories = listOf(category)
                        )
                    }
                }
        }
            .onFailure { Timber.w(it) }
            .getOrNull()
    }

    internal fun scrapeModdingForumLinks(): List<ScrapedMod>? {
        Timber.i { "Scraping Modding Forum..." }
        return scrapeSubforumLinks(
            forumBaseUrl = Main.FORUM_BASE_URL,
            subforumNumber = 3
        )
    }

    internal fun scrapeModForumLinks(): List<ScrapedMod>? {
        Timber.i { "Scraping Mod Forum..." }
        return scrapeSubforumLinks(
            forumBaseUrl = Main.FORUM_BASE_URL,
            subforumNumber = 8
        )
    }

    private fun scrapeSubforumLinks(forumBaseUrl: String, subforumNumber: Int): List<ScrapedMod>? {
        return kotlin.runCatching {
            (0 until 80 step 20)
                .flatMap { page ->
                    val doc: Document = Jsoup.connect("$forumBaseUrl?board=$subforumNumber.$page").get()
                    val posts: Elements = doc.select("#messageindex tr")
                    val versionRegex = Regex("""[\[{](.*?\d.*?)[]}]""")

                    posts
                        .map { postElement ->
                            val titleLinkElement = postElement.select("td.subject span a")
                            val authorLinkElement = postElement.select("td.starter a")

                            ScrapedMod(
                                name = titleLinkElement.text().replace(versionRegex, "").trim(),
                                description = null,
                                gameVersionReq = versionRegex.find(titleLinkElement.text())?.groupValues?.getOrNull(1)
                                    ?.trim()
                                    ?: "",
                                authors = authorLinkElement.text(),
                                forumPostLink = titleLinkElement.attr("href").ifBlank { null }?.let { Url(it) },
                                discordMessageLink = null,
                                source = ModSource.ModdingSubforum,
                                categories = emptyList()
                            )
                        }
                        .filter { it.gameVersionReq.isNotEmpty() }
                        .filter { !it.name.contains("MOVED", ignoreCase = true) }
                }
        }
            .onFailure { Timber.w(it) }
            .getOrNull()
    }
}