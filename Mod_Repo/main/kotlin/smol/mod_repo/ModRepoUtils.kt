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

package smol.mod_repo

import smol.mod_repo.fuzzy.Fuzzy
import smol.timber.ktx.Timber
import smol.utilities.equalsAny
import smol.utilities.parallelMap

object ModRepoUtils {

    val authorAliases = listOf(
        listOf("Soren", "Søren", "Harmful Mechanic"),
        listOf("RustyCabbage", "rubi", "ceruleanpancake"),
        listOf("Wisp", "Wispborne", "Tartiflette and Wispborne"),
        listOf("DesperatePeter", "Jannes"),
        listOf("shoi", "gettag"),
        listOf("Dark.Revenant", "DR"),
        listOf("LazyWizard", "Lazy"),
        listOf("Techpriest", "Timid"),
        listOf("Nick XR", "Nick", "nick7884"),
        listOf("PMMeCuteBugPhotos", "MrFluffster"),
        listOf("Dazs", "Spiritfox", "spiritfox_"),
        listOf("Histidine, Zaphide", "Histidine", "histidine_my"),
        listOf("Snrasha", "Snrasha, the tinkerer"),
        listOf("Hotpics", "jackwolfskin"),
        listOf("cptdash", "SpeedRacer"),
        listOf("Elseud", "Elseudo"),
        listOf("TobiaF", "Toby"),
        listOf("Mephyr", "Liral"),
        listOf("Tranquility", "tranquil_light"),
        listOf("FasterThanSleepyfish", "Sleepyfish"),
        listOf("Nerzhull_AI", "nerzhulai"),
        listOf("theDrag", "iryx."),
        listOf("Audax", "Audaxl"),
        listOf("Pogre", "noof"),
        listOf("lord_dalton", "Epta Consortium"),
        listOf("hakureireimu", "LngA7Gw"),
        listOf("Nes", "nescom"),
        listOf("float", "this_is_a_username"),
        listOf("AERO", "aero.assault"),
        listOf("Fellout", "felloutwastaken"),
        listOf("Mr. THG", "thog"),
        listOf("Derelict_Surveyor", "jdt15"),
    )

    fun getOtherMatchingAliases(
        author: String,
        fuzzyMatchAliases: Boolean = false,
        matchScoreNeeded: Int = 150
    ): List<String> {
        val aliasesFormatted = authorAliases
            .map { aliases -> aliases.map { alias -> alias.lowercase() } }
        val authorFormatted = author.lowercase()

        // fuzzyMatchAliases is slower, more flexible, but risks false positives.
        // Last check, using score limit of 150, it only confused "nick", "nick7884", and "nicke535".
        // Without fuzzy merge: Total time to merge 726 mods: 2565ms
        // With fuzzy merge: Total time to merge 726 mods: 4938ms
        return if (fuzzyMatchAliases) {
            aliasesFormatted.firstOrNull { aliases ->
                aliases.any { alias ->
                    val match1 = Fuzzy.fuzzyMatch(authorFormatted.lowercase(), alias.lowercase())
                    if (match1.first) {
                        if (match1.second > matchScoreNeeded) {
                            Timber.v { "Matched alias '$author' with '$alias' with score ${match1.second}." }
                            return@any true
                        } else
                            Timber.v { "Did not match alias '$author' with '$alias' with score ${match1.second}." }
                    }

                    val match2 = Fuzzy.fuzzyMatch(alias.lowercase(), authorFormatted.lowercase())
                    if (match2.first) {
                        if (match2.second > matchScoreNeeded) {
                            Timber.v { "Matched alias '$author' with '$alias' with score ${match2.second}." }
                            return@any true
                        } else
                            Timber.v { "Did not match alias '$author' with '$alias' with score ${match2.second}." }
                    }

                    return@any false
                }
            }
                .orEmpty()
        } else {
            aliasesFormatted
                // No need to ignore case, everything is already lowercase.
                .firstOrNull { aliasesRow ->
                    aliasesRow.any { alias ->
                        (alias == authorFormatted)
                            .also { if (it) Timber.v { "Matched author '$author' with alias list '${aliasesRow.joinToString()}'." } }
                    }
                }
                .orEmpty()
        }
    }

    suspend fun compareToFindBestMatch(
        leftList: List<String>,
        rightList: List<String>,
        stopAtFirstMatch: Boolean = true,
        scoreThreshold: Int = 100
    ): MatchResult {
        Timber.v { "Comparing left: ${leftList.joinToString()} to right: ${rightList.joinToString()}." }
        return leftList
            .flatMap { i ->
                // Generate all pairs
                rightList
                    .map { j ->
                        i to j
                    }
            }
            .parallelMap { pair ->
                val fuzzyMatch = Fuzzy.fuzzyMatch(pair.first, pair.second)
                val obj = MatchResult(
                    leftMatch = pair.first,
                    rightMatch = pair.second,
                    isMatch = fuzzyMatch.first,
                    score = fuzzyMatch.second
                )

                Timber.v { "Compared: $obj." }

                if (stopAtFirstMatch && fuzzyMatch.second > scoreThreshold)
                    return@parallelMap obj
                obj
            }
            .maxByOrNull { it.score }
            ?.let { highestMatch -> if (highestMatch.score > scoreThreshold) highestMatch else null }
            ?: MatchResult(leftMatch = "", rightMatch = "", isMatch = false, score = 0)
    }

    data class MatchResult(val leftMatch: String, val rightMatch: String, val isMatch: Boolean, val score: Int)
}