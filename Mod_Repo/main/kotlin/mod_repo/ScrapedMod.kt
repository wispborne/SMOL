package mod_repo

import java.net.URI

data class ScrapedMod(
    val name: String,
    val gameVersionReq: String,
    val authors: String,
    val forumPostLink: URI?,
    val category: String?
)