package mod_repo

import com.fasterxml.jackson.databind.json.JsonMapper
import com.google.gson.Gson
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import utilities.Jsanity
import java.net.URI
import java.nio.file.Path

internal val CONFIG_FOLDER_DEFAULT = Path.of("")

fun main(args: Array<String>) {
    val jsanity = Jsanity(Gson(), JsonMapper())
    val modIndexCache = ModIndexCache(jsanity)

    // Mod Index
    scrapeModIndexLinks()
        .onEach { println(it) }
        .run {
            println("Saving mods to ${ModIndexCache.location.toAbsolutePath()}")
            modIndexCache.items = this
        }

    // Modding Subforum
    val moddingSubforumCache = ModdingSubforumCache(jsanity)
    scrapeModdingForumLinks()
        .onEach { println(it) }
        .run {
            println("Saving mods to ${ModdingSubforumCache.location.toAbsolutePath()}")
            moddingSubforumCache.items = this
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
    val baseUri = "https://fractalsoftworks.com/forum/index.php"

    return (0 until 80 step 20)
        .flatMap { page ->
            val doc: Document = Jsoup.connect("$baseUri?board=3.$page").get()
            val posts: Elements = doc.select("#messageindex tr")
            val versionRegex = Regex("""[\[{](.*?)[]}]""")

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
