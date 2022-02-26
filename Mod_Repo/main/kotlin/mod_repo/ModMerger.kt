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

package mod_repo

import com.github.androidpasswordstore.sublimefuzzy.Fuzzy
import io.ktor.http.*
import io.ktor.util.*

/**
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
internal class ModMerger {
    fun merge(mods: List<ScrapedMod>): List<ScrapedMod> {
        return mods
            .sortedBy { it.name }
            .run {
                println("Deduplicating ${this.count()} mods...")
                val modsToSkip = mutableListOf<ScrapedMod>()

                for (outer in this) {
                    if (outer in modsToSkip) {
                        continue
                    }

                    this.minus(outer)
                        .forEach { inner ->
                            val nameResult =
                                Fuzzy.fuzzyMatch(outer.name.prepForMatching(), inner.name.prepForMatching())
                            val nameResultFlip =
                                Fuzzy.fuzzyMatch(inner.name.prepForMatching(), outer.name.prepForMatching())
                            val authorsResult =
                                Fuzzy.fuzzyMatch(
                                    outer.authors.prepForMatching(),
                                    inner.authors.prepForMatching()
                                )
                            val authorsResultFlip =
                                Fuzzy.fuzzyMatch(
                                    inner.authors.prepForMatching(),
                                    outer.authors.prepForMatching()
                                )

                            val isMatch =
                                (nameResult.first && authorsResult.first)
                                        || (nameResultFlip.first && authorsResultFlip.first)
                                        || (nameResult.first && authorsResultFlip.first)
                                        || (nameResultFlip.first && authorsResult.first)

                            if (Main.isDebugMode && (nameResult.second > 0 || nameResultFlip.second > 0 || authorsResult.second > 0 || authorsResultFlip.second > 0)) {
                                println(buildString {
                                    appendLine("Compared '${outer.name}' to '${inner.name}':")
                                    appendLine("  '${outer.name.prepForMatching()}'<-->'${inner.name.prepForMatching()}'==>${nameResult.second}")
                                    appendLine("  '${inner.name.prepForMatching()}'<-->'${outer.name.prepForMatching()}'==>${nameResultFlip.second}")
                                    appendLine("  '${outer.authors.prepForMatching()}'<-->'${inner.authors.prepForMatching()}'==>${authorsResult.second}")
                                    append("  '${inner.authors.prepForMatching()}'<-->'${outer.authors.prepForMatching()}'==>${authorsResultFlip.second}")
                                })
                            }

                            if (isMatch) {
                                modsToSkip.add(
                                    if (outer.source == ModSource.Index) {
                                        println("Replacing '${inner.name}' from '${inner.authors}' with '${outer.name}' from '${outer.authors}'.")
                                        inner
                                    } else if (inner.source == ModSource.Index) {
                                        println("Replacing '${outer.name}' from '${outer.authors}' with '${inner.name}' from '${inner.authors}'.")
                                        outer
                                    } else {
                                        println("Replacing '${inner.name}' from '${inner.authors}' with '${outer.name}' from '${outer.authors}'.")
                                        inner
                                    }
                                )
                            }
                        }
                }

                val result = this - modsToSkip
                println("Deduplicating ${this.count()} mods...done, removed ${this.count() - result.count()} mods.")
                cleanUpMods(result)
            }
    }

    private fun cleanUpMods(mods: List<ScrapedMod>): List<ScrapedMod> =
        mods
            .map { mod ->
                mod.copy(forumPostLink = mod.forumPostLink?.copy(
                    parameters = mod.forumPostLink.parameters
                        .filter { key, _ -> !key.equals("PHPSESSID", ignoreCase = true) }
                        .let { Parameters.build { appendAll(it) } })
                )
            }

    private fun String.prepForMatching() = this.lowercase().filter { it.isLetter() }
}