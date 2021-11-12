package business

import config.GamePath
import config.Platform
import model.Vmparams
import org.tinylog.Logger
import util.IOLock
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
                Logger.error { "Backup path was null!" }
                return@write
            }

            if (backupPath.notExists()) {
                backupPath.createFile()
                read()?.fullString?.let { backupPath.writeText(it) }
            }

            if (backupPath.notExists()) {
                Logger.error { "No backup file ($backupPath), aborting write of $path!" }
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