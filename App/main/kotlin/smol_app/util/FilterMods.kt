package smol_app.util

import me.xdrop.fuzzywuzzy.FuzzySearch
import mod_repo.ScrapedMod
import org.tinylog.Logger
import smol_access.model.Mod

internal fun filterMods(query: String, mods: List<Mod>): List<Mod> {
    val split = splitSearchQuery(query)

    return split
        .flatMap { filterStr ->
            mods
                .asSequence()
                .map { mod ->
                    val results = mutableMapOf<String, Int>() // match field name and value
                    val variant =
                        mod.findFirstEnabled ?: mod.findHighestVersion ?: return@map mod to mapOf<String, Int>()

                    fun Int.filterAndAdd(name: String) {
                        results += name to this
                        Logger.info { "  ${variant.modInfo.name} has a score of $this for '$filterStr' in text \"${name}\"." }
                    }

                    val modAbbreviation = variant.modInfo.name.abbreviate()

                    if (modAbbreviation.length > 1) {
                        FuzzySearch.partialRatio(filterStr, modAbbreviation) { it.lowercase() }
                            .run { filterAndAdd(modAbbreviation) }
                    }

                    FuzzySearch.partialRatio(filterStr, variant.modInfo.name) { it.lowercase() }
                        .run { filterAndAdd(variant.modInfo.name) }
                    FuzzySearch.partialRatio(filterStr, variant.modInfo.id) { it.lowercase() }
                        .run { filterAndAdd(variant.modInfo.id) }
                    FuzzySearch.partialRatio(filterStr, variant.modInfo.version.toString()) { it.lowercase() }
                        .run { filterAndAdd(variant.modInfo.version.toString()) }
                    FuzzySearch.partialRatio(filterStr, variant.modInfo.author) { it.lowercase() }
                        .run { filterAndAdd(variant.modInfo.author) }

                    // Cut the importance of this, so that it takes a strong match on description to make an impact.
                    // Only use it after a few chars, not right away.
                    if (filterStr.length >= 4) {
                        (FuzzySearch.partialRatio(filterStr, variant.modInfo.description) - 29).coerceAtLeast(0)
                            .run { filterAndAdd(variant.modInfo.description) }
                    }

                    Logger.info { "${variant.modInfo.name}'s match of '$filterStr' had a total score of ${results.values.sum()} and single highest of ${results.values.maxOrNull()}." }
                    return@map mod to results
                }
                .filter { it.second.any { it.value > 70 } }
                .sortedWith(compareByDescending<Pair<Mod, Map<String, Int>>> { it.second.maxOf { it.value } }
                    .thenByDescending {
                        it.first.findHighestVersion?.modInfo?.name ?: it.first.id
                    }) // Sort by highest match
                .map { it.first }
                .toList()
        }
        .distinctBy { it.id }
}


internal fun filterModPosts(query: String, mods: List<ScrapedMod>): List<ScrapedMod> {
    val split = splitSearchQuery(query)

    return split
        .flatMap { filterStr ->
            mods
                .asSequence()
                .map { mod ->
                    val results = mutableMapOf<String, Int>() // match field name and value

                    fun Int.filterAndAdd(name: String) {
                        results += name to this
                        Logger.info { "  ${mod.name} has a score of $this for '$filterStr' in text \"${name}\"." }
                    }

                    val modAbbreviation = mod.name.abbreviate()

                    if (modAbbreviation.length > 1) {
                        FuzzySearch.partialRatio(filterStr, modAbbreviation) { it.lowercase() }
                            .run { filterAndAdd(modAbbreviation) }
                    }

                    FuzzySearch.partialRatio(filterStr, mod.name) { it.lowercase() }
                        .run { filterAndAdd(mod.name) }
                    FuzzySearch.partialRatio(filterStr, mod.authors) { it.lowercase() }
                        .run { filterAndAdd(mod.authors) }
                    if (mod.category != null) {
                        FuzzySearch.partialRatio(filterStr, mod.category) { it.lowercase() }
                            .run { filterAndAdd(mod.category!!) }
                    }

                    Logger.info { "${mod.name}'s match of '$filterStr' had a total score of ${results.values.sum()} and single highest of ${results.values.maxOrNull()}." }
                    return@map mod to results
                }
                .filter { it.second.any { it.value > 70 } }
                .sortedWith(compareByDescending<Pair<ScrapedMod, Map<String, Int>>> { it.second.maxOf { it.value } }
                    .thenByDescending {
                        it.first.name
                    }) // Sort by highest match
                .map { it.first }
                .toList()
        }
}

private fun splitSearchQuery(query: String) = query
    .split(Regex(""" *[,;|] *"""))
    .filter { it.isNotBlank() }