package business

import config.AppConfig
import config.GamePath
import kotlinx.coroutines.runBlocking
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
import kotlin.concurrent.withLock
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
        IOLock.withLock {
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

    suspend fun install(modVariant: ModVariant): Result<Unit> {
        try {
            return installInternal(modVariant)
        } finally {
            manualReloadTrigger.trigger.emit("Installed mod: $modVariant")
        }
    }

    suspend fun uninstall(mod: Mod): Result<Unit> {
        try {
            return uninstallInternal(mod)
        } finally {
            manualReloadTrigger.trigger.emit("Mod uninstalled: $mod")
        }
    }

    suspend fun enable(modToEnable: ModVariant): Result<Unit> {
        try {
            return enableInternal(modToEnable)
        } finally {
            manualReloadTrigger.trigger.emit("Enabled mod: $modToEnable")
        }
    }

    suspend fun disable(modVariant: ModVariant): Result<Unit> {
        try {
            return disableInternal(modVariant)
        } finally {
            manualReloadTrigger.trigger.emit("Disabled mod: $modVariant")
        }
    }

    private suspend fun disableInternal(modVariant: ModVariant): Result<Unit> {
        // If it's not installed, install it (but it'll stay disabled)
        if (modVariant.stagingInfo == null) {
            installInternal(modVariant)
        }

        if (!modVariant.mod.isEnabled(modVariant)) {
            return Result.success(Unit)
        }

        if (modVariant.isEnabledInSmol) {
            val result = disableInSmol(modVariant)

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

    private suspend fun installInternal(modVariant: ModVariant): Result<Unit> {
        if (modVariant.stagingInfo != null) {
            Logger.debug { "Mod already staged! $modVariant" }
            return Result.success(Unit)
        }


        val stagingFolder = config.stagingPath.toFileOrNull()
            ?: return failLogging("No staging folder: $modVariant")

        kotlin.runCatching {
            archives.extractMod(modVariant, stagingFolder)
        }
            .onFailure { return failLogging(it.message ?: "") }

        return Result.success(Unit)
    }

    private suspend fun enableInternal(modToEnable: ModVariant): Result<Unit> {
        if (modToEnable.mod.isEnabled(modToEnable)) {
            Logger.info { "Already enabled!: $modToEnable" }
            return Result.success(Unit)
        }

        if (!modToEnable.isEnabledInSmol) {
            val result = enableInSmol(modToEnable)

            if (result != Result.success(Unit)) {
                return result
            }

            Logger.info { "Enabled mod for SMOL: $modToEnable" }
        }

        if (!modToEnable.mod.isEnabledInGame) {
            gameEnabledMods.enable(modToEnable.modInfo.id)
        }

        return Result.success((Unit))
    }

    private suspend fun uninstallInternal(mod: Mod): Result<Unit> {
        mod.variants.values.forEach { modVariant ->
            if (modVariant.stagingInfo == null || !modVariant.stagingInfo.folder.exists()) {
                Logger.debug { "Mod not installed! $modVariant" }
                return@forEach
            }

            if (modVariant.archiveInfo == null) {
                Logger.warn { "Cannot uninstall mod not archived: $modVariant" }
                return@forEach
            }

            // Make sure it's disabled before uninstalling
            disableInternal(modVariant)

            IOLock.withLock {
                kotlin.runCatching {
                    modVariant.stagingInfo.folder.deleteRecursively()
                }
                    .onFailure { Logger.error(it) }
                    .getOrThrow()
            }
        }

        Logger.debug { "Mod uninstalled: $mod" }
        return Result.success(Unit)
    }

    private suspend fun enableInSmol(modToEnable: ModVariant): Result<Unit> {
        IOLock.withLock {
            var mod = modToEnable

            if (mod.stagingInfo == null || !mod.stagingInfo!!.folder.exists()) {
                runBlocking { installInternal(mod) }
                mod = modLoader.getMods()
                    .asSequence()
                    .flatMap { it.variants.values }
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

            return Result.success(Unit)
        }
    }

    private fun disableInSmol(modVariant: ModVariant): Result<Unit> {
        if (!modVariant.isEnabledInSmol) {
            Logger.warn { "Already disabled in SMOL." }
            return Result.success(Unit)
        }

        val modsFolderInfo = modVariant.mod.modsFolderInfo

        if (modsFolderInfo?.folder?.exists() != true) {
            Logger.warn { "Nothing to remove. Folder doesn't exist in /mods. $modVariant" }
            return Result.success(Unit)
        }

        IOLock.withLock {
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

    companion object {
        const val MARKER_FILE_NAME = ".managed-by-smol"
    }
}

fun File.isSmolStagingMarker() = if (this.exists()) this.name == Staging.MARKER_FILE_NAME else false