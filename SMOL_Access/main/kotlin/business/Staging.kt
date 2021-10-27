package business

import config.AppConfig
import config.GamePath
import model.Mod
import model.ModVariant
import org.tinylog.Logger
import util.IOLock
import util.ManualReloadTrigger
import util.mkdirsIfNotExist
import util.toFileOrNull
import java.io.File
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.createLinkPointingTo
import kotlin.io.path.createSymbolicLinkPointingTo

class Staging(
    private val config: AppConfig,
    private val gamePath: GamePath,
    private val modLoader: ModLoader,
    private val gameEnabledMods: GameEnabledMods,
    private val archives: Archives,
    private val manualReloadTrigger: ManualReloadTrigger
) {
    enum class LinkMethod {
        HardLink,
        Symlink // requires admin
    }

    var linkMethod = LinkMethod.HardLink

    fun getStagingPath() = config.stagingPath

    /**
     * @throws Exception
     */
    fun changePath(newPath: String) {
        IOLock.write {
            kotlin.runCatching {
                val newFolder = File(newPath)
                val oldFolder = File(config.stagingPath ?: return).also { if (!it.exists()) return }

                newFolder.mkdirsIfNotExist()

                Files.move(oldFolder.toPath(), newFolder.toPath(), StandardCopyOption.REPLACE_EXISTING)

                config.stagingPath = newPath
            }
                .onFailure { Logger.error(it) }
                .getOrThrow()
        }
    }

    /**
     * Changes the active mod variant, or disables all if `null` is set.
     */
    suspend fun changeActiveVariant(mod: Mod, modVariant: ModVariant?): Result<Unit> {
        try {
            if (modVariant?.mod != null && mod != modVariant.mod) {
                val err = "Variant and mod were different! ${mod.id}, ${modVariant.smolId}"
                Logger.info { err }
                return Result.failure(RuntimeException(err))
            }

            if (modVariant != null && mod.isEnabled(modVariant)) {
                // Check if this is the only active variant.
                // If there are somehow more than one active, the rest of the method will clean that up.
                if (mod.variants.count { mod.isEnabled(it) } <= 1) {
                    Logger.info { "Variant is already active, nothing to do! $modVariant" }
                    return Result.success(Unit)
                }
            }

            val activeVariants = mod.variants.filter { mod.isEnabled(it) }

            if (modVariant == null && activeVariants.none()) {
                Logger.info { "No variants active, nothing to do! $mod" }
                return Result.success(Unit)
            }

            // Disable all active mod variants.
            // There should only ever be one active but might as well be careful.
            mod.variants
                .filter { mod.isEnabled(it) }
                .forEach { disableInternal(it) }

            return if (modVariant != null) {
                // Enable the one we want.
                // Slower: Reload, since we just disabled it
//                val freshModVariant = modLoader.getMods().flatMap { it.variants }.first { it.smolId == modVariant.smolId }
                // Faster: Assume we disabled it and change the mod to be disabled.
                modVariant.mod = modVariant.mod.copy(isEnabledInGame = false)
                enableInternal(modVariant)
            } else {
                Result.success(Unit)
            }
        } finally {
            manualReloadTrigger.trigger.emit("For mod ${mod.id}, staged variant: $modVariant.")
        }
    }

    suspend fun stage(modVariant: ModVariant): Result<Unit> {
        try {
            return stageInternal(modVariant)
        } finally {
            manualReloadTrigger.trigger.emit("staged mod: $modVariant")
        }
    }

    suspend fun enable(modToEnable: ModVariant): Result<Unit> {
        try {
            return enableInternal(modToEnable)
        } finally {
            manualReloadTrigger.trigger.emit("Enabled mod: $modToEnable")
        }
    }

    suspend fun unstage(mod: Mod): Result<Unit> {
        try {
            return unstageInternal(mod)
        } finally {
            manualReloadTrigger.trigger.emit("Mod unstaged: $mod")
        }
    }

    suspend fun disable(modVariant: ModVariant): Result<Unit> {
        try {
            return disableInternal(modVariant)
        } finally {
            manualReloadTrigger.trigger.emit("Disabled mod: $modVariant")
        }
    }

    /**
     * Disables the mod.
     * - Removes it from /mods.
     * - Removes it from `enabled_mods.json`.
     */
    private suspend fun disableInternal(modVariant: ModVariant): Result<Unit> {
        // If it's not staged, stage it (to the staging folder) (but it'll stay disabled)
        if (modVariant.stagingInfo == null) {
            val stageResult = stageInternal(modVariant)

            // If it isn't staged, stop here. Since it's not archived or staged, removing it will delete it entirely.
            if (stageResult.isFailure) {
                return stageResult
            }
        }

        if (!modVariant.mod.isEnabled(modVariant)) {
            return Result.success(Unit)
        }

        if (modVariant.modsFolderInfo != null) {
            val result = removeFromModsFolder(modVariant)

            if (result != Result.success(Unit)) {
                return result
            }

            Logger.info { "Disabled mod for SMOL: $modVariant" }
        }

        if (modVariant.mod.isEnabledInGame) {
            gameEnabledMods.disable(modVariant.modInfo.id)
        }

        return Result.success((Unit))
    }

    private suspend fun stageInternal(modVariant: ModVariant): Result<Unit> {
        if (modVariant.stagingInfo != null) {
            Logger.debug { "Mod already staged! $modVariant" }
            return Result.success(Unit)
        }

        val stagingFolder = config.stagingPath.toFileOrNull()
            ?: return failLogging("No staging folder: $modVariant")

        kotlin.runCatching {
            // Try to stage the mod by unzipping its archive
            archives.extractMod(modVariant, stagingFolder)
        }
            .onFailure {
                // If we can't unzip the archive, see if it's in the /mods folder, we can get it from there.
                if (modVariant.modsFolderInfo != null && modVariant.modsFolderInfo.folder.exists()) {
                    val linkFoldersResult = linkFolders(
                        modVariant.modsFolderInfo.folder,
                        File(gamePath.getModsPath(), modVariant.generateVariantFolderName())
                    )

                    if (linkFoldersResult.isFailure) {
                        return failLogging(linkFoldersResult.exceptionOrNull()?.message ?: "")
                    } else {
                        Logger.debug { "Mod staged from the /mods folder: $modVariant" }
                    }
                } else {
                    return failLogging(it.message ?: "")
                }
            }

        return Result.success(Unit)
    }

    private suspend fun enableInternal(modVariant: ModVariant): Result<Unit> {
        if (modVariant.mod.isEnabled(modVariant)) {
            Logger.info { "Already enabled!: $modVariant" }
            return Result.success(Unit)
        }

        if (modVariant.modsFolderInfo == null) {
            val result = linkFromStagingToGameMods(modVariant)

            if (result != Result.success(Unit)) {
                return result
            }

            Logger.info { "Enabled mod for SMOL: $modVariant" }
        }

        if (!modVariant.mod.isEnabledInGame) {
            gameEnabledMods.enable(modVariant.modInfo.id)
        }

        return Result.success((Unit))
    }

    private suspend fun unstageInternal(mod: Mod): Result<Unit> {
        mod.variants.forEach { modVariant ->
            if (modVariant.stagingInfo == null || !modVariant.stagingInfo.folder.exists()) {
                Logger.debug { "Mod not staged! $modVariant" }
                return@forEach
            }

            if (modVariant.archiveInfo == null) {
                Logger.warn { "Cannot unstage mod not archived or else it'll be gone forever!: $modVariant" }
                return@forEach
            }

            // Make sure it's disabled before unstaging
            disableInternal(modVariant)

            IOLock.write {
                kotlin.runCatching {
                    modVariant.stagingInfo.folder.deleteRecursively()
                }
                    .onFailure { Logger.error(it) }
                    .getOrThrow()
            }
        }

        Logger.debug { "Mod unstaged: $mod" }
        return Result.success(Unit)
    }

    private suspend fun linkFromStagingToGameMods(modToEnable: ModVariant): Result<Unit> {
        var mod = modToEnable

        // If it's not staged, stage it first (from the archive).
        if (mod.stagingInfo == null || !mod.stagingInfo!!.folder.exists()) {
            stageInternal(mod)
            mod = modLoader.getMods()
                .asSequence()
                .flatMap { it.variants }
                .firstOrNull { modV -> modV.smolId == modToEnable.smolId }
                ?: return failLogging("Mod was removed: $mod")

            if (mod.stagingInfo == null) {
                return failLogging("Unable to stage mod $mod")
            }
        }

        if (!mod.stagingInfo!!.folder.exists()) {
            return failLogging("Mod is not staged $mod")
        }

        val sourceFolder = mod.stagingInfo!!.folder

        if (!sourceFolder.exists()) {
            return failLogging("Staging folder doesn't exist. ${sourceFolder.path}, $mod")
        }

        val destFolder = File(gamePath.getModsPath(), sourceFolder.name)
        return linkFolders(sourceFolder, destFolder)
    }

    private fun linkFolders(sourceFolder: File, destFolder: File): Result<Unit> {
        IOLock.write {
            destFolder.mkdirsIfNotExist()

            destFolder.deleteRecursively()
            destFolder.createNewFile()
            val failedFiles = mutableListOf<File>()
            val succeededFiles = mutableListOf<File>()

            sourceFolder.walkTopDown().forEach { sourceFile ->
                //        listOf(sourceFolder).forEach { sourceFile ->
                //            if (sourceFile.path == sourceFolder.path) return@forEach
                val sourceRelativePath = Path.of(sourceFile.toRelativeString(sourceFolder))
                val destFile = File(destFolder.absolutePath, sourceRelativePath.toString())

                if (!sourceFile.exists()) {
                    failedFiles += sourceFile
                    Logger.warn { "Couldn't create ${linkMethod.name}, as source didn't exist. ${sourceFile.absolutePath}" }
                }


                when {
                    sourceFile.isDirectory -> destFile.deleteRecursively()
                    sourceFile.isFile -> destFile.delete()
                }

                kotlin.runCatching {
                    when (linkMethod) {
                        LinkMethod.HardLink ->
                            when {
                                sourceFile.isDirectory -> destFile.mkdirsIfNotExist()
                                sourceFile.isFile -> destFile.toPath().createLinkPointingTo(sourceFile.toPath())
                                else -> Logger.warn { "Not sure what kind of file this is: $sourceFile" }
                            }
                        LinkMethod.Symlink -> destFile.toPath().createSymbolicLinkPointingTo(sourceFile.toPath())
                    }
                }
                    .onFailure { ex ->
                        failedFiles += sourceFile
                        Logger.warn(ex) {
                            "Error creating ${linkMethod.name}.\n" +
                                    "      Source: ${sourceFile.absolutePath}\n" +
                                    "      Dest: ${destFile.absolutePath}"
                        }
                        if (linkMethod == LinkMethod.HardLink && ex is AccessDeniedException) {
                            Logger.warn { "Remember that hard links cannot cross disk partitions." }
                        }
                    }
                    .onSuccess {
                        succeededFiles += sourceFile
                        Logger.trace { "Created ${linkMethod.name} at ${destFile.absolutePath}" }
                    }
                    .getOrThrow()
            }

            if (failedFiles.any()) {
                Logger.warn { "Failed to create links/folders for ${failedFiles.count()} files in ${destFolder.absolutePath}." }
            }

            Logger.info { "Created links/folders for ${succeededFiles.count()} files in ${destFolder.absolutePath}." }
        }

        return Result.success(Unit)
    }

    /**
     * Removes the mod folder/files from the game /mods folder.
     */
    private fun removeFromModsFolder(modVariant: ModVariant): Result<Unit> {
        if (modVariant.modsFolderInfo == null) {
            Logger.warn { "Not found in /mods folder: ${modVariant.smolId}." }
            return Result.success(Unit)
        }

        val modsFolderInfo = modVariant.modsFolderInfo

        if (!modsFolderInfo.folder.exists()) {
            Logger.warn { "Nothing to remove. Folder doesn't exist in /mods. $modVariant" }
            return Result.success(Unit)
        }

        IOLock.write {
            kotlin.runCatching {
                if (!modsFolderInfo.folder.deleteRecursively()) {
                    Logger.warn { "Error deleting ${modsFolderInfo.folder.absolutePath}. Marking for deletion on exit." }
                    modsFolderInfo.folder.deleteOnExit()
                }
            }
                .onFailure {
                    Logger.error(it)
                    return Result.failure(it)
                }
                .getOrThrow()
        }

        return Result.success(Unit)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun failLogging(error: String): Result<Unit> {
        Logger.warn { error }
        return Result.failure(RuntimeException(error))
    }
}