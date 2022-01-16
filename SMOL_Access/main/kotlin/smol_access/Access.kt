package smol_access

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import smol_access.business.Archives
import smol_access.business.ModListUpdate
import smol_access.business.ModLoader
import smol_access.business.Staging
import smol_access.config.AppConfig
import smol_access.config.GamePath
import smol_access.config.Platform
import smol_access.config.SettingsPath
import smol_access.model.Mod
import smol_access.model.ModId
import smol_access.model.ModVariant
import timber.ktx.Timber
import utilities.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.*

class Access internal constructor(
    private val staging: Staging,
    private val config: AppConfig,
    private val modLoader: ModLoader,
    private val archives: Archives,
    private val appConfig: AppConfig,
    private val gamePath: GamePath
) {

    /**
     * Checks the /mods, archives, and staging paths and sets them to null if they don't exist.
     */
    fun checkAndSetDefaultPaths(platform: Platform) {
        if (appConfig.gamePath == null) {
            appConfig.gamePath = gamePath.getDefaultStarsectorPath(platform)?.absolutePath
        }

        if (appConfig.archivesPath.toPathOrNull()?.exists() != true) {
            appConfig.archivesPath = Constants.ARCHIVES_FOLDER_DEFAULT.absolutePathString()

            IOLock.write(IOLocks.everythingLock) {
                kotlin.runCatching {
                    appConfig.archivesPath.toPathOrNull()?.createDirectories()
                }
                    .onFailure { Timber.w(it) }
            }
        }

        archives.getArchivesManifest()
            .also { Timber.i { "Archives folder manifest: ${it?.manifestItems?.keys?.joinToString()}" } }

        if (appConfig.stagingPath.toPathOrNull()?.exists() != true) {
            appConfig.stagingPath = Constants.STAGING_FOLDER_DEFAULT.absolutePathString()

            IOLock.write(IOLocks.everythingLock) {
                kotlin.runCatching {
                    appConfig.stagingPath.toPathOrNull()?.createDirectories()
                }
                    .onFailure { Timber.w(it) }
            }
        }

        Timber.i { "Game: ${appConfig.gamePath}" }
        Timber.i { "Archives: ${appConfig.archivesPath}" }
        Timber.i { "Staging: ${appConfig.stagingPath}" }
    }

    /**
     *
     *
     * @return A list of errors, or empty if no errors.
     */
    fun validatePaths(
        newGamePath: Path? = gamePath.get(),
        newArchivesPath: Path? = appConfig.archivesPath?.toPathOrNull(),
        newStagingPath: Path? = appConfig.stagingPath?.toPathOrNull()
    ): SmolResult<Unit, Map<SettingsPath, List<String>>> {
        val errors: Map<SettingsPath, MutableList<String>> = mapOf(
            SettingsPath.Game to mutableListOf(),
            SettingsPath.Archives to mutableListOf(),
            SettingsPath.Staging to mutableListOf()
        )

        IOLock.read {
            // Game path
            if (newGamePath?.exists() != true) {
                errors[SettingsPath.Game]?.add("Game path '$newGamePath' doesn't exist!")
            } else {
                var hasGameExe = false
                var hasGameCoreExe = false

                newGamePath.walk(maxDepth = 1)
                    .map { it.nameWithoutExtension.lowercase() }
                    .forEach {
                        if (it == "starsector") hasGameExe = true
                        if (it == "starsector-core") hasGameCoreExe = true
                    }

                if (!hasGameExe) {
                    errors[SettingsPath.Game]?.add("Folder 'starsector' doesn't exist!")
                }

                if (!hasGameCoreExe) {
                    errors[SettingsPath.Game]?.add("Folder 'starsector-core' doesn't exist!")
                }
            }


            // Archives path
            if (newArchivesPath?.exists() != true) {
                errors[SettingsPath.Archives]?.add("Archives path '$newArchivesPath' doesn't exist!")
            }


            // Staging path
            if (newStagingPath?.exists() != true) {
                errors[SettingsPath.Staging]?.add("Staging path '$newStagingPath' doesn't exist!")
            }

            // Ensure that the two folders with hardlinks, /mods and staging, are on the same partition.
            kotlin.runCatching {
                val folders =
                    listOfNotNull(newGamePath, newStagingPath)

                if (folders.size > 1) {
                    if (folders.distinctBy { it.mountOf() }.size > 1) {
                        // There should only be a single mount point, aka partition, for hardlinked folders.
                        Timber.e { "/mods and staging are on separate partitions. Folders: ${folders.joinToString()}." }
                        errors[SettingsPath.Staging]?.add("The staging folder must be on the same drive (partition) as your Starsector mods folder.")
                        errors[SettingsPath.Game]?.add("The Starsector mods folder must be on the same drive (partition) as your staging folder.")
                    }
                }
            }
                .onFailure { Timber.w(it) }
        }

        return if (errors.values.none()) SmolResult.Success(Unit)
        else SmolResult.Failure(errors)
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
        object Ready : ModModificationState()
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
        Timber.i { "Changing active variant of ${mod.id} to ${modVariant?.smolId}. (current: ${mod.findFirstEnabled?.smolId})." }
        try {
            val modVariantParent = modVariant?.mod(modLoader)
            if (modVariantParent != null && mod != modVariantParent) {
                val err = "Variant and mod were different! ${mod.id}, ${modVariant.smolId}"
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
            // or variants that in the mod folder while the mod itself is disabled
            // (except for the variant we want to actually enable, if that's already active).
            // There should only ever be one active but might as well be careful.

            mod.variants
                .filter { mod.isEnabled(it) || it.modsFolderInfo != null }
                .filter { it != modVariant }
                .also {
                    modModificationState.update {
                        it.toMutableMap().apply { this[mod.id] = ModModificationState.DisablingVariants }
                    }
                }
                .forEach { staging.disableModVariant(it) }

            return if (modVariant != null) {
                // Enable the one we want.
                // Slower: Reload, since we just disabled it
//                val freshModVariant =
                modLoader.reload(listOf(mod.id))?.mods?.flatMap { it.variants }
                    ?.first { it.smolId == modVariant.smolId }
                    ?: kotlin.run {
                        val error = "After disabling, couldn't find mod variant ${modVariant.smolId}."
                        Timber.w { error }
                        return Result.failure(RuntimeException(error))
                    }
                // Faster: Assume we disabled it and change the mod to be disabled.
//                modVariant.mod = modVariant.mod.copy(isEnabledInGame = false)
                modModificationState.update {
                    it.toMutableMap().apply { this[mod.id] = ModModificationState.EnablingVariant }
                }
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
            modModificationState.update {
                it.toMutableMap().apply { this[modToEnable.mod(this@Access).id] = ModModificationState.EnablingVariant }
            }
            return staging.enableModVariant(modToEnable)
        } finally {
            staging.manualReloadTrigger.trigger.emit("Enabled mod: $modToEnable")
            modModificationState.update {
                it.toMutableMap().apply { this[modToEnable.mod(this@Access).id] = ModModificationState.Ready }
            }
        }
    }

    suspend fun disableMod(mod: Mod): Result<Unit> {
        try {
            modModificationState.update {
                it.toMutableMap().apply { this[mod.id] = ModModificationState.DisablingVariants }
            }
            return staging.disableMod(mod)
        } finally {
            staging.manualReloadTrigger.trigger.emit("Mod unstaged: $mod")
            modModificationState.update { it.toMutableMap().apply { this[mod.id] = ModModificationState.Ready } }
        }
    }

    suspend fun disableModVariant(modVariant: ModVariant): Result<Unit> {
        val mod = modVariant.mod(this@Access)
        try {
            modModificationState.update {
                it.toMutableMap().apply { this[mod.id] = ModModificationState.DisablingVariants }
            }
            return staging.disableModVariant(modVariant)
        } finally {
            staging.manualReloadTrigger.trigger.emit("Disabled mod: $modVariant")
            modModificationState.update { it.toMutableMap().apply { this[mod.id] = ModModificationState.Ready } }
        }
    }

    fun deleteVariant(modVariant: ModVariant, removeArchive: Boolean, removeUncompressedFolder: Boolean) {
        Timber.i { "Deleting mod variant ${modVariant.smolId} folders. Remove archive? $removeArchive. Remove staging/mods files? $removeUncompressedFolder." }
        trace(onFinished = { _, millis ->
            Timber.i { "Deleted mod variant ${modVariant.smolId} folders in ${millis}ms. Remove archive? $removeArchive. Remove staging/mods files? $removeUncompressedFolder." }
        }) {
            IOLock.write(IOLocks.modFilesLock) {
                if (removeArchive) {
                    val archiveFolder = modVariant.archiveInfo?.folder

                    if (archiveFolder?.exists() != true) {
                        Timber.e { "Unable to delete archive folder for variant ${modVariant.smolId}. File: $archiveFolder." }
                    } else {
                        kotlin.runCatching { archiveFolder.deleteIfExists() }
                            .onFailure {
                                Timber.e(it) { "Unable to delete archive folder for variant ${modVariant.smolId}." }
                            }
                    }
                }

                if (removeUncompressedFolder) {
                    val stagingFolder = modVariant.stagingInfo?.folder

                    if (stagingFolder?.exists() != true) {
                        if (stagingFolder != null) {
                            // If staging folder is null, then it's fine not to delete it, we probably just want to delete the /mods folder.
                            Timber.w { "Unable to delete staging folder for variant ${modVariant.smolId}. File: $stagingFolder." }
                        }
                    } else {
                        kotlin.runCatching { stagingFolder.deleteRecursively() }
                            .onFailure {
                                Timber.e(it) { "Unable to delete staging folder for variant ${modVariant.smolId}." }
                            }
                    }

                    val gameModsFolder = modVariant.modsFolderInfo?.folder

                    if (gameModsFolder?.exists() != true) {
                        if (gameModsFolder != null) {
                            // If /mods folder is null, then it's fine not to delete it, we probably just want to delete the staging folder.
                            Timber.w { "Unable to delete staging folder for variant ${modVariant.smolId}. File: $gameModsFolder." }
                        }
                    } else {
                        kotlin.runCatching { gameModsFolder.deleteRecursively() }
                            .onFailure {
                                Timber.e(it) { "Unable to delete game mods folder for variant ${modVariant.smolId}." }
                            }
                    }
                }
            }
        }
    }
}