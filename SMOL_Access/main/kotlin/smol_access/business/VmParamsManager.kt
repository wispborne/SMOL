package smol_access.business

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import smol_access.config.GamePathManager
import smol_access.model.Vmparams
import timber.ktx.Timber
import utilities.IOLock
import utilities.Platform
import utilities.isMissingAdmin
import kotlin.io.path.*

class VmParamsManager(
    gamePathManager: GamePathManager,
    private val platform: Platform
) {
    private val vmparams_ = MutableStateFlow(read())
    val vmparams = vmparams_.asStateFlow()

    private val path = gamePathManager.path.value?.let {
        when (platform) {
            Platform.Windows -> it.resolve("vmparams")
            Platform.MacOS -> TODO()
            Platform.Linux -> it.resolve("starsector.sh")
        }
    }

    private val backupPath = gamePathManager.path.value?.let {
        when (platform) {
            Platform.Windows -> it.resolve("vmparams.bak")
            Platform.MacOS -> TODO()
            Platform.Linux -> it.resolve("starsector.sh.bak")
        }
    }

    init {
        read()
    }

    fun isMissingAdmin() = path?.isMissingAdmin()

    fun read(): Vmparams? =
        IOLock.read {
            if (path?.exists() != true) {
                null
            } else {
                Vmparams(path.readText())
                    .also { vmparams_.value = it }
            }
        }

    fun write(vmparams: Vmparams) {
        IOLock.write {
            path ?: return

            if (backupPath == null) {
                Timber.e { "Backup path was null!" }
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

            if (!path.exists()) {
                kotlin.runCatching {
                    path.createFile()
                }
                    .onFailure { Timber.w(it) { "Unable to create a vmparams file. Ensure that SMOL has permission (run as admin?)." } }
            }

            kotlin.runCatching {
                path.writeText(vmparams.fullString)
            }
                .onFailure { Timber.w(it) { "Unable to update vmparams file. Ensure that SMOL has permission (run as admin?)." } }
        }

        // Update `vmparams` variable
        read()
    }

    fun update(mutator: (Vmparams?) -> Vmparams?): Vmparams? =
        mutator(read())?.also { write(it) }
}