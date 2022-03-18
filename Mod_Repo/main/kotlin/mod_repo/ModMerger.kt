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
import timber.ktx.Timber
import utilities.parallelMap
import java.time.Instant

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
    suspend fun merge(mods: List<ScrapedMod>): List<ScrapedMod> {
        val startTime = Instant.now()

        // Mods that are also listed from another, more preferable source.
        val modsAlreadyAddedToAGroup = mutableListOf<ScrapedMod>()
        val summary = StringBuilder()

        return mods
            .sortedBy { it.name }
            .let { scrapedMods ->
                Timber.i { "Grouping ${mods.count()} mods by similarity..." }

                // For each mod, look for all similar mods and return a group of similar mods
                scrapedMods
                    .mapNotNull { outerLoopMod ->
                        if (outerLoopMod in modsAlreadyAddedToAGroup) {
                            return@mapNotNull null
                        }

                        // Add the mod and then look for and add all similar ones
                        return@mapNotNull listOf(outerLoopMod) + scrapedMods
                            .parallelStream()
                            .filter { innerLoopMod ->
                                val nameResult =
                                    Fuzzy.fuzzyMatch(
                                        outerLoopMod.name.prepForMatching(),
                                        innerLoopMod.name.prepForMatching()
                                    )
                                val nameResultFlip =
                                    Fuzzy.fuzzyMatch(
                                        innerLoopMod.name.prepForMatching(),
                                        outerLoopMod.name.prepForMatching()
                                    )
                                val authorsResult =
                                    Fuzzy.fuzzyMatch(
                                        outerLoopMod.authors.prepForMatching(),
                                        innerLoopMod.authors.prepForMatching()
                                    )
                                val authorsResultFlip =
                                    Fuzzy.fuzzyMatch(
                                        innerLoopMod.authors.prepForMatching(),
                                        outerLoopMod.authors.prepForMatching()
                                    )

                                val isMatch =
                                    (nameResult.first && authorsResult.first)
                                            || (nameResultFlip.first && authorsResultFlip.first)
                                            || (nameResult.first && authorsResultFlip.first)
                                            || (nameResultFlip.first && authorsResult.first)

                                if (Main.verboseOutput && (nameResult.second > 0 || nameResultFlip.second > 0 || authorsResult.second > 0 || authorsResultFlip.second > 0)) {
                                    Timber.d {
                                        buildString {
                                            appendLine("Compared '${outerLoopMod.name}' to '${innerLoopMod.name}':")
                                            appendLine("  '${outerLoopMod.name.prepForMatching()}'<-->'${innerLoopMod.name.prepForMatching()}'==>${nameResult.second}")
                                            appendLine("  '${innerLoopMod.name.prepForMatching()}'<-->'${outerLoopMod.name.prepForMatching()}'==>${nameResultFlip.second}")
                                            appendLine("  '${outerLoopMod.authors.prepForMatching()}'<-->'${innerLoopMod.authors.prepForMatching()}'==>${authorsResult.second}")
                                            append("  '${innerLoopMod.authors.prepForMatching()}'<-->'${outerLoopMod.authors.prepForMatching()}'==>${authorsResultFlip.second}")
                                        }
                                    }
                                }

                                if (isMatch) {
                                    modsAlreadyAddedToAGroup.add(innerLoopMod)
                                    true
                                } else {
                                    false
                                }
                            }
                            .toList()
                    }
            }
            .also { modGroups ->
                val msg =
                    "Grouped ${mods.count()} mods by similarity, created ${modGroups.count()} groups."
                Timber.i { msg }
                summary.appendLine(msg)
            }
            .let { groupedMods ->
                Timber.i { "Merging ${mods.count()} mods by similarity..." }
                groupedMods.parallelMap { modGroup -> mergeSimilarMods(modGroup) }
            }
            .also { mergedMods ->
                val msg =
                    "Merged ${mods.count()} mods by similarity. ${mods.count() - mergedMods.count()} mods were duplicates, resulting in a total of ${mergedMods.count()} merged mods."
                Timber.i { msg }
                summary.appendLine(msg)
            }
            .let { mergedMods ->
                cleanUpMods(mergedMods)
            }
            .onEach { Timber.i { it.toString() } }
            .also { Timber.i { summary.toString() } }
            .also {
                Timber.i {
                    "Total time to merge ${mods.count()} mods: ${
                        Instant.now().toEpochMilli() - startTime.toEpochMilli()
                    }ms."
                }
            }
    }

    private fun mergeSimilarMods(mods: List<ScrapedMod>): ScrapedMod {
        return mods
            .reduce { mergedMod, modToFoldIn ->
                if (mergedMod == modToFoldIn) return@reduce mergedMod

                // Mods from the Index always have priority in case of conflicts.
                val doesNewModHavePriority =
                    if (mergedMod.sources.contains(ModSource.Index)) {
                        Timber.i { "Merging '${modToFoldIn.name}' from '${modToFoldIn.authors}' with higher priority '${mergedMod.name}' from '${mergedMod.authors}'." }
                        false
                    } else if (modToFoldIn.sources.contains(ModSource.Index)) {
                        Timber.i { "Merging '${mergedMod.name}' from '${mergedMod.authors}' with higher priority '${modToFoldIn.name}' from '${modToFoldIn.authors}'." }
                        true
                    } else {
                        Timber.i { "Merging '${modToFoldIn.name}' from '${modToFoldIn.authors}' with higher priority '${mergedMod.name}' from '${mergedMod.authors}'." }
                        false
                    }

                mergedMod.copy(
                    name = chooseBest(
                        left = mergedMod.name.ifBlank { null },
                        right = modToFoldIn.name.ifBlank { null },
                        doesRightHavePriority = doesNewModHavePriority
                    ) ?: "",
                    description = chooseBest(
                        left = mergedMod.description?.ifBlank { null },
                        right = modToFoldIn.description?.ifBlank { null },
                        doesRightHavePriority = doesNewModHavePriority
                    ),
                    gameVersionReq = chooseBest(
                        left = mergedMod.gameVersionReq?.ifBlank { null },
                        right = modToFoldIn.gameVersionReq?.ifBlank { null },
                        doesRightHavePriority = doesNewModHavePriority
                    ),
                    authors = chooseBest(
                        left = mergedMod.authors.ifBlank { null },
                        right = modToFoldIn.authors.ifBlank { null },
                        doesRightHavePriority = doesNewModHavePriority
                    ) ?: "",
                    forumPostLink = chooseBest(
                        left = mergedMod.forumPostLink,
                        right = modToFoldIn.forumPostLink,
                        doesRightHavePriority = doesNewModHavePriority
                    ),
                    discordMessageLink = chooseBest(
                        left = mergedMod.discordMessageLink,
                        right = modToFoldIn.discordMessageLink,
                        doesRightHavePriority = doesNewModHavePriority
                    ),
                    source = chooseBest(
                        left = mergedMod.source,
                        right = modToFoldIn.source,
                        doesRightHavePriority = doesNewModHavePriority
                    ),
                    sources = (modToFoldIn.sources + mergedMod.sources).distinct(),
                    categories = (modToFoldIn.categories.orEmpty() + mergedMod.categories.orEmpty()).distinct(),
                )
            }
    }

    private fun <T> chooseBest(left: T, right: T, doesRightHavePriority: Boolean): T {
        return if (left != null && right != null)
            if (doesRightHavePriority) right
            else left
        else if (doesRightHavePriority)
            right ?: left
        else left ?: right
    }

    private fun cleanUpMods(mods: List<ScrapedMod>): List<ScrapedMod> =
        mods
            .filter {
                val hasLink = it.forumPostLink != null
                if (!hasLink) Timber.i { "Removing mod without a forum link: '${it.name}' by '${it.authors}'." }
                hasLink
            }

    private fun String.prepForMatching() = this.lowercase().filter { it.isLetter() }
}