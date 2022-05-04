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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import net.sf.sevenzipjbinding.simple.ISimpleInArchiveItem
import smol.access.Constants
import smol.access.model.*
import smol.access.util.ArchiveExtractToFolderCallback
import smol.access.util.ArchiveExtractToMemoryCallback
import smol.timber.ktx.Timber
import smol.timber.ktx.d
import smol.utilities.*
import java.io.RandomAccessFile
import java.nio.file.Path
import kotlin.io.path.*

@OptIn(ExperimentalStdlibApi::class)
class Archives internal constructor(
    private val modInfoLoader: ModInfoLoader,
    private val jsanity: Jsanity
) {
    /**
     * Given an arbitrary file, find and install the associated mod into the given folder.
     * @param inputFile A file or folder to try to install.
     * @param destinationFolder The folder to place the result into. Not the mod folder, but the parent of that (eg /mods).
     */
    suspend fun installFromUnknownSource(
        inputFile: Path,
        destinationFolder: Path,
        existingMods: List<Mod>,
        promptUserToReplaceExistingFolder: suspend (modInfo: ModInfo) -> Boolean
    ) {
        Timber.i { "Installing ${inputFile.absolutePathString()} to ${destinationFolder.absolutePathString()}." }
        if (!inputFile.exists()) throw RuntimeException("File does not exist: ${inputFile.absolutePathString()}")
        if (!destinationFolder.exists()) throw RuntimeException("File does not exist: ${destinationFolder.absolutePathString()}")

        trace(onFinished = { _, millis: Long -> Timber.d { "Time to install from unknown source: ${millis}ms." } }) {
            if (inputFile.isRegularFile()) {
                if (inputFile.name.equals(Constants.MOD_INFO_FILE, ignoreCase = true)) {
                    // Input file is mod_info.json, parent folder is mod folder
                    val modFolder = inputFile.parent

                    if (modFolder.exists() && modFolder.isDirectory()) {
                        kotlin.runCatching {
                            copyOrCompressDir(
                                modFolder = modFolder,
                                destinationFolder = destinationFolder,
                                inputFile = inputFile
                            )
                        }
                            .onFailure { Timber.w(it); throw it }
                        return
                    } else {
                        RuntimeException("Input was ${Constants.MOD_INFO_FILE} but there was no parent folder?!")
                            .also { Timber.w(it) }
                            .also { throw it }
                    }
                } else {
                    // Input was a file but not mod_info.json, try as an archive.
                    val dataFiles: DataFiles? = findDataFilesInArchive(inputFile)

                    // If mod_info.json was found in archive
                    if (dataFiles != null) {
                        val modInfo = dataFiles.modInfo
                        val modFolder =
                            destinationFolder.resolve(ModVariant.generateVariantFolderName(modInfo = modInfo))
                        val newSmolId = ModVariant.createSmolId(modInfo)

                        // Mod variant already exists, ask user if we should replace it.
                        if (existingMods.flatMap { it.variants }.any { it.smolId == newSmolId }) {
                            Timber.i { "'${modInfo.name}' version '$${modInfo.version}' is already installed to '${modFolder.absolutePathString()}'. Asking user if they want to replace it." }
                            val shouldReplaceFolder = promptUserToReplaceExistingFolder.invoke(modInfo)

                            if (shouldReplaceFolder) {
                                IOLock.write {
                                    Timber.i { "Deleting ${modFolder.absolutePathString()}." }
                                    modFolder.deleteRecursively()
                                }
                            }
                        }

                        IOLock.write {

                            RandomAccessFileInStream(
                                RandomAccessFile(inputFile.toFile(), "r")
                            ).use { fileInStream ->
                                SevenZip.openInArchive(null, fileInStream).use { inArchive ->
                                    inArchive.extract(
                                        null, false,
                                        ArchiveExtractToFolderCallback(modFolder, inArchive)
                                    )
                                }
                            }

                            runBlocking { removedNestedFolders(modFolder) }
                            return
                        }
                    } else {
                        val ex = RuntimeException("Archive did not have a valid ${Constants.MOD_INFO_FILE} inside!")
                        Timber.w(ex)
                        throw ex
                    }
                }
            } else if (inputFile.isDirectory()) {
                kotlin.runCatching {
                    copyOrCompressDir(
                        modFolder = inputFile,
                        destinationFolder = destinationFolder,
                        inputFile = inputFile
                    )
                }
                    .onFailure { Timber.w(it); throw it }
            } else {
                // Not file or directory?
                throw RuntimeException("${inputFile.absolutePathString()} not recognized as file or folder.")
            }
        }
    }

    private suspend fun copyOrCompressDir(modFolder: Path, destinationFolder: Path, inputFile: Path) {
        val modInfoFile = (findModInfoFileInFolder(modFolder) ?: run {
            val ex = RuntimeException("Archive did not have a valid ${Constants.MOD_INFO_FILE} inside!")
            throw ex
        })

        val modInfo = modInfoFile.let {
            IOLock.read(lock = IOLocks.modFolderLock) {
                kotlin.runCatching { jsanity.fromJson<ModInfo>(it.readText(), shouldStripComments = true) }
                    .getOrNull()
            }
        }

        val destinationModFolder = destinationFolder.resolve(destinationFolder.resolve(modInfo?.let {
            ModVariant.generateVariantFolderName(modInfo = it)
        }
            ?: modFolder.name))

        if (destinationModFolder.exists() && inputFile.isSameFileAs(destinationModFolder)) {
            Timber.i { "Not copying the same file to itself: ${inputFile.absolutePathString()}." }
            return
        }

        IOLock.write(lock = IOLocks.modFolderLock) {
            kotlin.runCatching {
                // Or just copy the files
                withContext(Dispatchers.IO) {
                    modInfoFile.parent.listDirectoryEntries()
                        .parallelMap {
                            it.toFile().copyRecursively(
                                target = destinationModFolder.resolve(it.name).toFile(),
                                overwrite = true,
                                onError = { _, ioException ->
                                    Timber.e(ioException)
                                    throw ioException
                                }
                            )
                        }
                }
            }
        }
    }

    private fun findDataFilesInArchive(inputArchiveFile: Path): DataFiles? {
        val dataFiles: DataFiles? =
            kotlin.runCatching {
                trace({ _, time ->
                    Timber.tag(Constants.TAG_TRACE).d {
                        "Time to extract mod_info.json & maybe vercheck file from ${inputArchiveFile.absolutePathString()}: ${time}ms."
                    }
                }) {
                    IOLock.read(IOLocks.everythingLock) {
                        Timber.v { "Opening archive ${inputArchiveFile.name}" }
                        RandomAccessFileInStream(RandomAccessFile(inputArchiveFile.toFile(), "r")).use { fileInStream ->
                            SevenZip.openInArchive(null, fileInStream).use { inArchive ->
                                Timber.v { "Opened archive ${inputArchiveFile.name}" }
                                val items = inArchive.simpleInterface.archiveItems
                                    .filter { !it.isFolder }
                                val modInfoFile = items
                                    .firstOrNull { it.path.contains(Constants.MOD_INFO_FILE, ignoreCase = true) }
                                val versionCheckerFile = items
                                    .firstOrNull {
                                        it.path.endsWith(
                                            Constants.VERSION_CHECKER_FILE_ENDING,
                                            ignoreCase = true
                                        )
                                    }

                                val dataFiles = run {
                                    var modInfo: ModInfo? = null
                                    var versionCheckerInfo: VersionCheckerInfo? = null

                                    val indicesToExtract = listOfNotNull(
                                        modInfoFile?.itemIndex,
                                        versionCheckerFile?.itemIndex
                                    )
                                        .toIntArray()

                                    inArchive.extract(
                                        indicesToExtract, false,
                                        ArchiveExtractToMemoryCallback(indicesToExtract, inArchive) { results ->
                                            modInfo = modInfoFile?.let {
                                                results[modInfoFile.itemIndex]?.let {
                                                    kotlin.runCatching {
                                                        modInfoLoader.deserializeModInfoFile(
                                                            modInfoJson = it
                                                        )
                                                    }
                                                        .getOrNull()
                                                }
                                            }

                                            // If getting version checker file fails, swallow the exception because it was already logged.
                                            kotlin.runCatching {
                                                versionCheckerFile?.let {
                                                    results[versionCheckerFile.itemIndex]?.let {
                                                        versionCheckerInfo =
                                                            modInfoLoader.deserializeVersionCheckerFile(it)
                                                    }
                                                }
                                            }
                                        }
                                    )
                                    modInfo?.let { DataFiles(it, versionCheckerInfo) }
                                }

                                return@runCatching dataFiles
                            }
                        }
                    }
                }
            }
                .onFailure {
                    Timber.w(it) { "Unable to read ${inputArchiveFile.absolutePathString()}." }
                    throw it
                }
                .getOrElse { null }
        return dataFiles
    }

    data class DataFiles(
        val modInfo: ModInfo,
        val versionCheckerInfo: VersionCheckerInfo?
    )

    /**
     * Given a folder with a single mod somewhere inside, rearranges folders to match `./ModName/mod_info.json`.
     *
     * @param folderContainingSingleMod A folder with a single mod somewhere inside, eg Seeker in `Seeker/mod_info.json`.
     */
    fun removedNestedFolders(folderContainingSingleMod: Path) {
        kotlin.runCatching {
            if (!folderContainingSingleMod.isDirectory())
                throw RuntimeException("folderContainingSingleMod must be a folder! It's in the name!")

            val modInfoFile =
                findModInfoFileInFolder(folderContainingSingleMod)
                    ?: throw RuntimeException("Expected a ${Constants.MOD_INFO_FILE} or ${Constants.MOD_INFO_FILE_DISABLED_NAMES} in ${folderContainingSingleMod.absolutePathString()}")

//            val modInfo = modInfoLoader.readModInfoFile(modInfoFile.readText())

            if (folderContainingSingleMod.isSameFileAs(modInfoFile.parent)) {
                // Mod info file is one folder deep, all is well.
                return
            } else {
                // Is nested, move the folder above mod_info.json to the top level folder

                // chosen by fair dice roll.
                // guaranteed to be random.
                val randomTempFile = folderContainingSingleMod.resolve("3f8cd1b8-daea-435a-a932-da0a522438b1")
                // First make a temp dir and copy mod into that, then delete original mod location, then copy from temp into desired location.
                // This prevents being unable to move from /modname/modname to /modname.
                // Instead it will copy /modname/modname to /modname/temp, then delete /modname/modname, then copy /modname/temp to /modname.
                modInfoFile.parent.toFile().moveDirectory(randomTempFile.toFile())
                modInfoFile.deleteRecursively()
                randomTempFile.toFile().moveDirectory(folderContainingSingleMod.toFile())
            }
        }
            .onFailure {
                Timber.e(it)
            }
    }

    fun findModInfoFileInFolder(folder: Path) = folder.walk(maxDepth = 6)
        .firstOrNull { it.isModInfoFile() }

    fun extractArchive(
        archiveFile: Path,
        destinationPath: Path
    ): Path {
        IOLock.write {
            var modFolder: Path
            RandomAccessFileInStream(RandomAccessFile(archiveFile.toFile(), "r")).use { fileInStream ->
                SevenZip.openInArchive(null, fileInStream).use { inArchive ->
//                    val archiveItems = inArchive.simpleInterface.archiveItems
//                    val files = archiveItems.map { File(it.path) }
//                    val modInfoFile = files.find { it.name.equals(SMOL_Access.MOD_INFO_FILE, ignoreCase = true) }
//                        ?: throw RuntimeException("mod_info.json not found. ${archiveFile.absolutePathString()}")

//                    val archiveBaseFolder: Path? = modInfoFile.parentFile.toPath()

                    modFolder =
                            // Create new parent folder with id in it, don't reuse mod folder parent because different variants will have same folder name.
//                        if (modInfoFile.parent == null)
                        destinationPath
//                    else {
//                        File(destinationFolder, modInfoFile.parentFile.path)
//                    }

                    modFolder.createDirectories()

                    inArchive.extract(null, false, ArchiveExtractToFolderCallback(modFolder, inArchive))
//                    markManagedBySmol(modFolder)
                }
            }

            return modFolder
        }
    }

    private fun ISimpleInArchiveItem.extractFile(
        archiveBaseFolder: Path?,
        destFolder: Path
    ): ExtractOperationResult? {
        val result = this.extractSlow { bytes ->
            val fileRelativeToBase =
                archiveBaseFolder?.let { Path.of(this.path).relativeTo(it).pathString } ?: this.path
            destFolder.resolve(fileRelativeToBase).run {
                // Delete file if it exists so we replace it.
                if (this.exists()) {
                    this.deleteIfExists()
                }

                parent?.createDirectories()
                writeBytes(bytes)
            }
            bytes.size
        }
        return result
    }
}