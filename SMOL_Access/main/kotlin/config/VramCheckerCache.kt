package config

import VRAM_CHECKER_RESULTS_PATH
import com.google.gson.Gson
import model.ModId
import model.SmolId

class VramCheckerCache(gson: Gson) :
    Config(
        gson, JsonFilePrefStorage(
            gson = gson,
            file = VRAM_CHECKER_RESULTS_PATH
        )
    ) {
    var bytesPerVariant: Map<SmolId, Result>? by pref(prefKey = "bytesPerVariant", defaultValue = emptyMap())

    data class Result(
        val modId: ModId,
        val bytesForMod: Long,
        val imageCount: Int,
    )
}