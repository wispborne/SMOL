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

import com.github.androidpasswordstore.sublimefuzzy.Fuzzy
import smol.timber.ktx.Timber
import smol.utilities.equalsAny
import smol.utilities.parallelMap

object ModRepoUtils {

    val authorAliases = listOf(
        listOf("Soren", "Søren", "Harmful Mechanic"),
        listOf("RustyCabbage", "rubi"),
        listOf("Wisp", "Wispborne"),
        listOf("DesperatePeter", "Jannes"),
        listOf("shoi", "gettag"),
        listOf("Dark.Revenant", "DR"),
        listOf("LazyWizard", "Lazy"),
        listOf("Techpriest", "Timid"),
        listOf("Nick XR", "Nick"),
        listOf("PMMeCuteBugPhotos", "MrFluffster"),
        listOf("Dazs", "Spiritfox"),
        listOf("Histidine, Zaphide", "Histidine"),
        listOf("Snrasha", "Snrasha, the tinkerer"),
        listOf("Hotpics", "jackwolfskin"),
        listOf("cptdash", "SpeedRacer"),
        listOf("Elseud", "Elseudo"),
        listOf("TobiaF", "Toby"),
        listOf("Mephyr", "Liral"),
    )

    fun getOtherMatchingAliases(author: String, fuzzyMatchAliases: Boolean = false): List<String> {
        return if (fuzzyMatchAliases) {
            authorAliases.firstOrNull { aliases ->
                aliases.any { alias ->
                    val match1 = Fuzzy.fuzzyMatch(author, alias)
                    if (match1.first) {
                        Timber.v { "Matched alias '$author' with '$alias' with score ${match1.second}." }
                        return@any true
                    }

                    val match2 = Fuzzy.fuzzyMatch(alias, author)
                    if (match2.first) {
                        Timber.v { "Matched alias '$author' with '$alias' with score ${match2.second}." }
                        return@any true
                    }

                    return@any false
                }
            }
                .orEmpty()
        } else {
            authorAliases
                .firstOrNull() { author.equalsAny(*it.toTypedArray(), ignoreCase = true) }
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