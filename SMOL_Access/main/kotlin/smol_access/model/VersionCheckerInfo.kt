package smol_access.model

import com.google.gson.annotations.SerializedName

data class VersionCheckerInfo(
    @SerializedName("masterVersionFile") val masterVersionFile: String?,
    @SerializedName("modThreadId") val modThreadId: String?,
    @SerializedName("modVersion") val modVersion: Version?,
    @SerializedName("directDownloadURL") val directDownloadURL: String?,
) {
    data class Version(
        @SerializedName("major") val major: String?,
        @SerializedName("minor") val minor: String?,
        @SerializedName("patch") val patch: String?
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