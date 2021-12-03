package smol_access.business

import smol_access.Constants
import smol_access.config.AppConfig
import smol_access.config.GamePath
import smol_access.model.Mod
import smol_access.model.ModVariant
import smol_access.util.IOLock
import smol_access.util.ManualReloadTrigger
import timber.Timber
import timber.ktx.d
import timber.ktx.i
import timber.ktx.v
import timber.ktx.w
import utilities.deleteRecursively
import utilities.toPathOrNull
import utilities.trace
import utilities.walk
import java.nio.file.AccessDeniedException
import java.nio.file.Path
import kotlin.io.path.*

internal class Staging(
    private val config: AppConfig,
    private val gamePath: GamePath,
    private val modLoader: ModLoader,
    private val gameEnabledMods: GameEnabledMods,
    private val archives: Archives,
    val manualReloadTrigger: ManualReloadTrigger
) {
    enum class LinkMethod {
        HardLink, // requires admin on windows
        Symlink // requires admin
    }

    var linkMethod = LinkMethod.HardLink

    /**
     * Disables the mod.
     * - Removes it from /mods.
     * - Removes it from `enabled_mods.json`.
     */
    suspend fun disableInternal(modVariant: ModVariant): Result<Unit> {
        Timber.i { "Disabling variant ${modVariant.smolId}" }
        // If it's not staged, stage it (to the staging folder) (but it'll stay disabled)
        if (modVariant.stagingInfo == null) {
            val stageResult = stageInternal(modVariant)

            // If it isn't staged, stop here. Since it's not archived or staged, removing it will delete it entirely.
            if (stageResult.isFailure) {
                Timber.w(stageResult.exceptionOrNull()) { "Couldn't disable ${modVariant.smolId}, unable to stage." }
                return stageResult
            }
        }

        if (modVariant.modsFolderInfo != null) {
            val result = removeFromModsFolder(modVariant)

            if (result != Result.success(Unit)) {
                return result
            }

            Timber.d { "Disabling ${modVariant.smolId}: removed from /mods." }
        }

        if (modVariant.mod.isEnabledInGame) {
            Timber.d { "Disabling ${modVariant.smolId}: variant was enabled, disabling." }
            gameEnabledMods.disable(modVariant.modInfo.id)
        } else {
            Timber.d { "Disabling ${modVariant.smolId}: variant was not enabled." }
        }

        Timber.d { "Disabling ${modVariant.smolId}: success." }
        return Result.success((Unit))
    }

    suspend fun stageInternal(modVariant: ModVariant): Result<Unit> {
        if (modVariant.stagingInfo != null) {
            Timber.d { "Mod already staged! $modVariant" }
            return Result.success(Unit)
        }

        val stagingFolder = config.stagingPath.toPathOrNull()
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
                        gamePath.getModsPath().resolve(modVariant.generateVariantFolderName())
                    )

                    if (linkFoldersResult.isFailure) {
                        return failLogging(linkFoldersResult.exceptionOrNull()?.message ?: "")
                    } else {
                        Timber.d { "Mod staged from the /mods folder: $modVariant" }
                    }
                } else {
                    return failLogging(it.message ?: "")
                }
            }

        return Result.success(Unit)
    }

    suspend fun enableInternal(modVariant: ModVariant): Result<Unit> {
        if (modVariant.mod.isEnabled(modVariant)) {
            Timber.i { "Already enabled!: $modVariant" }
            return Result.success(Unit)
        }

        if (modVariant.modsFolderInfo == null) {
            val result = linkFromStagingToGameMods(modVariant)

            if (result != Result.success(Unit)) {
                return result
            }

            Timber.i { "Enabled mod for SMOL: $modVariant" }
        }

        if (!modVariant.mod.isEnabledInGame) {
            gameEnabledMods.enable(modVariant.modInfo.id)
        }

        return Result.success((Unit))
    }

    suspend fun unstageInternal(mod: Mod): Result<Unit> {
        mod.variants.forEach { modVariant ->
            if (modVariant.stagingInfo == null || !modVariant.stagingInfo.folder.exists()) {
                Timber.d { "Mod not staged! $modVariant" }
                return@forEach
            }

            if (modVariant.archiveInfo == null) {
                Timber.w { "Cannot unstage mod not archived or else it'll be gone forever!: $modVariant" }
                return@forEach
            }

            // Make sure it's disabled before unstaging
            disableInternal(modVariant)

            IOLock.write {
                kotlin.runCatching {
                    modVariant.stagingInfo.folder.deleteRecursively()
                }
                    .onFailure { Timber.e(it) }
                    .getOrThrow()
            }
        }

        Timber.d { "Mod unstaged: $mod" }
        return Result.success(Unit)
    }

    /**
     * Creates (hard)links from the staging folder to the /mods folder for the specified [ModVariant].
     */
    private suspend fun linkFromStagingToGameMods(modToEnable: ModVariant): Result<Unit> {
        var mod = modToEnable

        // If it's not staged, stage it first (from the archive).
        if (mod.stagingInfo == null || !mod.stagingInfo!!.folder.exists()) {
            stageInternal(mod)
            // Then reload, to get the new staging path.
            mod = (modLoader.reload() ?: emptyList())
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
            return failLogging("Staging folder doesn't exist. ${sourceFolder.pathString}, $mod")
        }

        val destFolder = gamePath.getModsPath().resolve(sourceFolder.name)
        return linkFolders(sourceFolder, destFolder)
    }

    /**
     * Creates (hard)links for all files in the source folder to the destination folder.
     */
    private fun linkFolders(sourceFolder: Path, destFolder: Path): Result<Unit> {
        trace(onFinished = { _, time ->
            timber.ktx.Timber.tag(Constants.TAG_TRACE).i {
                "Took ${time}ms to soft/hardlink folder ${sourceFolder.absolutePathString()} to ${destFolder.absolutePathString()}."
            }
        }) {
            IOLock.write {
                if (!destFolder.exists()) destFolder.createDirectories()

                destFolder.deleteRecursively()
                destFolder.createDirectories()
                val failedFiles = mutableListOf<Path>()
                val succeededFiles = mutableListOf<Path>()

                sourceFolder.walk().forEach { sourceFile ->
                    //        listOf(sourceFolder).forEach { sourceFile ->
                    //            if (sourceFile.path == sourceFolder.path) return@forEach
                    val sourceRelativePath = sourceFile.relativeTo(sourceFolder)
                    val destFile = Path(destFolder.absolutePathString(), sourceRelativePath.toString())

                    if (!sourceFile.exists()) {
                        failedFiles.add(sourceFile)
                        Timber.w { "Couldn't create ${linkMethod.name}, as source didn't exist. ${sourceFile.absolutePathString()}" }
                    }


                    when {
                        sourceFile.isDirectory() -> destFile.deleteRecursively()
                        sourceFile.isRegularFile() -> destFile.deleteIfExists()
                    }

                    kotlin.runCatching {
                        when (linkMethod) {
                            LinkMethod.HardLink ->
                                when {
                                    sourceFile.isDirectory() -> destFile.createDirectories()
                                    sourceFile.isRegularFile() -> destFile.createLinkPointingTo(sourceFile)
                                    else -> Timber.w { "Not sure what kind of file this is: $sourceFile" }
                                }
                            LinkMethod.Symlink -> destFile.createSymbolicLinkPointingTo(sourceFile)
                        }
                    }
                        .onFailure { ex ->
                            failedFiles.add(sourceFile)
                            Timber.w(ex) {
                                "Error creating ${linkMethod.name}.\n" +
                                        "      Source: ${sourceFile.absolutePathString()}\n" +
                                        "      Dest: ${destFile.absolutePathString()}"
                            }
                            if (linkMethod == LinkMethod.HardLink && ex is AccessDeniedException) {
                                Timber.w { "Remember that hard links cannot cross disk partitions." }
                            }
                        }
                        .onSuccess {
                            succeededFiles.add(sourceFile)
                            Timber.v { "Created ${linkMethod.name} at ${destFile.absolutePathString()}" }
                        }
                        .getOrThrow()
                }

                if (failedFiles.any()) {
                    Timber.w { "Failed to create links/folders for ${failedFiles.count()} files in ${destFolder.absolutePathString()}." }
                }

                Timber.i { "Created links/folders for ${succeededFiles.count()} files in ${destFolder.absolutePathString()}." }
            }

            return Result.success(Unit)
        }
    }

    /**
     * Removes the mod folder/files from the game /mods folder.
     */
    private fun removeFromModsFolder(modVariant: ModVariant): Result<Unit> {
        trace(onFinished = { _, time ->
            timber.ktx.Timber.tag(Constants.TAG_TRACE).i {
                "Took ${time}ms to remove variant ${modVariant.smolId} from mods folder."
            }
        }) {
            if (modVariant.modsFolderInfo == null) {
                Timber.w { "Not found in /mods folder: ${modVariant.smolId}." }
                return Result.success(Unit)
            }

            val modsFolderInfo = modVariant.modsFolderInfo

            if (!modsFolderInfo.folder.exists()) {
                Timber.w { "Nothing to remove. Folder doesn't exist in /mods. $modVariant" }
                return Result.success(Unit)
            }

            IOLock.write {
                kotlin.runCatching {
                    modsFolderInfo.folder.deleteRecursively()
                }
                    .recover {
                        Timber.w(it) { "Error deleting ${modsFolderInfo.folder.absolutePathString()}. Marking for deletion on exit." }
                        modsFolderInfo.folder.toFile().deleteOnExit()
                    }
                    .onFailure {
                        Timber.e(it)
                        return Result.failure(it)
                    }
                    .getOrThrow()
            }

            return Result.success(Unit)
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun failLogging(error: String): Result<Unit> {
        Timber.w { error }
        return Result.failure(RuntimeException(error))
    }
}