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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import smol.access.Constants
import smol.access.ModModificationState
import smol.access.ModModificationStateHolder
import smol.access.config.AppConfig
import smol.access.model.ModVariant
import smol.timber.ktx.Timber
import smol.utilities.IOLock
import smol.utilities.IOLocks
import smol.utilities.toPathOrNull
import smol.utilities.trace
import java.nio.file.Path
import kotlin.io.path.*

class BackupManager internal constructor(
    private val appConfig: AppConfig,
    private val modsCache: ModsCache,
    private val modModificationStateHolder: ModModificationStateHolder,
    private val archives: Archives,
    private val staging: Staging,
) {
    private val scope = CoroutineScope(Job())

    val folderPath: Path?
        get() = appConfig.modBackupPath.toPathOrNull()
    val isEnabled: Boolean
        get() = appConfig.areModBackupsEnabled
    val hasValidPath: Boolean
        get() = folderPath?.exists() == true && folderPath?.isDirectory() == true && folderPath?.isWritable() == true

    init {
        // Back up newly added mods if the backup feature is enabled and the backup file doesn't exist.
        scope.launch {
            modsCache.mods.collectLatest { modListUpdate ->
                runCatching {
                    modListUpdate?.added.orEmpty().forEach { modVariant ->
                        if (appConfig.areModBackupsEnabled && modVariant.backupFile?.exists() != true) {
                            backupMod(modVariant)
                        }
                    }
                }
                    .onFailure { Timber.w(it) }
            }
        }
    }


    /**
     * Creates a .7z archive of the given mod variant in the [AppConfig.modBackupPath] folder.
     */
    suspend fun backupMod(modVariant: ModVariant, overwriteExisting: Boolean = false): Archives.ArchiveResult? {
        val mod = modVariant.mod(modsCache) ?: return null
        val modBackupPath = appConfig.modBackupPath.toPathOrNull() ?: return null
        val modBackupFile = modBackupPath.resolve(modVariant.generateBackupFileName())
        var result: Archives.ArchiveResult? = null

        if (!overwriteExisting && modBackupFile.exists()) {
            Timber.d { "Mod backup file already exists at '$modBackupFile', skipping." }
            return null
        }

        Timber.i { "Backing up mod variant ${modVariant.smolId} to '$modBackupFile'." }
        trace(onFinished = { _, millis ->
            Timber.i { "Backed up mod variant ${modVariant.smolId} to '$modBackupFile' in ${millis}ms." }
        }) {
            try {
                modModificationStateHolder.state.update {
                    it.toMutableMap().apply {
                        this[mod.id] =
                            ModModificationState.BackingUpVariant
                    }
                }
                IOLock.read(IOLocks.modFolderLock) {
                    if (modBackupFile.exists()) {
                        Timber.i { "Deleting existing mod backup file at '$modBackupFile'." }
                        runCatching { modBackupFile.deleteIfExists() }
                            .onFailure {
                                Timber.e(it) { "Unable to delete existing mod backup file at '$modBackupFile'." }
                                return null
                            }
                    }

                    Timber.i { "Creating mod backup file at '$modBackupFile'." }
                    runCatching {
                        modBackupFile.createFile()
                        result = archives.createArchive(
                            modVariant = modVariant,
                            destinationFile = modBackupFile
                        )
                    }
                        .onFailure {
                            Timber.e(it) { "Unable to create mod backup file at '$modBackupFile'." }
                        }
                }
            } finally {
                staging.manualReloadTrigger.trigger.emit("Backed up mod variant: $modVariant")
                modModificationStateHolder.remove(mod.id)
            }

            result?.errors?.forEach { Timber.w(it) }

            return result
        }
    }

    fun moveFolder(source: Path, destination: Path, pathsMoved: MutableStateFlow<List<Path>>) {
        IOLock.read(IOLocks.backupFolderLock) {
            if (!source.exists()) {
                Timber.w { "Source folder '$source' does not exist, aborting move." }
                return@read
            }

            if (!source.isDirectory()) {
                Timber.w { "Source folder '$source' is not a directory, aborting move." }
                return@read
            }

            if (!destination.exists()) {
                Timber.w { "Destination folder '$destination' does not exist, aborting move." }
                return@read
            }

            if (!destination.isDirectory()) {
                Timber.w { "Destination folder '$destination' is not a directory, aborting move." }
                return@read
            }

            val filesToMove = source.listDirectoryEntries()
                .filter { it.isRegularFile() && it.extension == Constants.backupFileExtension }

            filesToMove.forEach { file ->
                val destinationFile = destination.resolve(file.name)
                if (destinationFile.exists()) {
                    Timber.w { "Destination file '$destinationFile' already exists, skipping." }
                    pathsMoved.update { listOf(*it.toTypedArray(), file) }
                    return@forEach
                }

                Timber.i { "Moving file '$file' to '$destinationFile'." }
                runCatching {
                    file.moveTo(destinationFile)
                    pathsMoved.update { listOf(*it.toTypedArray(), file) }
                }
                    .onFailure { Timber.e(it) { "Unable to move file '$file' to '$destinationFile'." } }
            }
        }
    }
}