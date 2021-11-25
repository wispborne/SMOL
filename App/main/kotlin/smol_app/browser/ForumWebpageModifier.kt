package smol_app.browser

import org.jsoup.Jsoup

object ForumWebpageModifier {
    fun filterToFirstPost(forumHtml: String): String =
        Jsoup.parse(forumHtml).let { doc ->
            val newdoc = doc.createElement("html")

            newdoc.appendChild(doc.select("head")?.firstOrNull())
            newdoc.appendChild(doc.createElement("body")
                .apply {
                    this.appendChild(doc.select("#forumposts div.bordercolor:first-child").first())
                })

            newdoc.outerHtml()
        }
}