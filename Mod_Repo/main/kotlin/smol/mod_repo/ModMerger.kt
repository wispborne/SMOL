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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import smol.timber.ktx.Timber
import smol.utilities.asList
import smol.utilities.nullIfBlank
import smol.utilities.parallelMap
import java.time.Instant
import java.util.Collections.swap

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
        val lock = Semaphore(permits = 1)
        val summary = StringBuilder()

        return mods
            .sortedBy { it.name }
            .let { scrapedMods ->
                Timber.i { "Grouping ${mods.count()} mods by similarity..." }

                // For each mod, look for all similar mods and return a group of similar mods
                scrapedMods
                    .mapIndexed { index, outerLoopMod ->
                        if (outerLoopMod in modsAlreadyAddedToAGroup) {
                            return@mapIndexed null
                        }

                        // Add the mod and then look for and add all similar ones, starting from location of the outer loop
                        return@mapIndexed listOf(outerLoopMod)
                            .plus(scrapedMods.subList(index, scrapedMods.count())
                                .parallelMap { innerLoopMod ->
                                    withContext(Dispatchers.Default) {
                                        // Skip comparing the mod to itself.
                                        // Skip comparing mods from the same source; there shouldn't be duplicates in the same place.
                                        if (innerLoopMod === outerLoopMod
                                            || outerLoopMod.sources().containsAll(innerLoopMod.sources())
                                        ) {
                                            return@withContext innerLoopMod to false
                                        }

                                        val outer = outerLoopMod.name.prepForMatching()
                                        val inner = innerLoopMod.name.prepForMatching()

                                        val bestNameResult = ModRepoUtils.compareToFindBestMatch(
                                            leftList = outer.asList(),
                                            rightList = inner.asList()
                                        )

                                        val bestAuthorsResult =
                                            ModRepoUtils.compareToFindBestMatch(
                                                leftList = listOf(
                                                    outerLoopMod.authors.asList(),
                                                    ModRepoUtils.getOtherMatchingAliases(outerLoopMod.authors),
                                                )
                                                    .flatten()
                                                    .distinct()
                                                    .mapNotNull { it.prepForMatching() },
                                                rightList = listOf(
                                                    innerLoopMod.authors.asList(),
                                                    ModRepoUtils.getOtherMatchingAliases(innerLoopMod.authors),
                                                )
                                                    .flatten()
                                                    .distinct()
                                                    .mapNotNull { it.prepForMatching() }
                                            )

                                        val outerUrl = outerLoopMod.urls()[ModUrlType.Forum]
                                        val doForumLinksMatch =
                                            outerUrl != null && outerUrl == innerLoopMod.urls()[ModUrlType.Forum]
                                        val doNameAndAuthorMatch = bestNameResult.isMatch && bestAuthorsResult.isMatch

                                        val isMatch = doNameAndAuthorMatch || doForumLinksMatch

                                        if (doNameAndAuthorMatch) {
                                            Timber.d { "Matched names $bestNameResult and authors $bestAuthorsResult." }
                                        }

                                        if (doForumLinksMatch) {
                                            Timber.d { "Matching forum urls for ${outerLoopMod.name} and ${innerLoopMod.name}: $outerUrl." }
                                        }

                                        if (isMatch) {
                                            lock.acquire()
                                            modsAlreadyAddedToAGroup.add(innerLoopMod)
                                            lock.release()
                                            innerLoopMod to true
                                        } else {
                                            innerLoopMod to false
                                        }
                                    }
                                }
                                .filter { it.second }
                                .map { it.first })
                    }
                    .filterNotNull()
            }
            .also { modGroups ->
                val msg =
                    "Grouped ${mods.count()} mods by similarity, created ${modGroups.count()} groups."
                Timber.i { msg }
                summary.appendLine(msg)
            }
            .also { groupedMods ->
                if (Main.verboseOutput) {
                    groupedMods.forEach { modGroup ->
                        Timber.i {
                            buildString {
                                appendLine("Mod group of ${modGroup.count()}:")
                                modGroup.forEach { mod ->
                                    appendLine(
                                        "  '${mod.name}' by '${
                                            mod.authors().joinToString()
                                        }' from ${mod.sources} (${
                                            when (mod.sources().firstOrNull()) {
                                                ModSource.Index -> mod.urls()[ModUrlType.Forum]?.toString()
                                                ModSource.ModdingSubforum -> mod.urls()[ModUrlType.Forum]?.toString()
                                                ModSource.Discord -> mod.urls()[ModUrlType.Discord]?.toString()
                                                ModSource.NexusMods -> mod.urls()[ModUrlType.NexusMods]?.toString()
                                                null -> "no source"
                                            }.toString()
                                        })"
                                    )
                                }
                            }
                        }
                    }
                }
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
            .onEach { Timber.v { it.toString() } }
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
                    if (mergedMod.sources.orEmpty().contains(ModSource.Index)) {
                        Timber.i { "Merging '${modToFoldIn.name}' from '${modToFoldIn.authors}' with higher priority '${mergedMod.name}' from '${mergedMod.authors}'." }
                        false
                    } else if (modToFoldIn.sources.orEmpty().contains(ModSource.Index)) {
                        Timber.i { "Merging '${mergedMod.name}' from '${mergedMod.authors}' with higher priority '${modToFoldIn.name}' from '${modToFoldIn.authors}'." }
                        true
                    } else {
                        Timber.i { "Merging '${modToFoldIn.name}' from '${modToFoldIn.authors}' with higher priority '${mergedMod.name}' from '${mergedMod.authors}'." }
                        false
                    }

                ScrapedMod(
                    name = chooseBest(
                        left = mergedMod.name.ifBlank { null },
                        right = modToFoldIn.name.ifBlank { null },
                        doesRightHavePriority = doesNewModHavePriority
                    ) ?: "",
                    summary = chooseBest(
                        left = mergedMod.summary?.ifBlank { null },
                        right = modToFoldIn.summary?.ifBlank { null },
                        doesRightHavePriority = doesNewModHavePriority
                    ),
                    description = chooseBest(
                        left = mergedMod.description?.ifBlank { null },
                        right = modToFoldIn.description?.ifBlank { null },
                        doesRightHavePriority = doesNewModHavePriority
                    ),
                    modVersion = chooseBest(
                        left = mergedMod.modVersion?.ifBlank { null },
                        right = modToFoldIn.modVersion?.ifBlank { null },
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
                    authorsList = (mergedMod.authors() + modToFoldIn.authors())
                        .distinctBy { it.prepForMatching() }
                        .filter { it.isNotBlank() },
                    forumPostLink = chooseBest(
                        left = mergedMod.forumPostLink,
                        right = modToFoldIn.forumPostLink,
                        doesRightHavePriority = doesNewModHavePriority
                    ),
                    link = chooseBest(
                        left = mergedMod.link,
                        right = modToFoldIn.link,
                        doesRightHavePriority = doesNewModHavePriority
                    ),
                    urls = mergedMod.urls() + modToFoldIn.urls(),
                    source = chooseBest(
                        left = mergedMod.source,
                        right = modToFoldIn.source,
                        doesRightHavePriority = doesNewModHavePriority
                    ),
                    sources = (modToFoldIn.sources() + mergedMod.sources()).distinct(),
                    categories = (modToFoldIn.categories() + mergedMod.categories()).distinctBy { it.lowercase() },
                    images = (modToFoldIn.images() + mergedMod.images())
                        .entries
                        .distinctBy { it.value.url }
                        .associate { it.toPair() },
                    dateTimeCreated = chooseBest(
                        left = mergedMod.dateTimeCreated,
                        right = modToFoldIn.dateTimeCreated,
                        doesRightHavePriority = doesNewModHavePriority
                    ),
                    dateTimeEdited = chooseBest(
                        left = mergedMod.dateTimeEdited,
                        right = modToFoldIn.dateTimeEdited,
                        doesRightHavePriority = doesNewModHavePriority
                    ),
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
                val hasLink = !it.urls.isNullOrEmpty()
                if (!hasLink) Timber.i { "Removing mod without any links: '${it.name}' by '${it.authors()}'." }
                hasLink
            }

    private fun String.prepForMatching() = this.lowercase().filter { it.isLetter() }.nullIfBlank()

    /**
     * Usage: listOf(1, 2, 3).permutations()
     * Output: [[1, 2, 3], [2, 1, 3], [3, 1, 2], [1, 3, 2], [2, 3, 1], [3, 2, 1]]
     */
    fun <V> List<V>.permutations(): List<List<V>> {
        val retVal: MutableList<List<V>> = mutableListOf()

        fun generate(k: Int, list: List<V>) {
            // If only 1 element, just output the array
            if (k == 1) {
                retVal.add(list.toList())
            } else {
                for (i in 0 until k) {
                    generate(k - 1, list)
                    if (k % 2 == 0) {
                        swap(list, i, k - 1)
                    } else {
                        swap(list, 0, k - 1)
                    }
                }
            }
        }

        generate(this.count(), this.toList())
        return retVal
    }
}