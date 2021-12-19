package smol_access.model

import com.squareup.moshi.Json

data class VersionCheckerInfo(
    @Json(name = "masterVersionFile") val masterVersionFile: String?,
    @Json(name = "modThreadId") val modThreadId: String?,
    @Json(name = "modVersion") val modVersion: Version?,
    @Json(name = "directDownloadURL") val directDownloadURL: String?,
) {
    data class Version(
        @Json(name = "major") val major: String?,
        @Json(name = "minor") val minor: String?,
        @Json(name = "patch") val patch: String?
    ): Comparable<Version> {
        override fun toString() = listOfNotNull(major, minor, patch).joinToString(separator = ".")

        override operator fun compareTo(other: Version): Int {
            (this.major ?: "0").compareTo(other.major ?: "0", ignoreCase = true).run { if (this != 0) return this }
            (this.minor ?: "0").compareTo(other.minor ?: "0", ignoreCase = true).run { if (this != 0) return this }
            (this.patch ?: "0").compareTo(other.patch ?: "0", ignoreCase = true).run { if (this != 0) return this }
            return 0
        }
    }
}