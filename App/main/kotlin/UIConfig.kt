import com.google.gson.Gson
import config.Config
import util.SmolWindowState

class UIConfig(gson: Gson) : Config(
    gson, JsonFilePrefStorage(
        gson = gson,
        file = UICONFIG_PATH
    )
) {
    var windowState: SmolWindowState? by pref(prefKey = "windowState", defaultValue = null)
}
