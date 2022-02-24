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

import com.google.gson.annotations.SerializedName
import smol_access.model.Version.Companion.compareRecognizingNumbers

data class VersionCheckerInfo(
    @SerializedName("masterVersionFile") val masterVersionFile: String?,
    @SerializedName("modNexusId") val modNexusId: String?,
    @SerializedName("modThreadId") val modThreadId: String?,
    @SerializedName("modVersion") val modVersion: Version?,
    @SerializedName("directDownloadURL") val directDownloadURL: String?,
) {
    data class Version(
        @SerializedName("major") val major: String?,
        @SerializedName("minor") val minor: String?,
        @SerializedName("patch") val patch: String?
    ) : Comparable<Version> {
        override fun toString() = listOfNotNull(major, minor, patch).joinToString(separator = ".")

        override operator fun compareTo(other: Version): Int {
            (this.major ?: "0").compareRecognizingNumbers(other.major ?: "0").run { if (this != 0) return this }

            (this.minor ?: "0").compareRecognizingNumbers(other.minor ?: "0").run { if (this != 0) return this }

            (this.patch ?: "0").compareRecognizingNumbers(other.patch ?: "0").run { if (this != 0) return this }
            return 0
        }
    }
}