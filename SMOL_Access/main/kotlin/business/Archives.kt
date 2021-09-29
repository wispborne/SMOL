package business

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import config.AppConfig
import config.GamePath
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import model.ModInfo
import model.ModVariant
import model.Version
import net.sf.sevenzipjbinding.*
import net.sf.sevenzipjbinding.impl.OutItemFactory
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import net.sf.sevenzipjbinding.impl.RandomAccessFileOutStream
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem
import net.sf.sevenzipjbinding.util.ByteArrayStream
import org.apache.commons.io.FileUtils
import org.tinylog.Logger
import util.IOLock
import util.MOD_INFO_FILE
import util.mkdirsIfNotExist
import util.toFileOrNull
import java.io.File
import java.io.FileWriter
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.concurrent.withLock

@OptIn(ExperimentalStdlibApi::class)
class Archives(
    private val config: AppConfig,
    private val gamePath: GamePath,
    private val gson: Gson,
    private val modInfoLoader: ModInfoLoader
) {
    companion object {
        const val ARCHIVES_FILENAME = "manifest.json"
    }

    val archiveMovementStatusFlow = MutableStateFlow<String>("")

    fun getArchivesPath() = config.archivesPath

    fun getArchivesManifest(): ArchivesManifest? =
        kotlin.runCatching {
            gson.fromJson<ArchivesManifest>(File(config.archivesPath!!, ARCHIVES_FILENAME).readText())
        }
            .onFailure { Logger.warn(it) }
            .recover {
                IOLock.withLock {
                    // Try to make a backup of the file
                    val file = config.archivesPath?.let { File(it, ARCHIVES_FILENAME) }
                    if (file?.exists() == true) {
                        kotlin.runCatching {
                            file.copyTo(File(file.parentFile, file.name + ".bak"), overwrite = true)
                        }
                            .onFailure { Logger.error(it) }
                    }
                }

                // Then return an empty manifest so it'll be created anew
                ArchivesManifest()
            }
            .getOrDefault(ArchivesManifest())

    /**
     * Given an arbitrary file, find and install the associated mod into the given folder.
     * @param inputFile A file or folder to try to install.
     * @param destinationFolder The folder to place the result into. Not the mod folder, but the parent of that (eg /mods).
     * @param shouldCompressModFolder If true, will compress the mod as needed and place the archive in the folder.
     */
    suspend fun installFromUnknownSource(inputFile: File, destinationFolder: File, shouldCompressModFolder: Boolean) {
        if (!inputFile.exists()) throw RuntimeException("File does not exist: ${inputFile.absolutePath}")
        if (!destinationFolder.exists()) throw RuntimeException("File does not exist: ${destinationFolder.absolutePath}")

        suspend fun copyOrCompressDir(modFolder: File) {
            if (shouldCompressModFolder) {
                // Create an archive from the parent folder
                compressModsInFolder(inputModFolder = modFolder, destinationFolder = destinationFolder)
            } else {
                kotlin.runCatching {
                    // Or just copy the files
                    modFolder.copyRecursively(
                        target = destinationFolder.resolve(modFolder.name),
                        overwrite = true,
                        onError = { _, ioException ->
                            Logger.error(ioException)
                            throw ioException
                        }
                    )
                }.onFailure { Logger.warn(it); throw it }
            }
        }

        if (inputFile.isFile) {
            if (inputFile.name == MOD_INFO_FILE) {
                // Input file is mod_info.json, parent folder is mod folder
                val modFolder = inputFile.parentFile

                if (modFolder.exists() && modFolder.isDirectory) {
                    copyOrCompressDir(modFolder)
                    return
                } else {
                    RuntimeException("Input was $MOD_INFO_FILE but there was no parent folder?!")
                        .also { Logger.warn(it) }
                        .also { throw it }
                }
            } else {
                // Input was a file but not mod_info.json, try as an archive.
                val modInfo: ModInfo? = kotlin.runCatching {
                    IOLock.withLock {
                        RandomAccessFileInStream(
                            RandomAccessFile(inputFile, "r")
                        ).use { fileInStream ->
                            SevenZip.openInArchive(null, fileInStream).use { inArchive ->
                                val items = inArchive.simpleInterface.archiveItems
                                val modInfoFile = items
                                    .asSequence()
                                    .filter { !it.isFolder }
                                    .firstOrNull { it.path.contains(MOD_INFO_FILE) }

                                var modInfo: ModInfo? = null
                                modInfoFile?.extractSlow { bytes ->
                                    bytes ?: return@extractSlow 0
                                    val modInfoJson = String(bytes = bytes)
                                    modInfo = modInfoLoader.readModInfoFile(modInfoJson)
                                    bytes.size
                                }

                                return@runCatching modInfo
                            }
                        }
                    }
                }
                    .onFailure {
                        Logger.warn(it) { "Unable to read ${inputFile.absolutePath}" }
                        throw it
                    }
                    .getOrElse { null }

                // If mod_info.json was found in archive
                if (modInfo != null) {
                    if (shouldCompressModFolder) {
                        val archivePath = destinationFolder.resolve(inputFile.name)
                        kotlin.runCatching {
                            // Copy archive file
                            inputFile.copyTo(
                                target = archivePath,
                                overwrite = true
                            )
                        }
                            .onFailure {
                                Logger.warn(it)
                                throw it
                            }
                        addToManifest(modInfo = modInfo, archivePath = archivePath)
                    } else {
                        // Extract archive to subfolder in destination folder (in case there was no root folder, then we'll fix after).
                        IOLock.withLock {
                            val extraParentFolder = destinationFolder.resolve("tempRootFolder")

                            RandomAccessFileInStream(
                                RandomAccessFile(inputFile, "r")
                            ).use { fileInStream ->
                                SevenZip.openInArchive(null, fileInStream).use { inArchive ->
                                    inArchive.simpleInterface.archiveItems.forEach { item ->
                                        extraParentFolder.mkdirsIfNotExist()
                                        item.extractFile(archiveBaseFolder = null, destFolder = extraParentFolder)
                                    }
                                }
                            }

                            removedNestedFolders(extraParentFolder)
                            return
                        }
                    }
                    return
                }
            }
        } else if (inputFile.isDirectory) {
            copyOrCompressDir(inputFile)
        } else {
            // Not file or directory?
            throw RuntimeException("${inputFile.absolutePath} not recognized as file or folder.")
        }
    }

    /**
     * Given a folder with a single mod somewhere inside, rearranges folders to match `./ModName/mod_info.json`.
     *
     * @param folderContainingSingleMod A folder with a single mod somewhere inside.
     */
    fun removedNestedFolders(folderContainingSingleMod: File) {
        kotlin.runCatching {
            if (!folderContainingSingleMod.isDirectory)
                throw RuntimeException("folderContainingSingleMod must be a folder! It's in the name!")

            val modInfoFile = folderContainingSingleMod.listFiles { _, name -> name == MOD_INFO_FILE }.singleOrNull()
                ?: throw RuntimeException("Expected a single $MOD_INFO_FILE in ${folderContainingSingleMod.absolutePath}")

            val modInfo = modInfoLoader.readModInfoFile(modInfoFile.readText())

            if (folderContainingSingleMod == modInfoFile.parentFile) {
                // Mod info file is one folder deep, all is well.
                return
            } else {
                // Is nested, move the folder above mod_info.json to the top level folder
                FileUtils.moveDirectory(modInfoFile.parentFile, folderContainingSingleMod)
            }
        }
            .onFailure {
                Logger.error(it)
                throw it
            }
    }

    fun extractMod(
        modVariant: ModVariant,
        destinationFolder: File
    ) {
        if (modVariant.archiveInfo == null) {
            throw RuntimeException("Cannot stage mod not archived: $modVariant")
        }

        extractArchive(modVariant.archiveInfo.folder, destinationFolder, modVariant.modInfo.name)
    }

    private fun extractArchive(
        archiveFile: File,
        destinationFolder: File,
        defaultFolderName: String
    ) {
        IOLock.withLock {
            RandomAccessFileInStream(
                RandomAccessFile(archiveFile, "r")
            ).use { fileInStream ->
                SevenZip.openInArchive(null, fileInStream).use { inArchive ->
                    val archiveItems = inArchive.simpleInterface.archiveItems
                    val files = archiveItems.map { File(it.path) }
                    val modInfoFile = files.find { it.name.equals(MOD_INFO_FILE, ignoreCase = true) }
                        ?: throw RuntimeException("mod_info.json not found. ${archiveFile.absolutePath}")

                    val archiveBaseFolder: File? = modInfoFile.parentFile

                    val destFolder = if (modInfoFile.parent == null)
                        File(destinationFolder, defaultFolderName)
                    else {
                        File(destinationFolder, modInfoFile.parentFile.path)
                    }

                    destFolder.mkdirsIfNotExist()

                    archiveItems.forEach { item ->
                        val result = item.extractFile(archiveBaseFolder, destFolder)

                        if (result == ExtractOperationResult.OK) {
                            Logger.debug { "Extracted ${item.path}" }
                        } else {
                            Logger.warn { result }
                        }
                    }

                    markManagedBySmol(destFolder)
                }
            }
        }
    }

    private fun ISimpleInArchiveItem.extractFile(
        archiveBaseFolder: File?,
        destFolder: File
    ): ExtractOperationResult? {
        val result = this.extractSlow { bytes ->
            val fileRelativeToBase =
                archiveBaseFolder?.let { File(this.path).toRelativeString(it) } ?: this.path
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
        return result
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun compressModsInFolder(
        inputModFolder: File,
        destinationFolder: File? = config.archivesPath?.toFileOrNull()
    ) {
        return callbackFlow<String> {
            IOLock.withLock {
                kotlin.runCatching {
                    if (destinationFolder == null)
                        throw RuntimeException("Not adding mods to archives folder; destination folder is null.")

                    if (!inputModFolder.exists()) throw RuntimeException("Mod folder doesn't exist:  ${inputModFolder.absolutePath}.")

                    if (inputModFolder.isFile && inputModFolder.name != MOD_INFO_FILE) throw RuntimeException("Not a mod folder: ${inputModFolder.absolutePath}.")

                    // If they dropped mod_info.json, use the parent folder
                    val modFolder =
                        if (inputModFolder.isFile && inputModFolder.name == MOD_INFO_FILE) inputModFolder.parentFile
                            ?: inputModFolder
                        else inputModFolder

                    val scope = this
//                val modsPath = gamePath.getModsPath()
                    val manifest = getArchivesManifest()
                    val mods = modInfoLoader.readModInfosFromFolderOfMods(modFolder, onlySmolManagedMods = false)

                    if (mods.none()) {
                        val runtimeException = RuntimeException("No mods found in ${modFolder.absolutePath}.")
                        Logger.warn(runtimeException)
                        throw runtimeException
                    }

                    mods
                        .filter { (modFolder, modInfo) ->
                            // Only add mods to archives folder if they aren't already there
                            val key = createManifestItemKey(modInfo)
                            val doesArchiveAlreadyExist = doesArchiveExistForKey(manifest, key)
                            Logger.debug { "[${modInfo.id}, ${modInfo.version}] archive already exists? $doesArchiveAlreadyExist" }
                            !doesArchiveAlreadyExist
                        }
                        .forEach { (modFolder, modInfo) ->
                            val filesToArchive =
                                modFolder.walkTopDown().toList()

                            val archiveFile = File(
                                destinationFolder,
                                createArchiveName(modInfo) + ".7z"
                            )
                                .apply { parentFile.mkdirsIfNotExist() }

                            var wasCanceled = false

                            SevenZip.openOutArchive7z().use { archive7z ->
                                fun cancelProcess() {
                                    if (!wasCanceled) {
                                        Logger.warn { "Canceled archive process." }
                                        wasCanceled = true
                                        archive7z.close()

                                        if (!archiveFile.delete()) {
                                            archiveFile.deleteOnExit()
                                        }
                                    }
                                }

                                archive7z.createArchive(
                                    RandomAccessFileOutStream(
                                        RandomAccessFile(
                                            archiveFile, "rw"
                                        )
                                    ),
                                    filesToArchive.size,
                                    object : IOutCreateCallback<IOutItem7z> {
                                        var currentTotal: Float = 0f
                                        var currentFilepath: String = ""

                                        override fun setTotal(total: Long) {
                                            if (!scope.isActive) {
                                                cancelProcess()
                                                return
                                            }

                                            currentTotal = total.toFloat()
                                        }

                                        override fun setCompleted(complete: Long) {
                                            if (!scope.isActive) {
                                                cancelProcess()
                                                return
                                            }

                                            val percentComplete =
                                                "%.2f".format((complete.toFloat() / currentTotal) * 100f)
                                            val progressMessage =
                                                "[$percentComplete%] Moving '${modInfo.id} v${modInfo.version}' to archives. File: $currentFilepath"
                                            trySend(progressMessage)
                                            Logger.debug { progressMessage }
                                        }

                                        override fun setOperationResult(wasSuccessful: Boolean) {}

                                        override fun getItemInformation(
                                            index: Int,
                                            outItemFactory: OutItemFactory<IOutItem7z>
                                        ): IOutItem7z? {
                                            if (!scope.isActive) {
                                                cancelProcess()
                                                return null
                                            }

                                            val item = outItemFactory.createOutItem()
                                            val file = filesToArchive[index]

                                            if (file.isDirectory) {
                                                item.propertyIsDir = true
                                            } else {
                                                item.dataSize = file.readBytes().size.toLong()
                                            }

                                            item.propertyPath = file.toRelativeString(modFolder)
                                            return item
                                        }

                                        override fun getStream(index: Int): ISequentialInStream? {
                                            if (!scope.isActive) {
                                                cancelProcess()
                                                return null
                                            }

                                            currentFilepath = filesToArchive[index].relativeTo(modFolder).path
                                            return ByteArrayStream(filesToArchive[index].readBytes(), true)
                                        }
                                    })
                            }

                            if (wasCanceled) {
                                removeFromManifest(modInfo)
                            } else {
                                addToManifest(modInfo, archiveFile)
                            }
                        }

                    close()
                }
            }.onFailure { Logger.warn(it); throw it }
        }
            .onEach { archiveMovementStatusFlow.value = it }
            .onCompletion { archiveMovementStatusFlow.value = it?.message ?: "Finished adding mods to archive." }
            .collect()
    }

    fun addToManifest(modInfo: ModInfo, archivePath: File) {
        updateManifest { manifest ->
            manifest.copy(
                manifestItems = manifest.manifestItems.toMutableMap().apply {
                    this[createManifestItemKey(modInfo)] = ManifestItemValue(
                        archivePath = archivePath.absolutePath,
                        modInfo = modInfo
                    )
                })
        }
    }

    fun removeFromManifest(modInfo: ModInfo) {
        updateManifest { manifest ->
            manifest.copy(
                manifestItems = manifest.manifestItems.toMutableMap().apply {
                    this.remove(createManifestItemKey(modInfo))
                })
        }
    }

    fun updateManifest(mutator: (archivesManifest: ArchivesManifest) -> ArchivesManifest) {
        val originalManifest = getArchivesManifest()!!
        mutator(originalManifest)
            .run {
                IOLock.withLock {
                    val file = File(config.archivesPath!!, ARCHIVES_FILENAME)
                    FileWriter(file).use { writer ->
                        gson.toJson(this, writer)
                    }
                    Logger.info {
                        "Updated manifest at ${file.absolutePath} from ${originalManifest.manifestItems.count()} " +
                                "to ${this.manifestItems.count()} items."
                    }
                }
            }
    }

    fun doesArchiveExistForKey(manifest: ArchivesManifest?, key: Int) =
        (manifest?.manifestItems?.containsKey(key) == true
                && File(manifest.manifestItems[key]?.archivePath ?: "").exists())

    fun createManifestItemKey(modId: String, version: Version) = Objects.hash(modId, version.toString())
    fun createManifestItemKey(modInfo: ModInfo) = createManifestItemKey(modInfo.id, modInfo.version)

    private fun createArchiveName(modInfo: ModInfo) =
        modInfo.id.replace('-', '_') + "-" + modInfo.version.toString()


    fun markManagedBySmol(modInStagingFolder: File) {
        IOLock.withLock {
            val marker = File(modInStagingFolder, Staging.MARKER_FILE_NAME)
            marker.createNewFile()
        }
    }

    fun changePath(newPath: String) {
        kotlin.runCatching {
            IOLock.withLock {
                val newFolder = File(newPath)
                val oldFolder = File(config.archivesPath ?: return).also { if (!it.exists()) return }

                newFolder.mkdirsIfNotExist()

                Files.move(oldFolder.toPath(), newFolder.toPath(), StandardCopyOption.REPLACE_EXISTING)

                config.archivesPath = newPath
            }
        }
            .onFailure { Logger.error(it) }
            .getOrThrow()
    }

    data class ArchivesManifest(
        val manifestItems: Map<Int, ManifestItemValue> = emptyMap()
    )

    data class ManifestItemValue(
        val archivePath: String,
        val modInfo: ModInfo
    )
}