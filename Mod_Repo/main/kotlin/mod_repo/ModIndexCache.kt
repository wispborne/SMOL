package mod_repo

import com.google.gson.Gson
import utilities.Config
import utilities.JsonFilePrefStorage

class ModIndexCache(gson: Gson = Gson()) : Config(
    prefStorage = JsonFilePrefStorage(
        gson = gson,
        file = location
    )
) {
    companion object {
        val location = CONFIG_FOLDER_DEFAULT.resolve("modIndex.json")
    }

    var items by pref<List<ScrapedMod>>(prefKey = "items", defaultValue = emptyList())
}