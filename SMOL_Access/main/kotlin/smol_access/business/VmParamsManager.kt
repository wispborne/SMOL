package smol_access.business

import smol_access.config.GamePath
import smol_access.config.Platform
import smol_access.model.Vmparams
import utilities.IOLock
import timber.ktx.Timber
import kotlin.io.path.*

class VmParamsManager(
    gamePath: GamePath,
    private val platform: Platform
) {
    private val path = gamePath.get()?.let {
        when (platform) {
            Platform.Windows -> it.resolve("vmparams")
            Platform.MacOS -> TODO()
            Platform.Linux -> it.resolve("starsector.sh")
        }
    }

    private val backupPath = gamePath.get()?.let {
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

    fun write(vmparams: Vmparams) =
        IOLock.read {
            path ?: return@write

            if (backupPath == null) {
                Timber.e { "Backup path was null!" }
                return@write
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
                    path.createFile()
                }

                path.writeText(vmparams.fullString)
            }
        }

    fun update(mutator: (Vmparams?) -> Vmparams?): Vmparams? =
        mutator(read())?.also { write(it) }
}