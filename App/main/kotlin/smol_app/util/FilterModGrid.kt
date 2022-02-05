package smol_app.util

import com.github.androidpasswordstore.sublimefuzzy.Fuzzy
import me.xdrop.fuzzywuzzy.FuzzySearch
import smol_access.Access
import smol_access.model.Mod
import smol_access.model.ModVariant
import smol_app.util.Filter.searchMethod
import timber.ktx.Timber

object Filter {
    var searchMethod = FilterType.SublimeSearch
}

enum class FilterType {
    SublimeSearch,
    FuzzyWuzzySearch
}

internal fun filterModGrid(query: String, mods: List<Mod>, access: Access): List<Mod> {
    val split = splitSearchQuery(query)

    return split
        .flatMap { filterStr ->
            mods
                .asSequence()
                .map { mod ->
                    val variant = mod.findFirstEnabled ?: mod.findHighestVersion ?: return@map mod to mapOf()
                    if (searchMethod == FilterType.FuzzyWuzzySearch)
                        fuzzyWuzzyModSearch(
                            query = query,
                            variant = variant,
                            access = access
                        ) else sublimeFuzzyModSearch(
                        query = query,
                        variant = variant,
                        access = access
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

private fun sublimeFuzzyModSearch(query: String, variant: ModVariant, access: Access): Pair<Mod, Map<String, Int>> {
    val results = mutableMapOf<String, Int>() // match field name and value
    val modName = variant.modInfo.name
    val modAuthor = variant.modInfo.author

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

    if (modAuthor != null) {
        Fuzzy.fuzzyMatch(query, modAuthor)
            .run { filterAndAdd(modAuthor) }
    }

    Timber.d { "$modName's match of '$query' had a total score of ${results.values.sum()} and single highest of ${results.values.maxOrNull()}." }
    return variant.mod(access) to results
}

private fun fuzzyWuzzyModSearch(query: String, variant: ModVariant, access: Access): Pair<Mod, Map<String, Int>> {
    val results = mutableMapOf<String, Int>() // match field name and value
    val modName = variant.modInfo.name
    val modAuthor = variant.modInfo.author

    fun Int.filterAndAdd(name: String) {
        results += name to this
        Timber.d { "${Filter.searchMethod}: ${variant.modInfo.name} has a score of $this for '$query' in text \"${name}\"." }
    }

    val modAbbreviation = modName?.acronym() ?: ""

    if (modAbbreviation.length > 1) {
        FuzzySearch.partialRatio(query, modAbbreviation) { it.lowercase() }
            .run { filterAndAdd(modAbbreviation) }
    }

    if (modName != null) {
        FuzzySearch.partialRatio(query, modName) { it.lowercase() }
            .run { filterAndAdd(modName) }
    }

    FuzzySearch.partialRatio(query, variant.modInfo.id) { it.lowercase() }
        .run { filterAndAdd(variant.modInfo.id) }
    FuzzySearch.partialRatio(query, variant.modInfo.version.toString()) { it.lowercase() }
        .run { filterAndAdd(variant.modInfo.version.toString()) }

    if (modAuthor != null) {
        FuzzySearch.partialRatio(query, modAuthor) { it.lowercase() }
            .run { filterAndAdd(modAuthor) }
    }

    // Cut the importance of this, so that it takes a strong match on description to make an impact.
    // Only use it after a few chars, not right away.
    val description = variant.modInfo.description
    if (query.length >= 4 && description != null) {
        (FuzzySearch.partialRatio(query, description) - 29).coerceAtLeast(0)
            .run { filterAndAdd(description) }
    }

    Timber.d { "${variant.modInfo.name}'s match of '$query' had a total score of ${results.values.sum()} and single highest of ${results.values.maxOrNull()}." }
    return variant.mod(access) to results
}

fun splitSearchQuery(query: String) = query
    .split(Regex(""" *[,;|] *"""))
    .filter { it.isNotBlank() }