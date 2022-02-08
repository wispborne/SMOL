package smol_access.business

import smol_access.Constants
import smol_access.config.GamePathManager
import smol_access.model.Mod
import smol_access.model.ModVariant
import smol_access.util.ManualReloadTrigger
import timber.Timber
import timber.ktx.i
import timber.ktx.w
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.moveTo

internal class Staging(
    private val gamePathManager: GamePathManager,
    private val modLoader: ModLoader,
    private val gameEnabledMods: GameEnabledMods,
    val manualReloadTrigger: ManualReloadTrigger
) {
    /**
     * Disables the mod.
     * - Removes it from /mods.
     * - Removes it from `enabled_mods.json`.
     */
    fun disableModVariant(modVariant: ModVariant): Result<Unit> {
        Timber.i { "Disabling variant ${modVariant.smolId}" }
        val modInfoFile = modVariant.modsFolderInfo.folder.resolve(Constants.MOD_INFO_FILE)

        if (!modInfoFile.exists()) {
            return Result.failure(RuntimeException("mod_info.json not found in ${modVariant.modsFolderInfo.folder.absolutePathString()}"))
        } else {
            kotlin.runCatching { modInfoFile.moveTo(modInfoFile.parent.resolve(Constants.MOD_INFO_FILE_DISABLED)) }
                .onFailure {
                    Timber.w(it)
                }
        }

        Timber.i { "Disabling ${modVariant.smolId}: renamed to ${Constants.MOD_INFO_FILE_DISABLED}." }

        if (modVariant.mod(modLoader).isEnabledInGame) {
            Timber.i { "Disabling mod ${modVariant.modInfo.id} as part of disabling variant ${modVariant.smolId}." }
            gameEnabledMods.disable(modVariant.modInfo.id)
        } else {
            Timber.i { "Mod ${modVariant.modInfo.id} was already disabled in enabled_mods.json and won't be disabled as part of disabling variant ${modVariant.smolId}." }
        }

        Timber.i { "Disabling ${modVariant.smolId}: success." }
        return Result.success((Unit))
    }

    suspend fun enableModVariant(modVariant: ModVariant): Result<Unit> {
        Timber.i { "Enabling mod variant ${modVariant.smolId}." }
        val modsFolderPath = gamePathManager.getModsPath()

        if (modsFolderPath?.exists() != true) {
            Timber.w { "Game mods path doesn't exist, unable to continue enabling ${modVariant.smolId}." }
            return Result.failure(NullPointerException())
        }

        if (modVariant.mod(modLoader).isEnabled(modVariant)) {
            Timber.i { "Already enabled!: $modVariant" }
            return Result.success(Unit)
        }

        val modInfoFile = modVariant.modsFolderInfo.folder.resolve(Constants.MOD_INFO_FILE_DISABLED)

        if (!modVariant.isModInfoEnabled) {
            kotlin.runCatching { modInfoFile.moveTo(modInfoFile.parent.resolve(Constants.MOD_INFO_FILE)) }
                .onFailure {
                    Timber.w(it)
                }
        }

        Timber.i { "Enabled mod for SMOL: $modVariant" }

        modLoader.reload(listOf(modVariant.modInfo.id))

        if (!modVariant.mod(modLoader).isEnabledInGame) {
            gameEnabledMods.enable(modVariant.modInfo.id)
        } else {
            Timber.i { "Mod was already enabled in enabled_mods.json." }
        }

        return Result.success((Unit))
    }

    fun disableMod(mod: Mod): Result<Unit> {
        Timber.i { "Disabling mod ${mod.id}." }
        mod.variants.forEach { modVariant ->
            disableModVariant(modVariant)
        }

        Timber.i { "Mod disabled: $mod" }
        return Result.success(Unit)
    }
}