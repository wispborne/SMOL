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

package smol.mod_repo

import io.ktor.http.*
import smol.timber.ktx.Timber
import java.net.URL

object Common {
    private val downloadableContentTypes = listOf(
        ContentType.Application.OctetStream,
        ContentType.Application.Zip
    )

    /**
     * Whether a url has a downloadable file on the other side.
     * Requires Internet.
     */
    fun isDownloadable(url: String?): Boolean =
        runCatching {
            Timber.d { "Checking to see if $url is downloadable by opening a connection." }
            val conn = URL(url ?: return false).openConnection()

            val hasAttachment = conn.getHeaderField("Content-Disposition")?.let { contentDispoHeader ->
                runCatching { ContentDisposition.parse(contentDispoHeader) }
                    .onFailure { Timber.d(it) }
                    .getOrNull()
                    ?.disposition?.startsWith("attachment", ignoreCase = true) ?: false
            } ?: false

            val hasDownloadableContentType = kotlin.run {
                conn.getHeaderField("Content-Type")?.let { contentType ->
                    runCatching { ContentType.parse(contentType) }
                        .onFailure { Timber.d(it) }
                        .getOrNull()
                        ?.let { type -> downloadableContentTypes.any { downloadableContentType -> downloadableContentType.match(type) } }
                }
            } ?: false

            val isDownloadable = hasAttachment || hasDownloadableContentType

            val headers = conn.headerFields
            Timber.d { "Url '$url': HasAttachment: $hasAttachment, HasDownloadableContentType: $hasDownloadableContentType, and has headers:\n${headers.entries.sortedBy { it.key }.joinToString(separator = "\n")}." }

            return isDownloadable
        }
            .onFailure { Timber.d(it) }
            .getOrDefault(false)
}