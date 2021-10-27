package config

import com.squareup.moshi.Moshi
import util.SmolWindowState

class UIConfig(moshi: Moshi) : Config(moshi) {
    var windowState: SmolWindowState? by pref(prefKey = "windowState", defaultValue = null)
}
