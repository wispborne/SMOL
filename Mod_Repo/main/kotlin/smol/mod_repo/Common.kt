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
    /**
     * Whether a url has a downloadable file on the other side.
     * Requires Internet.
     */
    fun isDownloadable(url: String?): Boolean =
        kotlin.runCatching {
            val conn = URL(url ?: return false).openConnection()
            val headers = conn.headerFields
            Timber.v { "Url $url has headers ${headers.entries.joinToString(separator = "\n")}." }

            val contentDisposition = ContentDisposition.parse(conn.getHeaderField("Content-Disposition")) as ContentDisposition?
            return contentDisposition?.disposition?.startsWith("attachment", ignoreCase = true) ?: false
        }
            .onFailure { Timber.d(it) }
            .getOrDefault(false)
}