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

package smol.access

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import smol.access.business.*
import smol.access.config.AppConfig
import smol.access.config.GamePathManager
import smol.access.config.SettingsPath
import smol.access.model.Mod
import smol.access.model.ModId
import smol.access.model.ModInfo
import smol.access.model.ModVariant
import smol.timber.ktx.Timber
import smol.utilities.*
import smol.utilities.deleteRecursively
import java.nio.file.Path
import kotlin.io.path.*

class Access internal constructor(
    private val staging: Staging,
    private val modLoader: ModLoader,
    private val archives: Archives,
    private val appConfig: AppConfig,
    private val gamePathManager: GamePathManager,
    private val modsCache: ModsCache
) {
    private val scope = CoroutineScope(Job())

    init {
        scope.launch {
            modsFlow.collectLatest { modListUpdate ->
                runCatching {
                    modListUpdate?.added.orEmpty().forEach { modVariant ->
                        // Back up mod if feature is enabled and the backup file doesn't exist.
                        if (appConfig.areModArchivesEnabled && modVariant.backupFile?.exists() != true) {
                            backupMod(modVariant)
                        }
                    }
                }
                    .onFailure { Timber.w(it) }
            }
        }
    }

    /**
     * Checks the /mods and archives paths and sets them if they don't exist.
     */
    fun checkAndSetDefaultPaths(platform: Platform) {
        if (gamePathManager.path.value == null) {
            gamePathManager.getDefaultStarsectorPath(platform)?.absolutePath?.run {
                gamePathManager.set(this)
            }
        }

        // If the mod archive path doesn't exist/isn't set, and the game path is set, set the archive path
        // and create the folder.
        if (appConfig.areModArchivesEnabled
            && appConfig.modBackupPath?.toPathOrNull()?.exists() != true
            && gamePathManager.path.value?.exists() == true
        ) {
            appConfig.modBackupPath =
                gamePathManager.path.value?.resolve(Constants.ARCHIVES_FOLDER_NAME)?.absolutePathString()

            IOLock.write {
                runCatching {
                    if (!appConfig.modBackupPath.toPathOrNull()?.exists()!!) {
                        appConfig.modBackupPath.toPathOrNull()?.createDirectories()
                    }
                }
                    .onFailure {
                        Timber.w(it) { "Unable to create mod archive folder at ${appConfig.modBackupPath}." }
                    }
            }
        }

        Timber.i { "Game: ${appConfig.gamePath}\nMod Archive: ${appConfig.modBackupPath}" }
    }

    /**
     * @return A list of errors, or empty if no errors.
     */
    fun validatePaths(
        newGamePath: Path? = gamePathManager.path.value,
        archivesPath: Path? = appConfig.modBackupPath.toPathOrNull()
    ): SmolResult<Unit, Map<SettingsPath, List<String>>> {
        val errors: Map<SettingsPath, MutableList<String>> = mapOf(
            SettingsPath.Game to mutableListOf(),
            SettingsPath.Archives to mutableListOf()
        )

        IOLock.read {
            // Game path
            if (newGamePath == null) {
                errors[SettingsPath.Game]?.add("Game path invalid or not set!")
            } else if (!newGamePath.exists()) {
                errors[SettingsPath.Game]?.add("Game path '$newGamePath' doesn't exist!")
            } else {
                val hasGameExe = gamePathManager.getGameExeFolderPath(newGamePath)?.exists() ?: false
                val hasGameCoreExe = gamePathManager.getGameCoreFolderPath(newGamePath)?.exists() ?: false

                if (currentPlatform == Platform.Windows && !hasGameExe) {
                    errors[SettingsPath.Game]?.add("Folder 'starsector' doesn't exist!")
                }

                if (!hasGameCoreExe) {
                    errors[SettingsPath.Game]?.add(
                        "Folder '${
                            gamePathManager.getGameCoreFolderPath()
                                ?.relativeTo(gamePathManager.path.value ?: Path("/"))
                        }' not found!"
                    )
                }
            }

            // Archives path
            if (appConfig.areModArchivesEnabled) {
                if (archivesPath == null) {
                    errors[SettingsPath.Archives]?.add("Archives path invalid or not set.\nSet it or disable Mod Archival.")
                } else if (!archivesPath.exists()) {
                    errors[SettingsPath.Archives]?.add("Archives path '$archivesPath' doesn't exist!")
                } else if (!archivesPath.exists()) {
                    errors[SettingsPath.Archives]?.add("Folder '${Constants.ARCHIVES_FOLDER_NAME}' not found!")
                } else {
                }
            }
        }

        return if (errors.flatMap { it.value }.none()) SmolResult.Success(Unit)
        else SmolResult.Failure(errors)
    }

    val modsFlow: StateFlow<ModListUpdate?>
        get() = modsCache.mods
    val mods: List<Mod>
        get() = modsCache.mods.value?.mods.orEmpty()
    val areModsLoading = modLoader.isLoading

    val modModificationState = MutableStateFlow<Map<ModId, ModModificationState>>(emptyMap())

    sealed class ModModificationState {
        data object Ready : ModModificationState()
        data object DisablingVariants : ModModificationState()
        data object DeletingVariants : ModModificationState()
        data object EnablingVariant : ModModificationState()
        data object BackingUpVariant : ModModificationState()
    }

    /**
     * Reads all mods from /mods, staging, and archive folders.
     */
    suspend fun reload(modIds: List<ModId>? = null) = modLoader.reload()

    /**
     * Given an arbitrary file, find and install the associated mod into the given folder.
     * @param inputFile A file or folder to try to install.
     * @param destinationFolder The folder to place the result into. Not the mod folder, but the parent of that (eg /mods).
     * Usually, this should be `gamePathManager.getModsPath()`.
     */
    suspend fun installFromUnknownSource(
        inputFile: Path,
        destinationFolder: Path,
        promptUserToReplaceExistingFolder: suspend (modInfo: ModInfo) -> Boolean
    ) {
        archives.installFromUnknownSource(
            inputFile = inputFile,
            destinationFolder = destinationFolder,
            existingMods = modsCache.mods.value?.mods.orEmpty(),
            promptUserToReplaceExistingFolder = promptUserToReplaceExistingFolder
        )
    }

    /**
     * Changes the active mod variant, or disables all if [modVariant] is `null`.
     */
    suspend fun changeActiveVariant(mod: Mod, modVariant: ModVariant?): Result<Unit> {
        Timber.i { "Changing active variant of ${mod.id} to ${modVariant?.smolId}. (current: ${mod.findFirstEnabled?.smolId})." }
        try {
            val modVariantParent = modVariant?.mod(modsCache)
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
                .filter { it != modVariant }
                .also {
                    modModificationState.update {
                        it.toMutableMap().apply {
                            this[mod.id] =
                                ModModificationState.DisablingVariants
                        }
                    }
                }
                // We've enabled one variant, so any other variants need to be ignored by the vanilla launcher (changeFileExtension = true).
                .forEach { staging.disableModVariant(it, changeFileExtension = true) }

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
                    it.toMutableMap().apply {
                        this[mod.id] =
                            ModModificationState.EnablingVariant
                    }
                }
                staging.enableModVariant(modVariant, modLoader)
            } else {
                Result.success(Unit)
            }
        } finally {
            staging.manualReloadTrigger.trigger.emit("For mod ${mod.id}, staged variant: $modVariant.")
            modModificationState.update {
                it.toMutableMap().apply {
                    this[mod.id] =
                        ModModificationState.Ready
                }
            }
        }
    }

//    @Deprecated("Use changeActiveVariant instead, it disables other variants properly.")
//    private suspend fun enableModVariant(modToEnable: ModVariant): Result<Unit> {
//        try {
//            modModificationState.update {
//                it.toMutableMap().apply {
//                    val mod = modToEnable.mod(this@Access) ?: return Result.failure(NullPointerException())
//                    this[mod.id] =
//                        ModModificationState.EnablingVariant
//                }
//            }
//            return staging.enableModVariant(modToEnable, modLoader)
//        } finally {
//            staging.manualReloadTrigger.trigger.emit("Enabled mod: $modToEnable")
//            modModificationState.update {
//                it.toMutableMap().apply {
//                    val mod = modToEnable.mod(this@Access) ?: return Result.failure(NullPointerException())
//                    this[mod.id] =
//                        ModModificationState.Ready
//                }
//            }
//        }
//    }

    suspend fun disableMod(mod: Mod): Result<Unit> {
        try {
            modModificationState.update {
                it.toMutableMap().apply {
                    this[mod.id] =
                        ModModificationState.DisablingVariants
                }
            }
            return staging.disableMod(mod, modLoader)
        } finally {
            staging.manualReloadTrigger.trigger.emit("Disabled mod: $mod")
            modModificationState.update {
                it.toMutableMap().apply {
                    this[mod.id] =
                        ModModificationState.Ready
                }
            }
        }
    }

    suspend fun ensureLatestDisabledVariantIsVisibleInVanillaLauncher(
        mods: List<Mod>
    ) = staging.ensureLatestDisabledVariantIsVisibleInVanillaLauncher(mods, modLoader)

    suspend fun disableModVariant(
        modVariant: ModVariant,
        changeFileExtension: Boolean = false,
    ): Result<Unit> {
        val mod = modVariant.mod(this@Access) ?: return Result.failure(NullPointerException())
        try {
            modModificationState.update {
                it.toMutableMap().apply {
                    this[mod.id] =
                        ModModificationState.DisablingVariants
                }
            }
            return staging.disableModVariant(modVariant = modVariant, changeFileExtension = changeFileExtension)
        } finally {
            staging.manualReloadTrigger.trigger.emit("Disabled mod variant: $modVariant")
            modModificationState.update {
                it.toMutableMap().apply {
                    this[mod.id] =
                        ModModificationState.Ready
                }
            }
        }
    }

    suspend fun deleteVariant(modVariant: ModVariant, removeUncompressedFolder: Boolean) {
        Timber.i { "Deleting mod variant ${modVariant.smolId} folders. Remove staging/mods files? $removeUncompressedFolder." }

        // Back up mod if feature is enabled and the backup file doesn't exist.
        if (appConfig.areModArchivesEnabled && modVariant.backupFile?.exists() != true) {
            backupMod(modVariant)
        }

        trace(onFinished = { _, millis ->
            Timber.i { "Deleted mod variant ${modVariant.smolId} folders in ${millis}ms. Remove staging/mods files? $removeUncompressedFolder." }
        }) {
            val mod = modVariant.mod(this) ?: return
            try {
                modModificationState.update {
                    it.toMutableMap().apply {
                        this[mod.id] =
                            ModModificationState.DeletingVariants
                    }
                }
                IOLock.write(IOLocks.modFolderLock) {
                    if (removeUncompressedFolder) {
                        val gameModsFolder = modVariant.modsFolderInfo.folder

                        if (!gameModsFolder.exists()) {
                            Timber.w { "Unable to delete folder for variant ${modVariant.smolId}. File: $gameModsFolder." }
                        } else {
                            runCatching { gameModsFolder.deleteRecursively() }
                                .onFailure {
                                    Timber.e(it) { "Unable to delete game mods folder for variant ${modVariant.smolId}." }
                                }
                        }
                    }
                }
            } finally {
                staging.manualReloadTrigger.trigger.emit("Deleted mod variant: $modVariant")
                modModificationState.update {
                    it.toMutableMap().apply {
                        this[mod.id] =
                            ModModificationState.Ready
                    }
                }
            }
        }
    }

    /**
     * Creates a .7z archive of the given mod variant in the [AppConfig.modBackupPath] folder.
     */
    suspend fun backupMod(modVariant: ModVariant, overwriteExisting: Boolean = false): Archives.ArchiveResult? {
        val mod = modVariant.mod(this) ?: return null
        val modArchivePath = appConfig.modBackupPath.toPathOrNull() ?: return null
        val modArchiveFile = modArchivePath.resolve(modVariant.generateBackupFileName())
        var result: Archives.ArchiveResult? = null

        if (!overwriteExisting && modArchiveFile.exists()) {
            Timber.d { "Mod archive file already exists at '$modArchiveFile', skipping." }
            return null
        }

        Timber.i { "Backing up mod variant ${modVariant.smolId} to '$modArchiveFile'." }
        trace(onFinished = { _, millis ->
            Timber.i { "Backed up mod variant ${modVariant.smolId} to '$modArchiveFile' in ${millis}ms." }
        }) {
            try {
                modModificationState.update {
                    it.toMutableMap().apply {
                        this[mod.id] =
                            ModModificationState.BackingUpVariant
                    }
                }
                IOLock.read(IOLocks.modFolderLock) {
                    if (modArchiveFile.exists()) {
                        Timber.i { "Deleting existing mod archive file at '$modArchiveFile'." }
                        runCatching { modArchiveFile.deleteIfExists() }
                            .onFailure {
                                Timber.e(it) { "Unable to delete existing mod archive file at '$modArchiveFile'." }
                                return null
                            }
                    }

                    Timber.i { "Creating mod archive file at '$modArchiveFile'." }
                    runCatching {
                        modArchiveFile.createFile()
                        result = archives.createArchive(
                            modVariant = modVariant,
                            destinationFile = modArchiveFile
                        )
                    }
                        .onFailure {
                            Timber.e(it) { "Unable to create mod archive file at '$modArchiveFile'." }
                        }
                }
            } finally {
                staging.manualReloadTrigger.trigger.emit("Backed up mod variant: $modVariant")
                modModificationState.update {
                    it.toMutableMap().apply {
                        this[mod.id] =
                            ModModificationState.Ready
                    }
                }
            }

            result?.errors?.forEach { Timber.w(it) }

            return result
        }
    }
}