package smol_app.util

import dev.sphericalkat.sublimefuzzy.Fuzzy
import me.xdrop.fuzzywuzzy.FuzzySearch
import mod_repo.ScrapedMod
import org.tinylog.Logger
import timber.ktx.Timber

internal fun filterModPosts(query: String, mods: List<ScrapedMod>): List<ScrapedMod> {
    val split = splitSearchQuery(query)

    return split
        .flatMap { filterStr ->
            mods
                .asSequence()
                .map { mod ->
                    if (Filter.searchMethod == FilterType.FuzzyWuzzySearch)
                        fuzzyWuzzyModPostSearch(
                            query = query,
                            mod = mod
                        ) else sublimeFuzzyModPostSearch(
                        query = query,
                        mod = mod
                    )
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

private fun sublimeFuzzyModPostSearch(query: String, mod: ScrapedMod): Pair<ScrapedMod, MutableMap<String, Int>> {
    val results = mutableMapOf<String, Int>() // match field name and value

    fun Pair<Boolean, Int>.filterAndAdd(name: String) {
        Timber.d { "${Filter.searchMethod}: ${mod.name} has a score of $this for '$query' in text \"${name}\" with match status: ${this.first}." }

        if (this.first) {
            results += name to this.second
        }
    }

    val modAbbreviation = mod.name.acronym()

    if (modAbbreviation.length > 1) {
        Fuzzy.fuzzyMatch(query, modAbbreviation)
            .run { filterAndAdd(modAbbreviation) }
    }

    Fuzzy.fuzzyMatch(query, mod.name)
        .run { filterAndAdd(mod.name) }
    Fuzzy.fuzzyMatch(query, mod.authors)
        .run { filterAndAdd(mod.authors) }
    Fuzzy.fuzzyMatch(query, mod.source.name)
        .run { filterAndAdd(mod.source.name) }
    if (mod.categories != null) {
        Fuzzy.fuzzyMatch(query, mod.categories.joinToString())
            .run { filterAndAdd(mod.categories.joinToString()) }
    }

    Timber.d { "${mod.name}'s match of '$query' had a total score of ${results.values.sum()} and single highest of ${results.values.maxOrNull()}." }
    return mod to results
}

private fun fuzzyWuzzyModPostSearch(query: String, mod: ScrapedMod): Pair<ScrapedMod, MutableMap<String, Int>> {
    val results = mutableMapOf<String, Int>() // match field name and value

    fun Int.filterAndAdd(name: String) {
        results += name to this
        Logger.info { "${Filter.searchMethod}: ${mod.name} has a score of $this for '$query' in text \"${name}\"." }
    }

    val modAbbreviation = mod.name.acronym()

    if (modAbbreviation.length > 1) {
        FuzzySearch.partialRatio(query, modAbbreviation) { it.lowercase() }
            .run { filterAndAdd(modAbbreviation) }
    }

    FuzzySearch.partialRatio(query, mod.name) { it.lowercase() }
        .run { filterAndAdd(mod.name) }
    FuzzySearch.partialRatio(query, mod.authors) { it.lowercase() }
        .run { filterAndAdd(mod.authors) }
    FuzzySearch.partialRatio(query, mod.source.name) { it.lowercase() }
        .run { filterAndAdd(mod.source.name) }
    if (mod.categories != null) {
        FuzzySearch.partialRatio(query, mod.categories.joinToString()) { it.lowercase() }
            .run { filterAndAdd(mod.categories.joinToString()) }
    }

    Logger.info { "${mod.name}'s match of '$query' had a total score of ${results.values.sum()} and single highest of ${results.values.maxOrNull()}." }
    return mod to results
}