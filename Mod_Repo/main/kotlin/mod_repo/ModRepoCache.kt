package mod_repo

import utilities.Config
import utilities.InMemoryPrefStorage
import utilities.Jsanity
import utilities.JsonFilePrefStorage

class ModRepoCache(gson: Jsanity) : Config(
    prefStorage = InMemoryPrefStorage(
        JsonFilePrefStorage(
            gson = gson,
            file = location
        )
    )
) {
    companion object {
        val location = CONFIG_FOLDER_DEFAULT.resolve("ModRepo.json")
    }

    var lastUpdated: String? by pref(
        prefKey = "lastUpdated",
        defaultValue = null
    )
    var items by pref<List<ScrapedMod>>(prefKey = "items", defaultValue = emptyList())
}