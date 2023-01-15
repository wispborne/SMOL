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

package smol.app.util

import com.github.androidpasswordstore.sublimefuzzy.Fuzzy
import smol.mod_repo.ModRepoUtils
import smol.mod_repo.ScrapedMod
import smol.timber.ktx.Timber
import smol.utilities.asList
import smol.utilities.parallelMap

internal suspend fun filterModPosts(query: String, mods: List<ScrapedMod>): List<ScrapedMod> {
    val split = splitSearchQuery(query)

    return split
        .flatMap { filterStr ->
            mods
                .parallelMap { mod ->
//                    if (Filter.searchMethod == FilterType.FuzzyWuzzySearch)
//                        fuzzyWuzzyModPostSearch(
//                            query = query,
//                            mod = mod
//                        )
//                    else
                        sublimeFuzzyModPostSearch(
                        query = query,
                        mod = mod
                    )
                }
                .filter { it.second.any { it.value > 70 } }
                .sortedWith(compareByDescending<Pair<ScrapedMod, Map<String, Int>>> { it.second.maxOfOrNull { it.value } }
                    .thenByDescending {
                        it.first.name
                    }) // Sort by highest match
                .map { it.first }
                .toList()
        }
}

private suspend fun sublimeFuzzyModPostSearch(query: String, mod: ScrapedMod): Pair<ScrapedMod, MutableMap<String, Int>> {
    val results = mutableMapOf<String, Int>() // match field name and value

    fun Pair<Boolean, Int>.filterAndAdd(matchedText: String) {
        Timber.d { "${Filter.searchMethod}: ${mod.name} has a score of $this for '$query' in text \"${matchedText}\" with match status: ${this.first}." }

        if (this.first) {
            results += matchedText to this.second
        }
    }

    val modAbbreviation = mod.name.acronym()

    if (modAbbreviation.length > 1) {
        Fuzzy.fuzzyMatch(query, modAbbreviation)
            .run { filterAndAdd(modAbbreviation) }
    }

    Fuzzy.fuzzyMatch(query, mod.name)
        .run { filterAndAdd(mod.name) }

    ModRepoUtils.compareToFindBestMatch(leftList = query.asList(), rightList = mod.authorsWithAliases())
        .let { (it.isMatch to it.score).filterAndAdd(it.rightMatch) }
    ModRepoUtils.compareToFindBestMatch(leftList = query.asList(), rightList = mod.sources().map { it.name })
        .let { (it.isMatch to it.score).filterAndAdd(it.rightMatch) }

    if (mod.categories().isNotEmpty()) {
        Fuzzy.fuzzyMatch(query, mod.categories().joinToString())
            .run { filterAndAdd(mod.categories().joinToString()) }
    }

    Timber.d { "${mod.name}'s match of '$query' had a total score of ${results.values.sum()} and single highest of ${results.values.maxOrNull()}." }
    return mod to results
}

//@Deprecated("Using `Fuzzy` now, from sublimetext.")
//private fun fuzzyWuzzyModPostSearch(query: String, mod: ScrapedMod): Pair<ScrapedMod, MutableMap<String, Int>> {
//    val results = mutableMapOf<String, Int>() // match field name and value
//
//    fun Int.filterAndAdd(name: String) {
//        results += name to this
//        Logger.info { "${Filter.searchMethod}: ${mod.name} has a score of $this for '$query' in text \"${name}\"." }
//    }
//
//    val modAbbreviation = mod.name.acronym()
//
//    if (modAbbreviation.length > 1) {
//        FuzzySearch.partialRatio(query, modAbbreviation) { it.lowercase() }
//            .run { filterAndAdd(modAbbreviation) }
//    }
//
//    FuzzySearch.partialRatio(query, mod.name) { it.lowercase() }
//        .run { filterAndAdd(mod.name) }
//
//    mod.authors().forEach { author ->
//        FuzzySearch.partialRatio(query, author) { it.lowercase() }
//            .run { filterAndAdd(author) }
//    }
//    mod.sources().forEach { source ->
//        FuzzySearch.partialRatio(query, source.name) { it.lowercase() }
//            .run { filterAndAdd(source.name) }
//    }
//    if (mod.categories().isNotEmpty()) {
//        FuzzySearch.partialRatio(query, mod.categories().joinToString()) { it.lowercase() }
//            .run { filterAndAdd(mod.categories().joinToString()) }
//    }
//
//    Logger.info { "${mod.name}'s match of '$query' had a total score of ${results.values.sum()} and single highest of ${results.values.maxOrNull()}." }
//    return mod to results
//}