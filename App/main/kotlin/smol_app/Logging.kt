package smol_app

import org.tinylog.Level
import org.tinylog.Logger
import org.tinylog.configuration.Configuration
import timber.LogLevel
import timber.Timber
import kotlin.properties.Delegates

object Logging {
    var logLevel by Delegates.observable(Level.DEBUG) { _, _, _ ->
        setup()
    }

    fun setup() {
        val format = "{date} {class}.{method}:{line} {level}: {message}"
        val level = if (safeMode) "trace" else logLevel.name.lowercase()
        Configuration.replace(
            mapOf(
                "writer1" to "console",
                "writer1.level" to level,
                "writer1.format" to format,

                "writer2" to "rolling file",
                "writer2.level" to level,
                "writer2.format" to format,
                "writer2.file" to "SMOL_log.{count}.log",
                "writer2.buffered" to "true",
                "writer2.backups" to "2",
                "writer2.policies" to "size: 10mb",
            )
        )

        Thread.setDefaultUncaughtExceptionHandler { _, ex ->
            Timber.e(ex)
        }

        Timber.uprootAll()
        Timber.plant(object : Timber.Tree() {
            override fun log(priority: LogLevel, tag: String?, message: String, t: Throwable?) {
                val messageMaker = { "${if (tag != null) "$tag/ " else ""}$message" }
                when (priority) {
                    LogLevel.VERBOSE -> Logger.trace(t, messageMaker)
                    LogLevel.DEBUG -> Logger.debug(t, messageMaker)
                    LogLevel.INFO -> Logger.info(t, messageMaker)
                    LogLevel.WARN -> Logger.warn(t, messageMaker)
                    LogLevel.ERROR -> Logger.error(t, messageMaker)
                    LogLevel.ASSERT -> Logger.error(t, messageMaker)
                }
            }
        })
    }
}