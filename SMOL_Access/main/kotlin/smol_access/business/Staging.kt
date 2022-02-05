package smol_access.business

import smol_access.Constants
import smol_access.config.GamePathManager
import smol_access.model.Mod
import smol_access.model.ModVariant
import smol_access.util.ManualReloadTrigger
import timber.Timber
import timber.ktx.i
import timber.ktx.v
import timber.ktx.w
import utilities.IOLock
import utilities.deleteRecursively
import utilities.trace
import utilities.walk
import java.nio.file.AccessDeniedException
import java.nio.file.Path
import kotlin.io.path.*

internal class Staging(
    private val gamePathManager: GamePathManager,
    private val modLoader: ModLoader,
    private val gameEnabledMods: GameEnabledMods,
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
    fun disableModVariant(modVariant: ModVariant): Result<Unit> {
        Timber.i { "Disabling variant ${modVariant.smolId}" }
        if (modVariant.modsFolderInfo != null) {
            val result = removeFromModsFolder(modVariant)

            if (result != Result.success(Unit)) {
                return result
            }

            Timber.i { "Disabling ${modVariant.smolId}: removed from /mods." }
        }

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

        kotlin.runCatching {
            if (modVariant.mod(modLoader).isEnabled(modVariant)) {
                Timber.i { "Already enabled!: $modVariant" }
                return Result.success(Unit)
            }
        }
            .onFailure {
                Timber.w(it)
                return Result.failure(it)
            }

        if (modVariant.modsFolderInfo == null) {
            val result = linkFromStagingToGameMods(modVariant, modsFolderPath)

            if (result != Result.success(Unit)) {
                Timber.w(result.exceptionOrNull()) { "Error enabling ${modVariant.smolId}." }
                return result
            }

            Timber.i { "Enabled mod for SMOL: $modVariant" }
        }

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
            if (modVariant.stagingInfo?.folder?.exists() != true) {
                Timber.i { "Mod not staged! $modVariant" }
                return@forEach
            }

            disableModVariant(modVariant)
        }

        Timber.i { "Mod disabled: $mod" }
        return Result.success(Unit)
    }

    /**
     * Creates (hard)links from the staging folder to the /mods folder for the specified [ModVariant].
     */
    private fun linkFromStagingToGameMods(modToEnable: ModVariant, modsFolderPath: Path): Result<Unit> {
        Timber.i { "Linking ${modToEnable.smolId} to /mods." }

        if (modToEnable.stagingInfo?.folder?.exists() != true) {
            return failLogging("Mod is not staged $modToEnable")
        }

        val sourceFolder = modToEnable.stagingInfo.folder

        if (!sourceFolder.exists()) {
            return failLogging("Staging folder doesn't exist. ${sourceFolder.pathString}, $modToEnable")
        }

        val destFolder = modsFolderPath.resolve(sourceFolder.name)
        return linkFolders(sourceFolder, destFolder)
    }

    /**
     * Creates (hard)links for all files in the source folder to the destination folder.
     */
    private fun linkFolders(sourceFolder: Path, destFolder: Path): Result<Unit> {
        Timber.i { "Linking $sourceFolder to $destFolder." }
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
                Timber.w { "Not found in /mods folder: ${modVariant.smolId} (so removing it is a success!)." }
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