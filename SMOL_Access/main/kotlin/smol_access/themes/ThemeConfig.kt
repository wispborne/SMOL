package smol_access.themes

import smol_access.Constants
import utilities.Jsanity
import utilities.Config
import utilities.InMemoryPrefStorage
import utilities.JsonFilePrefStorage

class ThemeConfig(gson: Jsanity) :
    Config(
        InMemoryPrefStorage(
            JsonFilePrefStorage(
                gson = gson,
                file = Constants.THEME_CONFIG_PATH
            )
        )
    ) {
    var themes: Map<String, Theme> by pref(prefKey = "themes", defaultValue = emptyMap())
}