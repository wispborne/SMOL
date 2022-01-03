package smol_access

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import smol_access.business.Archives
import smol_access.business.ModListUpdate
import smol_access.business.ModLoader
import smol_access.business.Staging
import smol_access.config.AppConfig
import smol_access.config.Platform
import smol_access.model.Mod
import smol_access.model.ModId
import smol_access.model.ModVariant
import utilities.IOLock
import timber.ktx.Timber
import utilities.mkdirsIfNotExist
import utilities.toFileOrNull
import utilities.toPathOrNull
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.absolutePathString

class Access internal constructor(
    private val staging: Staging,
    private val config: AppConfig,
    private val modLoader: ModLoader,
    private val archives: Archives
) {

    /**
     * Checks the /mods, archives, and staging paths and sets them to null if they don't exist.
     */
    fun checkAndSetDefaultPaths(platform: Platform) {
        val uiConfig: AppConfig = SL.appConfig

        if (!SL.gamePath.isValidGamePath(uiConfig.gamePath ?: "")) {
            uiConfig.gamePath = SL.gamePath.getDefaultStarsectorPath(platform)?.absolutePath
        }

        if (uiConfig.archivesPath.toFileOrNull()?.exists() != true) {
            uiConfig.archivesPath = Constants.ARCHIVES_FOLDER_DEFAULT.absolutePathString()
        }

        SL.archives.getArchivesManifest()
            .also { Timber.d { "Archives folder manifest: ${it?.manifestItems?.keys?.joinToString()}" } }

        if (uiConfig.stagingPath.toFileOrNull()?.exists() != true) {
            uiConfig.stagingPath = Constants.STAGING_FOLDER_DEFAULT.absolutePathString()
        }

        Timber.d { "Game: ${uiConfig.gamePath}" }
        Timber.d { "Archives: ${uiConfig.archivesPath}" }
        Timber.d { "Staging: ${uiConfig.stagingPath}" }
    }

    /**
     * Gets the current staging folder path.
     */
    fun getStagingPath() = config.stagingPath

    /**
     * @throws Exception
     */
    fun changeStagingPath(newPath: String) {
        IOLock.write {
            kotlin.runCatching {
                val newFolder = File(newPath)
                val oldFolder = File(config.stagingPath ?: return).also { if (!it.exists()) return }

                newFolder.mkdirsIfNotExist()

                Files.move(oldFolder.toPath(), newFolder.toPath(), StandardCopyOption.REPLACE_EXISTING)

                config.stagingPath = newPath
            }
                .onFailure { Timber.e(it) }
                .getOrThrow()
        }
    }

    val mods: StateFlow<ModListUpdate?>
        get() = modLoader.mods
    val areModsLoading = modLoader.isLoading

    val modModificationState = MutableStateFlow<Map<ModId, ModModificationState>>(emptyMap())

    sealed class ModModificationState {
        object Ready: ModModificationState()
        object DisablingVariants : ModModificationState()
        object EnablingVariant : ModModificationState()
    }

    /**
     * Reads all mods from /mods, staging, and archive folders.
     */
    suspend fun reload() = modLoader.reload()

    /**
     * Given an arbitrary file, find and install the associated mod into the given folder.
     * @param inputFile A file or folder to try to install.
     * @param destinationFolder The folder to place the result into. Not the mod folder, but the parent of that (eg /mods).
     * @param shouldCompressModFolder If true, will compress the mod as needed and place the archive in the folder.
     */
    suspend fun installFromUnknownSource(
        inputFile: Path,
        destinationFolder: Path = archives.getArchivesPath().toPathOrNull()!!,
        shouldCompressModFolder: Boolean
    ) =
        archives.installFromUnknownSource(inputFile, destinationFolder, shouldCompressModFolder)

    /**
     * Changes the active mod variant, or disables all if `null` is set.
     */
    suspend fun changeActiveVariant(mod: Mod, modVariant: ModVariant?): Result<Unit> {
        try {
            val modVariantParent = modVariant?.mod(modLoader)
            if (modVariantParent != null && mod != modVariantParent) {
                val err = "Variant and mod were different! ${mod.id}, ${modVariant!!.smolId}"
                Timber.i { err }
                return Result.failure(RuntimeException(err))
            }

            if (modVariant != null && mod.isEnabled(modVariant)) {
                // Check if this is the only active variant.
                // If there are somehow more than one active, the rest of the method will clean that up.
                if (mod.variants.count { mod.isEnabled(it) } <= 1) {
                    Timber.i { "Variant is already active, nothing to do! $modVariant" }
                    return Result.success(Unit)
                }
            }

            val activeVariants = mod.variants.filter { mod.isEnabled(it) }

            if (modVariant == null && activeVariants.none()) {
                Timber.i { "No variants active, nothing to do! $mod" }
                return Result.success(Unit)
            }

            // Disable all active mod variants
            // or variants that in the mod folder while the mod itself is disabled.
            // There should only ever be one active but might as well be careful.

            mod.variants
                .filter { mod.isEnabled(it) || it.modsFolderInfo != null }
                .also { modModificationState.update { it.toMutableMap().apply { this[mod.id] = ModModificationState.DisablingVariants } } }
                .forEach { staging.disableModVariant(it) }

            return if (modVariant != null) {
                // Enable the one we want.
                // Slower: Reload, since we just disabled it
//                val freshModVariant =
//                    modLoader.reload(listOf(mod.id))?.mods?.flatMap { it.variants }?.first { it.smolId == modVariant.smolId }
//                        ?: kotlin.run {
//                            val error = "After disabling, couldn't find mod variant ${modVariant.smolId}."
//                            Timber.w { error }
//                            return Result.failure(RuntimeException(error))
//                        }
                // Faster: Assume we disabled it and change the mod to be disabled.
//                modVariant.mod = modVariant.mod.copy(isEnabledInGame = false)
                modModificationState.update { it.toMutableMap().apply { this[mod.id] = ModModificationState.EnablingVariant } }
                staging.enableModVariant(modVariant)
            } else {
                Result.success(Unit)
            }
        } finally {
            staging.manualReloadTrigger.trigger.emit("For mod ${mod.id}, staged variant: $modVariant.")
            modModificationState.update { it.toMutableMap().apply { this[mod.id] = ModModificationState.Ready } }
        }
    }

    suspend fun stageModVariant(modVariant: ModVariant): Result<Unit> {
        try {
            return staging.stageInternal(modVariant)
        } finally {
            staging.manualReloadTrigger.trigger.emit("staged mod: $modVariant")
        }
    }

    suspend fun enableModVariant(modToEnable: ModVariant): Result<Unit> {
        try {
            modModificationState.update { it.toMutableMap().apply { this[modToEnable.mod(this@Access).id] = ModModificationState.EnablingVariant } }
            return staging.enableModVariant(modToEnable)
        } finally {
            staging.manualReloadTrigger.trigger.emit("Enabled mod: $modToEnable")
            modModificationState.update { it.toMutableMap().apply { this[modToEnable.mod(this@Access).id] = ModModificationState.Ready } }
        }
    }

    suspend fun disableMod(mod: Mod): Result<Unit> {
        try {
            modModificationState.update { it.toMutableMap().apply { this[mod.id] = ModModificationState.DisablingVariants } }
            return staging.disableMod(mod)
        } finally {
            staging.manualReloadTrigger.trigger.emit("Mod unstaged: $mod")
            modModificationState.update { it.toMutableMap().apply { this[mod.id] = ModModificationState.Ready } }
        }
    }

    suspend fun disableModVariant(modVariant: ModVariant): Result<Unit> {
        val mod = modVariant.mod(this@Access)
        try {
            modModificationState.update { it.toMutableMap().apply { this[mod.id] = ModModificationState.DisablingVariants } }
            return staging.disableModVariant(modVariant)
        } finally {
            staging.manualReloadTrigger.trigger.emit("Disabled mod: $modVariant")
            modModificationState.update { it.toMutableMap().apply { this[mod.id] = ModModificationState.Ready } }
        }
    }

}