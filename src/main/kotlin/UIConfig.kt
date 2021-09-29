package config

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import util.SmolWindowState
import java.util.prefs.Preferences
import kotlin.reflect.KProperty

class UIConfig(moshi: Moshi) : Config(moshi) {
    var windowState: SmolWindowState? by pref(prefKey = "windowState", defaultValue = null)
}
