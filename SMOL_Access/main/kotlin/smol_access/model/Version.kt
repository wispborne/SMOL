package smol_access.model

data class Version(
    val raw: String?,
    val major: String = "0",
    val minor: String = "0",
    val patch: String = "0",
    val build: String? = null,
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
                build = split.getOrElse(3) { "0" }
            )
        }

        val letterDigitSplitterRegex = Regex("""(?<=\D)(?=\d)|(?<=\d)(?=\D)""")

        /**
         * Breaks a string into chunks of letters and numbers.
         * "55hhb3vv-5 s" -> ["55", "hhb", "3", "vv", "5", "s"]
         */
        fun String.splitIntoAlphaAndNumeric(): List<String> {
            val str = this

            return (listOf(0) + letterDigitSplitterRegex.findAll(str)
                .map { it.range.first }
                .toList() + listOf(str.length))
                .zipWithNext { l, r -> str.subSequence(l, r).toString().filter { it.isLetterOrDigit() } }
        }

        /**
         * `compareTo`, except it compares numbers as numbers instead of their unicode symbol value.
         * Can use <https://github.com/apache/maven/blob/master/maven-artifact/src/main/java/org/apache/maven/artifact/versioning/ComparableVersion.java>
         *     if this doesn't work right.
         */
        fun String.compareRecognizingNumbers(other: String): Int {
            val thisChunked = this.splitIntoAlphaAndNumeric()
            val otherChunked = other.splitIntoAlphaAndNumeric()
            var i = 0

            while (i < thisChunked.size && i < otherChunked.size) {
                val thisToCompare = thisChunked.getOrElse(i) { "0" }.let { it.toIntOrNull() ?: it }
                val otherToCompare = otherChunked.getOrElse(i) { "0" }.let { it.toIntOrNull() ?: it }

                if (thisToCompare is Int && otherToCompare is Int) {
                    thisToCompare.compareTo(otherToCompare).run { if (this != 0) return this }
                } else {
                    thisToCompare.toString().compareTo(otherToCompare.toString()).run { if (this != 0) return this }
                }

                i++
            }

            return 0
        }
    }

    /**
     * Compares this object with the specified object for order. Returns zero if this object is equal
     * to the specified [other] object, a negative number if it's less than [other], or a positive number
     * if it's greater than [other].
     */
    override operator fun compareTo(other: Version): Int {
        this.major.compareRecognizingNumbers(other.major).run { if (this != 0) return this }
        this.minor.compareRecognizingNumbers(other.minor).run { if (this != 0) return this }
        this.patch.compareRecognizingNumbers(other.patch).run { if (this != 0) return this }
        (this.build ?: "0").compareRecognizingNumbers((other.build ?: "")).run { if (this != 0) return this }
        return 0
    }

    override fun equals(other: Any?): Boolean {
        return other is Version && this.raw == other.raw
    }

    override fun hashCode(): Int {
        return raw?.hashCode() ?: 0
    }
}