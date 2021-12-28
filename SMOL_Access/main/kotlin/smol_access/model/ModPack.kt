package smol_access.model

import utilities.net.Uri
import java.net.URI

class ModPack(
    val schemaRevision: Int,
    val name: String,
    val revision: Int,
    val modIds: List<ModId>,
    val curator: String?,
) {
    companion object {
        val schemaPrefix = "starsector-modpack-v"

        fun fromUri(uriStr: String): ModPack {
            val uri = Uri.parse(uriStr)

            return ModPack(
                schemaRevision = uri.scheme?.removePrefix(schemaPrefix)?.removeSuffix("://")?.toIntOrNull() ?: 1,
                name = uri.pathSegments?.get(1) ?: throw NullPointerException("'name' was missing."),
                revision = uri.pathSegments?.get(3)?.toInt()
                    ?: throw NullPointerException("'revision' was missing or not an integer."),
                curator = uri.getQueryParameter("curator"),
                modIds = uri.getQueryParameters("id") ?: emptyList()
            )
        }
    }

    override fun toString(): String {
        return URI(
            "$schemaPrefix$schemaRevision://",
            null,
            "name/$name/revision/$revision",
            "?curator=$curator&${modIds.joinToString(separator = "&") { modId -> "id=$modId" }}",
            null
        ).toString()
    }
}