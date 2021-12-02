package mod_repo

import java.net.URI

data class ScrapedMod(
    val name: String,
    val gameVersionReq: String,
    val authors: String,
    val forumPostLink: URI?,
    val source: ModSource,
    val category: String?
)

enum class ModSource {
    Index,
    ModdingSubforum
}