/*
 * This file is distributed under the GPLv3. An informal description follows:
 * - Anyone can copy, modify and distribute this software as long as the other points are followed.
 * - You must include the license and copyright notice with each and every distribution.
 * - You may this software for commercial purposes.
 * - If you modify it, you must indicate changes made to the code.
 * - Any modifications of this code base MUST be distributed with the same license, GPLv3.
 * - This software is provided without warranty.
 * - The software author or license can not be held liable for any damages inflicted by the software.
 * The full license is available from <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package smol.access.business

import smol.access.Constants
import smol.access.config.GamePathManager
import smol.access.model.Mod
import smol.access.model.ModVariant
import smol.access.util.ManualReloadTrigger
import smol.timber.Timber
import smol.timber.ktx.i
import smol.timber.ktx.w
import smol.utilities.IOLock
import smol.utilities.asList
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isReadable
import kotlin.io.path.moveTo

internal class Staging(
    private val gamePathManager: GamePathManager,
    private val modsCache: ModsCache,
    private val gameEnabledMods: GameEnabledMods,
    val manualReloadTrigger: ManualReloadTrigger
) {
    /**
     * Disables the mod.
     * - Optionally adds `.disabled` to the `mod_info.json` filename.
     * - Optionally removes it from `enabled_mods.json`.
     */
    fun disableModVariant(
        modVariant: ModVariant,
        changeFileExtension: Boolean = false,
        disableInVanillaLauncher: Boolean = true
    ): Result<Unit> {
        Timber.i { "Disabling variant ${modVariant.smolId}" }
        val modInfoFile = modVariant.modsFolderInfo.folder.resolve(Constants.UNBRICKED_MOD_INFO_FILE)

        if (!modInfoFile.exists()) {
            return Result.failure(RuntimeException("mod_info.json not found in ${modVariant.modsFolderInfo.folder.absolutePathString()}"))
        }

        if (changeFileExtension) {
            IOLock.write {
                runCatching { modInfoFile.moveTo(modInfoFile.parent.resolve(Constants.MOD_INFO_FILE_DISABLED_NAMES.first())) }
                    .onFailure {
                        Timber.w(it)
                    }
                    .onSuccess {
                        Timber.i { "Disabled ${modVariant.smolId}: renamed to ${Constants.MOD_INFO_FILE_DISABLED_NAMES.first()}." }
                    }
            }
        }


        if (disableInVanillaLauncher) {
            val mod = modVariant.mod(modsCache) ?: return Result.failure(NullPointerException())
            if (mod.isEnabledInGame) {
                Timber.i { "Disabling mod ${modVariant.modInfo.id} as part of disabling variant ${modVariant.smolId}." }
                gameEnabledMods.disable(modVariant.modInfo.id)
            } else {
                Timber.i { "Mod ${modVariant.modInfo.id} was already disabled in enabled_mods.json and won't be disabled as part of disabling variant ${modVariant.smolId}." }
            }
        }

        Timber.i { "Disabling ${modVariant.smolId}: success." }
        return Result.success((Unit))
    }

    suspend fun enableModVariant(
        modVariant: ModVariant,
        modLoader: ModLoader,
        enableInVanillaLauncher: Boolean = true
    ): Result<Unit> {
        Timber.i { "Enabling mod variant ${modVariant.smolId}." }
        val modsFolderPath = gamePathManager.getModsPath()

        if (modsFolderPath?.exists() != true) {
            Timber.w { "Game mods path doesn't exist, unable to continue enabling ${modVariant.smolId}." }
            return Result.failure(NullPointerException())
        }

        if (modVariant.mod(modsCache)?.isEnabled(modVariant) == true) {
            Timber.i { "Already enabled!: $modVariant" }
            return Result.success(Unit)
        }

        // Look for any disabled mod_info filenames.
        val disabledModInfoFiles = Constants.MOD_INFO_FILE_DISABLED_NAMES.map {
            modVariant.modsFolderInfo.folder.resolve(it)
        }
            .filter { it.isReadable() }

        // And disable them.
        IOLock.write {
            if (!modVariant.isModInfoEnabled) {
                disabledModInfoFiles.forEach { modInfoFile ->
                    runCatching { modInfoFile.moveTo(modInfoFile.parent.resolve(Constants.UNBRICKED_MOD_INFO_FILE)) }
                        .onFailure {
                            Timber.w(it)
                        }
                }
            }

        }
        Timber.i { "Enabled mod for SMOL: $modVariant" }

        if (enableInVanillaLauncher) {
            modLoader.reload(listOf(modVariant.modInfo.id))

            if (modVariant.mod(modsCache)?.isEnabledInGame != true) {
                gameEnabledMods.enable(modVariant.modInfo.id)
            } else {
                Timber.i { "Mod was already enabled in enabled_mods.json." }
            }
        }

        return Result.success((Unit))
    }

    suspend fun disableMod(mod: Mod, modLoader: ModLoader): Result<Unit> {
        Timber.i { "Disabling mod ${mod.id}." }
        val highestVersion = mod.findHighestVersion

        mod.variants.forEach { modVariant ->
            // Don't rename the variant with the highest version, we want that to appear in the vanilla launcher.
            disableModVariant(modVariant = modVariant, changeFileExtension = modVariant != highestVersion)
        }

        // If all of the mod variants are bricked, rename a mod_info.json file to make the highest version visible in the vanilla launcher.
        modLoader.reload(listOf(mod.id))
        modsCache.mods.value?.mods?.firstOrNull { it.id == mod.id }?.let { modAfterReload ->
            ensureLatestDisabledVariantIsVisibleInVanillaLauncher(modAfterReload.asList(), modLoader)
        }

        Timber.i { "Mod disabled: $mod" }
        return Result.success(Unit)
    }

    suspend fun ensureLatestDisabledVariantIsVisibleInVanillaLauncher(
        mods: List<Mod>,
        modLoader: ModLoader
    ) {
        mods.forEach { mod ->
            val highestVersion = mod.findHighestVersion

            if (highestVersion != null
                && mod.enabledVariants.none()
                && mod.variants.none {
                    it.modsFolderInfo.folder.resolve(Constants.UNBRICKED_MOD_INFO_FILE).exists()
                }
            ) {
                Timber.i { "Unbricked ${highestVersion.modsFolderInfo.folder} mod info file to be visible in vanilla launcher." }
                enableModVariant(highestVersion, modLoader, enableInVanillaLauncher = false)
            }
        }
    }
}