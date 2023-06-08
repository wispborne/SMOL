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

package smol.access.model

data class Vmparams(
    val fullString: String
) {
    companion object {
        val xmsRegex = Regex("""(?<=xms).*?(?= )""", RegexOption.IGNORE_CASE)
        val xmxRegex = Regex("""(?<=xmx).*?(?= )""", RegexOption.IGNORE_CASE)
        val verifyNoneRegex = Regex("""-Xverify.*?(?= )""", RegexOption.IGNORE_CASE)
        val javaExeRegex = Regex("""(\.exe)(?= )""", RegexOption.IGNORE_CASE) // Windows only
    }

    val xmx = xmxRegex.find(fullString)?.value
    val xms = xmsRegex.find(fullString)?.value

    fun withRam(newRamAmount: String) = Vmparams(
        fullString
            .replace(xmsRegex, newRamAmount)
            .replace(xmxRegex, newRamAmount)
            .let {
                if (it.contains(verifyNoneRegex)) {
                    it.replace(verifyNoneRegex, "-Xverify:none")
                } else {
                    it.replaceFirst(javaExeRegex, ".exe -Xverify:none")
                }
            }
    )

    fun withGb(gb: Int) = withRam(newRamAmount = "${gb}g")
    fun withMb(mb: Int) = withRam(newRamAmount = "${mb}m")
}