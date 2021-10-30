import com.google.gson.Gson
import config.Config
import util.SmolWindowState
import java.nio.file.Paths

class UIConfig(gson: Gson) : Config(
    gson, JsonFilePrefStorage(
        gson = gson,
        file = Paths.get("").toFile().resolve("SMOL_UIConfig.json")
    )
) {
    var windowState: SmolWindowState? by pref(prefKey = "windowState", defaultValue = null)
}
