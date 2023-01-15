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
import smol.access.SL
import smol.access.business.dependencies
import smol.access.business.metadata
import smol.access.model.Mod
import smol.access.model.ModVariant
import smol.app.util.Filter.searchMethod
import smol.mod_repo.ModRepoUtils
import smol.timber.ktx.Timber
import smol.utilities.asList
import smol.utilities.parallelMap

object Filter {
    var searchMethod = FilterType.SublimeSearch
}

enum class FilterType {
    SublimeSearch,

    @Deprecated("moved to sublime")
    FuzzyWuzzySearch
}

internal suspend fun filterModGrid(query: String, mods: List<Mod>, access: smol.access.Access): List<Mod> {
    val split = splitSearchQuery(query)

    return split
        .flatMap { filterStr ->
            mods
                .parallelMap { mod ->
                    val variant = mod.findFirstEnabled ?: mod.findHighestVersion ?: return@parallelMap mod to mapOf()
//                    if (searchMethod == FilterType.FuzzyWuzzySearch)
//                        fuzzyWuzzyModSearch(
//                            query = query,
//                            variant = variant,
//                            access = access
//                        ) else
                        sublimeFuzzyModSearch(
                            query = query,
                            variant = variant,
                            mod = mod
                        )
                }
                .filter { it.second.any { it.value > 70 } }
                .sortedWith(compareByDescending<Pair<Mod, Map<String, Int>>> { it.second.maxOfOrNull { it.value } }
                    .thenByDescending {
                        it.first.findHighestVersion?.modInfo?.name ?: it.first.id
                    }) // Sort by highest match
                .map { it.first }
                .toList()
        }
        .distinctBy { it.id }
}

private suspend fun sublimeFuzzyModSearch(
    query: String,
    variant: ModVariant,
    mod: Mod
): Pair<Mod, Map<String, Int>> {
    val results = mutableMapOf<String, Int>() // match field name and value
    val modName = variant.modInfo.name
    val modAuthors = variant.modInfo.author
        ?.let { it.asList().plus(ModRepoUtils.getOtherMatchingAliases(it)).distinct() }
        .orEmpty()
    val metadata = mod.metadata(SL.modMetadata)

    fun Pair<Boolean, Int>.filterAndAdd(name: String) {
        Timber.d { "$searchMethod: $modName has a score of $this for '$query' in text \"${name}\" with match status: ${this.first}." }

        if (this.first) {
            results += name to this.second
        }
    }

    val modAbbreviation = modName?.acronym() ?: ""

    if (modAbbreviation.length > 1) {
        Fuzzy.fuzzyMatch(query, modAbbreviation)
            .run { filterAndAdd(modAbbreviation) }
    }

    if (modName != null) {
        Fuzzy.fuzzyMatch(query, modName)
            .run { filterAndAdd(modName) }
    }
    Fuzzy.fuzzyMatch(query, variant.modInfo.id)
        .run { filterAndAdd(variant.modInfo.id) }
    Fuzzy.fuzzyMatch(query, variant.modInfo.version.toString())
        .run { filterAndAdd(variant.modInfo.version.toString()) }

    if (modAuthors.any()) {
        ModRepoUtils.compareToFindBestMatch(query.asList(), modAuthors)
            .let { (it.isMatch to it.score).filterAndAdd(it.rightMatch) }
    }

    val dependencies = variant.dependencies(SL.dependencyFinder)

    if (dependencies.any()) {
        ModRepoUtils.compareToFindBestMatch(query.asList(), dependencies.mapNotNull { it.first.name })
            .let { (it.isMatch to it.score).filterAndAdd(it.rightMatch) }
    }

    if (metadata?.category?.isNotBlank() == true) {
        Fuzzy.fuzzyMatch(query, metadata.category!!)
            .run { filterAndAdd(metadata.category!!) }
    }

    Timber.d { "$modName's match of '$query' had a total score of ${results.values.sum()} and single highest of ${results.values.maxOrNull()}." }
    return mod to results
}

//@Deprecated("switch to sublime fuzzy")
//private fun fuzzyWuzzyModSearch(
//    query: String,
//    variant: ModVariant,
//    access: smol.access.Access
//): Pair<Mod, Map<String, Int>> {
//    val results = mutableMapOf<String, Int>() // match field name and value
//    val modName = variant.modInfo.name
//    val modAuthor = variant.modInfo.author
//
//    fun Int.filterAndAdd(name: String) {
//        results += name to this
//        Timber.d { "${Filter.searchMethod}: ${variant.modInfo.name} has a score of $this for '$query' in text \"${name}\"." }
//    }
//
//    val modAbbreviation = modName?.acronym() ?: ""
//
//    if (modAbbreviation.length > 1) {
//        FuzzySearch.partialRatio(query, modAbbreviation) { it.lowercase() }
//            .run { filterAndAdd(modAbbreviation) }
//    }
//
//    if (modName != null) {
//        FuzzySearch.partialRatio(query, modName) { it.lowercase() }
//            .run { filterAndAdd(modName) }
//    }
//
//    FuzzySearch.partialRatio(query, variant.modInfo.id) { it.lowercase() }
//        .run { filterAndAdd(variant.modInfo.id) }
//    FuzzySearch.partialRatio(query, variant.modInfo.version.toString()) { it.lowercase() }
//        .run { filterAndAdd(variant.modInfo.version.toString()) }
//
//    if (modAuthor != null) {
//        FuzzySearch.partialRatio(query, modAuthor) { it.lowercase() }
//            .run { filterAndAdd(modAuthor) }
//    }
//
//    // Cut the importance of this, so that it takes a strong match on description to make an impact.
//    // Only use it after a few chars, not right away.
//    val description = variant.modInfo.description
//    if (query.length >= 4 && description != null) {
//        (FuzzySearch.partialRatio(query, description) - 29).coerceAtLeast(0)
//            .run { filterAndAdd(description) }
//    }
//
//    Timber.d { "${variant.modInfo.name}'s match of '$query' had a total score of ${results.values.sum()} and single highest of ${results.values.maxOrNull()}." }
//    return variant.mod(access) to results
//}

fun splitSearchQuery(query: String) = query
    .split(Regex(""" *[,;|] *"""))
    .filter { it.isNotBlank() }