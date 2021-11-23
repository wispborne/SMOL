package smol_app

import smol_access.UICONFIG_PATH
import com.google.gson.Gson
import utilities.Config
import smol_app.util.SmolWindowState

class UIConfig(gson: Gson) : Config(
    gson, JsonFilePrefStorage(
        gson = gson,
        file = UICONFIG_PATH
    )
) {
    var windowState: SmolWindowState? by pref(prefKey = "windowState", defaultValue = null)
}
