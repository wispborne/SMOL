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