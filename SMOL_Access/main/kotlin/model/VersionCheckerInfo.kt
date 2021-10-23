package model

import com.squareup.moshi.Json

data class VersionCheckerInfo(
    @Json(name = "modThreadId") val modThreadId: String?,
    @Json(name = "modVersion") val modVersion: Version?
) {
    data class Version(
        @Json(name = "major") val major: String?,
        @Json(name = "minor") val minor: String?,
        @Json(name = "patch") val patch: String?
    )
}