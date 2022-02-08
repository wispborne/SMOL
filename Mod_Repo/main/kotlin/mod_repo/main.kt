package mod_repo

import com.fasterxml.jackson.databind.json.JsonMapper
import com.github.androidpasswordstore.sublimefuzzy.Fuzzy
import com.google.gson.GsonBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import utilities.Jsanity
import java.net.URI
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit

internal val CONFIG_FOLDER_DEFAULT = Path.of("")
internal val FORUM_BASE_URL = "https://fractalsoftworks.com/forum/index.php"

internal val isDebugMode = true

private fun String.prepForMatching() = this.lowercase().filter { it.isLetter() }

fun main(args: Array<String>) {
    val jsanity = Jsanity(GsonBuilder().setPrettyPrinting().create())
    val modRepoCache = ModRepoCache(jsanity)

    scrapeModIndexLinks()
        .plus(scrapeModdingForumLinks())
        .plus(scrapeModForumLinks())
        .sortedBy { it.name }
        .run {
            println("Deduplicating ${this.count()} mods...")
            val modsToSkip = mutableListOf<ScrapedMod>()

            for (outer in this) {
                if (outer in modsToSkip) {
                    continue
                }

                this.minus(outer)
                    .forEach { inner ->
                        val nameResult = Fuzzy.fuzzyMatch(outer.name.prepForMatching(), inner.name.prepForMatching())
                        val nameResultFlip =
                            Fuzzy.fuzzyMatch(inner.name.prepForMatching(), outer.name.prepForMatching())
                        val authorsResult =
                            Fuzzy.fuzzyMatch(outer.authors.prepForMatching(), inner.authors.prepForMatching())
                        val authorsResultFlip =
                            Fuzzy.fuzzyMatch(inner.authors.prepForMatching(), outer.authors.prepForMatching())

                        val isMatch =
                            (nameResult.first && authorsResult.first)
                                    || (nameResultFlip.first && authorsResultFlip.first)
                                    || (nameResult.first && authorsResultFlip.first)
                                    || (nameResultFlip.first && authorsResult.first)

                        if (isDebugMode && (nameResult.second > 0 || nameResultFlip.second > 0 || authorsResult.second > 0 || authorsResultFlip.second > 0)) {
                            println(buildString {
                                appendLine("Compared '${outer.name}' to '${inner.name}':")
                                appendLine("  '${outer.name.prepForMatching()}'<-->'${inner.name.prepForMatching()}'==>${nameResult.second}")
                                appendLine("  '${inner.name.prepForMatching()}'<-->'${outer.name.prepForMatching()}'==>${nameResultFlip.second}")
                                appendLine("  '${outer.authors.prepForMatching()}'<-->'${inner.authors.prepForMatching()}'==>${authorsResult.second}")
                                append("  '${inner.authors.prepForMatching()}'<-->'${outer.authors.prepForMatching()}'==>${authorsResultFlip.second}")
                            })
                        }

                        if (isMatch) {
                            modsToSkip.add(
                                if (outer.source == ModSource.Index) inner
                                else if (inner.source == ModSource.Index) outer
                                else inner
                            )
                        }
                    }
            }

            val result = this - modsToSkip
            println("Deduplicating ${this.count()} mods...done, removed ${this.count() - result.count()} mods.")
            result
        }
        .onEach { println(it.toString()) }
        .run {
            println("Saving ${this.count()} mods to ${ModRepoCache.location.toAbsolutePath()}")
            modRepoCache.items = this
            modRepoCache.lastUpdated = Instant.now().truncatedTo(ChronoUnit.MINUTES).toString()
        }

    runBlocking {
        delay(1000)
    }
}

internal fun scrapeModIndexLinks(): List<ScrapedMod> {
    println("Scraping Mod Index...")
    val doc: Document = Jsoup.connect("https://fractalsoftworks.com/forum/index.php?topic=177.0").get()
//        Jsoup.parse(
//            Path.of("C:/Users/whitm/SMOL/web/Starsector_Index/fractalsoftworks.com/forum/indexebd2.html").toFile(), null
//        )
    val categories: Elements = doc.select("ul.bbc_list")

    return categories
        .flatMap { categoryElement ->
            val category =
                categoryElement.previousElementSibling()?.previousElementSibling()?.previousElementSibling()?.text()
                    ?.trimEnd(':') ?: ""

            categoryElement.select("li").map { modElement ->
                val link = modElement.select("a.bbc_link")

                ScrapedMod(
                    name = link.text(),
                    gameVersionReq = modElement.select("strong span").text(),
                    authors = modElement.select("em strong").text(),
                    forumPostLink = link.attr("href").ifBlank { null }?.let { URI.create(it) },
                    source = ModSource.Index,
                    categories = listOf(category)
                )
            }
        }
}

internal fun scrapeModdingForumLinks(): List<ScrapedMod> {
    println("Scraping Modding Forum...")
    return scrapeSubforumLinks(
        forumBaseUrl = FORUM_BASE_URL,
        subforumNumber = 3
    )
}

internal fun scrapeModForumLinks(): List<ScrapedMod> {
    println("Scraping Mod Forum...")
    return scrapeSubforumLinks(
        forumBaseUrl = FORUM_BASE_URL,
        subforumNumber = 8
    )
}

private fun scrapeSubforumLinks(forumBaseUrl: String, subforumNumber: Int): List<ScrapedMod> {
    return (0 until 80 step 20)
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
                        gameVersionReq = versionRegex.find(titleLinkElement.text())?.groupValues?.getOrNull(1)?.trim()
                            ?: "",
                        authors = authorLinkElement.text(),
                        forumPostLink = titleLinkElement.attr("href").ifBlank { null }?.let { URI.create(it) },
                        source = ModSource.ModdingSubforum,
                        categories = emptyList()
                    )
                }
                .filter { it.gameVersionReq.isNotEmpty() }
                .filter { !it.name.contains("MOVED", ignoreCase = true) }
        }
}
