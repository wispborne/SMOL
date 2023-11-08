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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
                modModificationStateHolder.state.update {
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
                modModificationStateHolder.state.update {
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