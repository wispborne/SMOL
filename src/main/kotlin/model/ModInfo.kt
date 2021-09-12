package model

import com.squareup.moshi.Json

sealed class ModInfo(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String = "",
    @Json(name = "author") val author: String = "",
    @Json(name = "utility") val utilityString: String = "false",
    @Json(name = "description") val description: String = "",
    @Json(name = "gameVersion") val gameVersion: String,
    @Json(name = "jars") val jars: List<String> = emptyList(),
    @Json(name = "modPlugin") val modPlugin: String = ""
) {
    abstract val version: Version
    val utility = utilityString.toBooleanStrictOrNull() ?: false


//    @JsonClass(generateAdapter = true)
    class v091(
        id: String,
        name: String = "",
        author: String = "",
        utilityString: String = "false",
        @Json(name = "version") val versionString: String,
        description: String = "",
        gameVersion: String,
        jars: List<String> = emptyList(),
        modPlugin: String = ""
    ) : ModInfo(
        id,
        name,
        author,
        utilityString,
        description,
        gameVersion,
        jars,
        modPlugin
    ) {
        override val version: Version = kotlin.run {
            // Remove all non-version data from the version information,
            // then split the version number and release candidate number
            // (ex: "Starsector 0.65.2a-RC1" becomes {"0.65.2","1"})
            val localRaw = versionString
                .replace("[^0-9.-]", "")
                .split('-', limit = 2);

            val split = localRaw.first().split('.')

            Version(
                raw = versionString,
                major = split.getOrElse(0) { "0" },
                minor = split.getOrElse(1) { "0" },
                patch = split.getOrElse(2) { "0" },
                build = split.getOrElse(3) { "0" },
            )
        }

    }

//    @JsonClass(generateAdapter = true)
    class v095(
    id: String,
    name: String = "",
    author: String = "",
    utilityString: String = "false",
    @Json(name = "version") val versionString: Version,
    description: String = "",
    gameVersion: String,
    jars: List<String> = emptyList(),
    modPlugin: String = ""
    ) : ModInfo(
        id,
        name,
        author,
        utilityString,
        description,
        gameVersion,
        jars,
        modPlugin
    ) {
        override val version: Version = versionString
    }
}

//@JsonClass(generateAdapter = true)
data class Version(
    val raw: String?,
    val major: String = "0",
    val minor: String = "0",
    val patch: String = "0",
    val build: String?
) {
    override fun toString() = raw ?: listOfNotNull(major, minor, patch, build).joinToString(separator = ".")
}