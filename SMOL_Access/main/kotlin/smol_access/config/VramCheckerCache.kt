package smol_access.config

import smol_access.VRAM_CHECKER_RESULTS_PATH
import com.google.gson.Gson
import smol_access.model.ModId
import smol_access.model.SmolId
import utilities.Config

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
        val version: String,
        val bytesForMod: Long,
        val imageCount: Int,
    )
}