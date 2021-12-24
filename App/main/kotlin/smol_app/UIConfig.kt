package smol_app

import smol_access.Constants
import smol_app.util.SmolWindowState
import utilities.Config
import utilities.InMemoryPrefStorage
import utilities.Jsanity
import utilities.JsonFilePrefStorage

class UIConfig(gson: Jsanity) : Config(
    InMemoryPrefStorage(
        JsonFilePrefStorage(
        gson = gson,
        file = Constants.UI_CONFIG_PATH
    ))
) {
    var windowState: SmolWindowState? by pref(prefKey = "windowState", defaultValue = null)
    var modBrowserState: ModBrowserState? by pref(prefKey = "modBrowserState", defaultValue = null)
}

data class ModBrowserState(
    val modListWidthPercent: Float
)