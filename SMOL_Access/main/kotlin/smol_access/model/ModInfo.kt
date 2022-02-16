package smol_access.model

import smol_access.Constants
import utilities.equalsAny
import java.nio.file.Path
import kotlin.io.path.name

data class ModInfo(
    val id: String,
    val name: String?,
    val author: String?,
    val description: String?,
    val requiredMemoryMB: String?,
    val gameVersion: String?,
    val jars: List<String>,
    val modPlugin: String,
    val version: Version,
    val dependencies: List<Dependency>,
    val isUtilityMod: Boolean,
)

data class Version(
    val raw: String?,
    val major: String = "0",
    val minor: String = "0",
    val patch: String = "0",
    val build: String? = null
) : Comparable<Version> {
    override fun toString() = raw ?: listOfNotNull(major, minor, patch, build).joinToString(separator = ".")

    companion object {
        fun parse(versionString: String): Version {
            // Remove all non-version data from the version information,
            // then split the version number and release candidate number
            // (ex: "Starsector 0.65.2a-RC1" becomes {"0.65.2","1"})
            val localRaw = versionString
                .replace("[^0-9.-]", "")
                .split('-', limit = 2)

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

    /**
     * Compares this object with the specified object for order. Returns zero if this object is equal
     * to the specified [other] object, a negative number if it's less than [other], or a positive number
     * if it's greater than [other].
     */
    override operator fun compareTo(other: Version): Int {
        this.major.compareTo(other.major, ignoreCase = true).run { if (this != 0) return this }
        this.minor.compareTo(other.minor, ignoreCase = true).run { if (this != 0) return this }
        this.patch.compareTo(other.patch, ignoreCase = true).run { if (this != 0) return this }
        (this.build ?: "0").compareTo((other.build ?: ""), ignoreCase = true).run { if (this != 0) return this }
        return 0
    }

    override fun equals(other: Any?): Boolean {
        return other is Version && this.raw == other.raw
    }

    override fun hashCode(): Int {
        return raw?.hashCode() ?: 0
    }
}

data class Dependency(
    val id: String? = null,
    val name: String? = null,
    val version: Version?
)

fun Path.isModInfoFile() = this.name.equalsAny(Constants.MOD_INFO_FILE, Constants.MOD_INFO_FILE_DISABLED, ignoreCase = true)