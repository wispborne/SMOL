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