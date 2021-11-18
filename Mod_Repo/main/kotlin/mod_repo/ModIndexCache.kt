package mod_repo

import com.google.gson.Gson
import utilities.Config
import java.nio.file.Path

class ModIndexCache(gson: Gson = Gson()) : Config(
    gson = gson,
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