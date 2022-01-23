package mod_repo

import utilities.Config
import utilities.InMemoryPrefStorage
import utilities.Jsanity
import utilities.JsonFilePrefStorage
import java.time.Instant
import java.time.temporal.ChronoUnit

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

    var lastUpdated by pref(
        prefKey = "lastUpdated",
        defaultValue = Instant.now().truncatedTo(ChronoUnit.MINUTES).toString()
    )
    var items by pref<List<ScrapedMod>>(prefKey = "items", defaultValue = emptyList())
}