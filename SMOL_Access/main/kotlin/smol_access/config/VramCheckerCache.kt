package smol_access.config

import com.google.gson.Gson
import smol_access.Constants
import smol_access.model.ModId
import smol_access.model.SmolId
import utilities.Config
import utilities.JsonFilePrefStorage

class VramCheckerCache(gson: Gson) :
    Config(
        JsonFilePrefStorage(
            gson = gson,
            file = Constants.VRAM_CHECKER_RESULTS_PATH
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