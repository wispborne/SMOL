package smol_access.business

import smol_access.config.GamePath
import utilities.Platform
import smol_access.model.Vmparams
import utilities.IOLock
import timber.ktx.Timber
import kotlin.io.path.*

class VmParamsManager(
    gamePath: GamePath,
    private val platform: Platform
) {
    private val path = gamePath.path.value?.let {
        when (platform) {
            Platform.Windows -> it.resolve("vmparams")
            Platform.MacOS -> TODO()
            Platform.Linux -> it.resolve("starsector.sh")
        }
    }

    private val backupPath = gamePath.path.value?.let {
        when (platform) {
            Platform.Windows -> it.resolve("vmparams.bak")
            Platform.MacOS -> TODO()
            Platform.Linux -> it.resolve("starsector.sh.bak")
        }
    }

    fun read(): Vmparams? =
        IOLock.read {
            if (path?.exists() != true) {
                null
            } else {
                Vmparams(path.readText())
            }
        }

    fun write(vmparams: Vmparams) {
        IOLock.read {
            path ?: return

            if (backupPath == null) {
                Timber.e { "Backup path was null!" }
                return
            }

            if (backupPath.notExists()) {
                backupPath.createFile()
                read()?.fullString?.let { backupPath.writeText(it) }
            }

            if (backupPath.notExists()) {
                Timber.e { "No backup file ($backupPath), aborting write of $path!" }
                return@read
            }

            IOLock.write {
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
        }
    }

    fun update(mutator: (Vmparams?) -> Vmparams?): Vmparams? =
        mutator(read())?.also { write(it) }
}