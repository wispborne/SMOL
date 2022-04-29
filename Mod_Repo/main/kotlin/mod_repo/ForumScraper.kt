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
import io.ktor.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import timber.ktx.Timber
import utilities.asList

private const val i = 15

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
internal object ForumScraper {
    private val postsPerPage = 20

    fun run(
        config: Main.Companion.Config,
        moddingForumPagesToScrape: Int,
        modForumPagesToScrape: Int
    ): List<ScrapedMod>? {
        return (scrapeModIndexLinks() ?: emptyList())
            .plus(runBlocking { scrapeModdingForumLinks(moddingForumPagesToScrape) } ?: emptyList())
            .plus(runBlocking { scrapeModForumLinks(modForumPagesToScrape) } ?: emptyList())
            .ifEmpty { null }
    }

    private fun scrapeModIndexLinks(): List<ScrapedMod>? {
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

                        val forumPostLink = link.attr("href").ifBlank { null }?.let { Url(it) }?.cleanForumUrl()
                        ScrapedMod(
                            name = link.text(),
                            summary = null,
                            description = null,
                            modVersion = null,
                            gameVersionReq = modElement.select("strong span").text(),
                            authors = modElement.select("em strong").text(),
                            authorsList = modElement.select("em strong").text().asList(),
                            forumPostLink = forumPostLink,
                            link = forumPostLink,
                            urls = listOfNotNull(
                                forumPostLink?.let { ModUrlType.Forum to forumPostLink }
                            ).toMap(),
                            source = ModSource.Index,
                            sources = listOf(ModSource.Index),
                            categories = listOf(category),
                            images = emptyMap(),
                            dateTimeCreated = null,
                            dateTimeEdited = null,
                        )
                    }
                }
        }
            .onFailure { Timber.w(it) }
            .getOrNull()
    }

    private suspend fun scrapeModdingForumLinks(moddingForumPagesToScrape: Int): List<ScrapedMod>? {
        Timber.i { "Scraping Modding Forum..." }
        return scrapeSubforumLinks(
            forumBaseUrl = Main.FORUM_BASE_URL,
            subforumNumber = 3,
            take = postsPerPage * moddingForumPagesToScrape
        )
    }

    private suspend fun scrapeModForumLinks(modForumPagesToScrape: Int): List<ScrapedMod>? {
        Timber.i { "Scraping Mod Forum..." }
        return scrapeSubforumLinks(
            forumBaseUrl = Main.FORUM_BASE_URL,
            subforumNumber = 8,
            take = postsPerPage * modForumPagesToScrape
        )
    }

    private fun Url?.cleanForumUrl() =
        this?.copy(
            parameters = parameters
                .filter { key, _ -> !key.equals("PHPSESSID", ignoreCase = true) }
                .let { Parameters.build { appendAll(it) } })

    private suspend fun scrapeSubforumLinks(forumBaseUrl: String, subforumNumber: Int, take: Int): List<ScrapedMod>? {
        return kotlin.runCatching {
            (0 until take step 20)
                .flatMap { page ->
                    Timber.i { "Fetching page ${page / postsPerPage} from subforum $subforumNumber." }
                    val doc: Document = Jsoup.connect("$forumBaseUrl?board=$subforumNumber.$page").get()
                    val posts: Elements = doc.select("#messageindex tr")
                    val versionRegex = Regex("""[\[{](.*?\d.*?)[]}]""")

                    posts
                        .map { postElement ->
                            val titleLinkElement = postElement.select("td.subject span a")
                            val authorLinkElement = postElement.select("td.starter a")

                            val forumPostLink = titleLinkElement.attr("href").ifBlank { null }?.let { Url(it) }.cleanForumUrl()
                            ScrapedMod(
                                name = titleLinkElement.text().replace(versionRegex, "").trim(),
                                summary = null,
                                description = null,
                                modVersion = null,
                                gameVersionReq = versionRegex.find(titleLinkElement.text())?.groupValues?.getOrNull(1)
                                    ?.trim()
                                    ?: "",
                                authors = authorLinkElement.text(),
                                authorsList = authorLinkElement.text().asList(),
                                forumPostLink = forumPostLink,
                                link = forumPostLink,
                                urls = listOfNotNull(
                                    forumPostLink?.let { ModUrlType.Forum to forumPostLink }
                                ).toMap(),
                                source = ModSource.ModdingSubforum,
                                sources = listOf(ModSource.ModdingSubforum),
                                categories = emptyList(),
                                images = emptyMap(),
                                dateTimeCreated = null,
                                dateTimeEdited = null,
                            )
                        }
                        .filter { !it.gameVersionReq.isNullOrBlank() }
                        .filter { !it.name.contains("MOVED", ignoreCase = true) }
                        .also {
                            Timber.i { "Found ${it.count()} mods on page ${page / postsPerPage} of subforum $subforumNumber." }
                        }
                        .also {
                            delay(200)
                        }
                }
        }
            .onFailure { Timber.w(it) }
            .getOrNull()
    }
}