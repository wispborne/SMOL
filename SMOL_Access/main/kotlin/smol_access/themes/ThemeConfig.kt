package smol_access.themes

import utilities.Jsanity
import utilities.Config
import utilities.InMemoryPrefStorage
import utilities.JsonFilePrefStorage
import java.nio.file.Path

class ThemeConfig(gson: Jsanity, val path: Path) :
    Config(
        InMemoryPrefStorage(
            JsonFilePrefStorage(
                gson = gson,
                file = path
            )
        )
    ) {
    var themes: Map<String, Theme> by pref(prefKey = "themes", defaultValue = emptyMap())
}