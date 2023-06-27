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
import kotlinx.coroutines.flow.*
import smol.access.config.GamePathManager
import smol.access.model.Vmparams
import smol.timber.ktx.Timber
import smol.utilities.IOLock
import smol.utilities.Platform
import smol.utilities.isMissingAdmin
import java.nio.file.Path
import kotlin.io.path.*

class VmParamsManager(
    private val gamePathManager: GamePathManager,
    private val platform: Platform
) {
    private val vmparams_ = MutableStateFlow(read())
    val vmparams = vmparams_.asStateFlow()

    private val path = gamePathManager.path
        .map { getVmParamsPath() }
        .filterNotNull()
        .stateIn(scope = CoroutineScope(Job()), started = SharingStarted.Eagerly, initialValue = getVmParamsPath())

    private val backupPath = gamePathManager.path.value?.let {
        when (platform) {
            Platform.Windows -> it.resolve("vmparams.bak")
            Platform.MacOS -> it.resolve("Contents/MacOS/starsector_mac.sh.bak")
            Platform.Linux -> it.resolve("starsector.sh.bak")
        }
    }

    val isMissingAdmin = path
        .map { it?.isMissingAdmin() == true }
        .stateIn(scope = CoroutineScope(Job()), started = SharingStarted.Eagerly, initialValue = false)

    init {
        read()
    }

    private fun getVmParamsPath(): Path? {
        val gamePath = gamePathManager.path
        return when (platform) {
            Platform.Windows -> gamePath.value?.resolve("vmparams")
            Platform.Linux -> gamePath.value?.resolve("starsector.sh")
            Platform.MacOS -> gamePath.value?.resolve("Contents/MacOS/starsector_mac.sh")
        }
    }

    fun read(): Vmparams? =
        IOLock.read {
            if (path?.value?.exists() != true) {
                null
            } else {
                Vmparams(path.value!!.readText())
                    .also { vmparams_.value = it }
            }
        }

    fun write(vmparams: Vmparams) {
        IOLock.write {
            path ?: return

            if (backupPath == null) {
                Timber.e { "Backup path was null! Try running as admin." }
                return
            }

            if (backupPath.notExists()) {
                kotlin.runCatching {
                    backupPath.createFile()
                    read()?.fullString?.let { backupPath.writeText(it) }
                }
                    .onFailure {
                        Timber.w(it) { "Unable to create backup vmparams file. Try running as admin." }
                    }
            }

            if (backupPath.notExists()) {
                Timber.e { "No backup file ($backupPath), aborting write of $path!" }
                return
            }

            if (path.value?.exists() != true) {
                kotlin.runCatching {
                    path.value?.createFile()
                }
                    .onFailure { Timber.w(it) { "Unable to create a vmparams file. Ensure that SMOL has permission (run as admin?)." } }
            }

            kotlin.runCatching {
                path.value?.writeText(vmparams.fullString)
            }
                .onFailure { Timber.w(it) { "Unable to update vmparams file. Ensure that SMOL has permission (run as admin?)." } }
        }

        // Update `vmparams` variable
        read()
    }

    fun update(mutator: (Vmparams?) -> Vmparams?): Vmparams? =
        mutator(read())?.also { write(it) }
}