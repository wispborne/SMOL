package config

import VRAM_CHECKER_RESULTS_PATH
import com.google.gson.Gson
import model.SmolId

class VramCheckerResults(gson: Gson) :
    Config(
        gson, JsonFilePrefStorage(
            gson = gson,
            file = VRAM_CHECKER_RESULTS_PATH
        )
    ) {
    var bytesPerVariant: Map<SmolId, Long> by pref(prefKey = "bytesPerVariant", defaultValue = emptyMap())
}