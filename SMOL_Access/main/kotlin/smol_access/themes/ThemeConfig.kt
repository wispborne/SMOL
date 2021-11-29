package smol_access.themes

import com.google.gson.Gson
import smol_access.Constants
import utilities.Config

class ThemeConfig(gson: Gson) :
    Config(
        gson, JsonFilePrefStorage(
            gson = gson,
            file = Constants.THEME_CONFIG_PATH
        )
    ) {
    var themes: Map<String, Theme> by pref(prefKey = "themes", defaultValue = emptyMap())
}