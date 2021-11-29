package smol_app

import com.google.gson.Gson
import smol_access.Constants
import smol_app.util.SmolWindowState
import utilities.Config

class UIConfig(gson: Gson) : Config(
    gson, JsonFilePrefStorage(
        gson = gson,
        file = Constants.UI_CONFIG_PATH
    )
) {
    var windowState: SmolWindowState? by pref(prefKey = "windowState", defaultValue = null)
}
