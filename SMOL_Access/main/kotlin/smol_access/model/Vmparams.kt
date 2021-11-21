package smol_access.model

data class Vmparams(
    val fullString: String
) {
    companion object {
        val xmsRegex = Regex("""(?<=xms).*?(?= )""", RegexOption.IGNORE_CASE)
        val xmxRegex = Regex("""(?<=xmx).*?(?= )""", RegexOption.IGNORE_CASE)
    }

    val xmx = xmxRegex.find(fullString)?.value
    val xms = xmsRegex.find(fullString)?.value

    fun withRam(newRamAmount: String) = Vmparams(
        fullString
            .replace(xmsRegex, newRamAmount)
            .replace(xmxRegex, newRamAmount)
    )

    fun withGb(gb: Int) = withRam(newRamAmount = "${gb}g")
    fun withMb(mb: Int) = withRam(newRamAmount = "${mb}m")
}