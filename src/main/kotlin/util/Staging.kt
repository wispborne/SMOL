package util

import model.ModVersion
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import org.tinylog.Logger
import java.io.File
import java.io.RandomAccessFile
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
    private val gameEnabledMods: GameEnabledMods
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
    fun changeStagingPath(newPath: String) {
        kotlin.runCatching {
            val newFolder = File(newPath)
            val oldFolder = File(config.stagingPath ?: return).also { if (!it.exists()) return }

            newFolder.mkdirsIfNotExist()

            Files.move(oldFolder.toPath(), newFolder.toPath(), StandardCopyOption.REPLACE_EXISTING)

            config.stagingPath = newPath
        }
            .onFailure { Logger.warn(it) }
            .getOrThrow()
    }

    fun stage(mod: ModVersion): Result<Unit> {
        if (mod.archiveInfo == null) {
            return failLogging("Cannot stage mod not archived: $mod")
        }

        val stagingFolder = config.stagingPath.toFileOrNull() ?: return failLogging("No staging folder: $mod")

        RandomAccessFileInStream(
            RandomAccessFile(mod.archiveInfo.folder, "r")
        ).use { fileInStream ->
            SevenZip.openInArchive(null, fileInStream).use { inArchive ->
                val archiveItems = inArchive.simpleInterface.archiveItems
                val files = archiveItems.map { File(it.path) }
                val modInfoFile = files.find { it.name.equals("mod_info.json", ignoreCase = true) }
                    ?: return failLogging("mod_info.json not found. $mod")

                val archiveBaseFolder: File? = modInfoFile.parentFile

                val destFolder = if (modInfoFile.parent == null)
                    File(stagingFolder, mod.modInfo.name)
                else {
                    File(stagingFolder, modInfoFile.parentFile.path)
                }

                destFolder.mkdirsIfNotExist()

                archiveItems.forEach { item ->
                    val result = item.extractSlow { bytes ->
                        val fileRelativeToBase =
                            archiveBaseFolder?.let { File(item.path).toRelativeString(it) } ?: item.path
                        File(destFolder, fileRelativeToBase).run {
                            // Delete file if it exists so we replace it.
                            if (this.exists()) {
                                this.delete()
                            }

                            parentFile?.mkdirsIfNotExist()
                            writeBytes(bytes)
                        }
                        bytes.size
                    }

                    if (result == ExtractOperationResult.OK) {
                        Logger.debug { "Extracted ${item.path}" }
                    } else {
                        Logger.warn { result }
                    }
                }

                markManagedBySmol(destFolder)
            }
        }


        return Result.success(Unit)
    }

    fun enable(modToEnable: ModVersion): Result<Unit> {
        if (modToEnable.mod.isEnabled(modToEnable)) {
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

    private fun enableInSmol(modToEnable: ModVersion): Result<Unit> {
        var mod = modToEnable

        if (mod.stagingInfo == null || !mod.stagingInfo!!.folder.exists()) {
            stage(mod)
            mod = modLoader.getMods()
                .asSequence()
                .flatMap { it.modVersions.values }
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
                    Logger.debug { "Created ${linkMethod.name} at ${destFile.absolutePath}" }
                }
                .getOrThrow()
        }

        if (failedFiles.any()) {
            Logger.warn { "Failed to create links/folders for ${failedFiles.count()} files in ${destFolder.absolutePath}." }
        }

        Logger.info { "Created links/folders for ${succeededFiles.count()} files in ${destFolder.absolutePath}." }

        return Result.success(Unit)
    }

    fun disable(modVToDisable: ModVersion): Result<Unit> {
        if (!modVToDisable.mod.isEnabled(modVToDisable)) {
            return Result.success(Unit)
        }

        if (modVToDisable.isEnabledInSmol) {
            val result = disableInSmol(modVToDisable)

            if (result != Result.success(Unit)) {
                return result
            }

            Logger.info { "Disabled mod for SMOL: $modVToDisable" }
        }

        if (modVToDisable.mod.isEnabledInGame) {
            gameEnabledMods.disable(modVToDisable.modInfo.id)
        }

        return Result.success((Unit))
    }

    private fun disableInSmol(modV: ModVersion): Result<Unit> {
        if (!modV.isEnabledInSmol) {
            Logger.warn { "Already disabled in SMOL." }
            return Result.success(Unit)
        }

        val modsFolderInfo = modV.mod.modsFolderInfo

        if (modsFolderInfo?.folder?.exists() != true) {
            Logger.warn { "Nothing to remove. Folder doesn't exist in /mods. $modV" }
            return Result.success(Unit)
        }

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

    fun markManagedBySmol(modInStagingFolder: File) {
        val marker = File(modInStagingFolder, MARKER_FILE_NAME)
        marker.createNewFile()
    }
}

fun File.isSmolStagingMarker() = if (this.exists()) this.name == Staging.MARKER_FILE_NAME else false