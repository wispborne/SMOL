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
    val isUtilityMod = utilityString.toBooleanStrictOrNull() ?: false

    val authorsSplit: List<String> =
        kotlin.runCatching {
            author
                .split(',')
                .map { it.trim() }
        }
            .getOrElse { emptyList() }

    //    @JsonClass(generateAdapter = true)
    data class v091(
        private val _id: String,
        private val _name: String = "",
        private val _author: String = "",
        private val _utilityString: String = "false",
        @Json(name = "version") private val versionString: String,
        private val _description: String = "",
        private val _gameVersion: String,
        private val _jars: List<String> = emptyList(),
        private val _modPlugin: String = ""
    ) : ModInfo(
        _id,
        _name,
        _author,
        _utilityString,
        _description,
        _gameVersion,
        _jars,
        _modPlugin
    ) {
        override val version: Version = Version.parse(versionString)

    }

    //    @JsonClass(generateAdapter = true)
    data class v095(
        private val _id: String,
        private val _name: String = "",
        private val _author: String = "",
        private val _utilityString: String = "false",
        @Json(name = "version") private val versionString: Version,
        private val _description: String = "",
        private val _gameVersion: String,
        private val _jars: List<String> = emptyList(),
        private val _modPlugin: String = ""
    ) : ModInfo(
        _id,
        _name,
        _author,
        _utilityString,
        _description,
        _gameVersion,
        _jars,
        _modPlugin
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

    companion object {
        fun parse(versionString: String): Version {
            // Remove all non-version data from the version information,
            // then split the version number and release candidate number
            // (ex: "Starsector 0.65.2a-RC1" becomes {"0.65.2","1"})
            val localRaw = versionString
                .replace("[^0-9.-]", "")
                .split('-', limit = 2);

            val split = localRaw.first().split('.')

            return Version(
                raw = versionString,
                major = split.getOrElse(0) { "0" },
                minor = split.getOrElse(1) { "0" },
                patch = split.getOrElse(2) { "0" },
                build = split.getOrElse(3) { "0" },
            )
        }
    }
}