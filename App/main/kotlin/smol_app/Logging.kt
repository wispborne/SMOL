package smol_app

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.tinylog.Level
import org.tinylog.Logger
import org.tinylog.configuration.Configuration
import timber.LogLevel
import timber.Timber
import kotlin.properties.Delegates

object Logging {
    private var wasTinyLogConfigured = false // Dumb library doesn't let you reconfigure and crashes if you try.
    val logFlow = MutableSharedFlow<String>(
        replay = 200,
        extraBufferCapacity = 200,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    var logLevel by Delegates.observable(LogLevel.DEBUG) { _, _, _ ->
        setup()
    }

    private val tinyLogLevel
        get() = when (logLevel) {
            LogLevel.VERBOSE -> Level.TRACE
            LogLevel.DEBUG -> Level.DEBUG
            LogLevel.INFO -> Level.INFO
            LogLevel.WARN -> Level.WARN
            LogLevel.ERROR -> Level.ERROR
            LogLevel.ASSERT -> Level.ERROR
        }

    fun setup() {
        val format = "{date:MM-dd HH:mm:ss.SSS} {class}.{method}:{line} {level}: {message}"
        val tinyLogLevelLower = tinyLogLevel.name.lowercase()

        if (!wasTinyLogConfigured) {
            Configuration.replace(
                mapOf(
                    //                "writer1" to "console",
                    //                "writer1.level" to tinyLogLevelLower,
                    //                "writer1.format" to format,

                    "writer2" to "rolling file",
                    "writer2.level" to tinyLogLevelLower,
                    "writer2.format" to format,
                    "writer2.file" to "SMOL_log.{count}.log",
                    "writer2.buffered" to "true",
                    "writer2.backups" to "2",
                    "writer2.policies" to "size: 10mb",
                )
            )
            wasTinyLogConfigured = true
        }

        Thread.setDefaultUncaughtExceptionHandler { _, ex ->
            Timber.e(ex)
        }

        Timber.uprootAll()
        Timber.plant(
            Timber.DebugTree(
                logLevel = logLevel,
                appenders = listOf { logMessage -> logFlow.tryEmit(logMessage) }
            )
        )
        Timber.plant(tinyLoggerTree())
    }

    private fun tinyLoggerTree() = object : Timber.Tree() {
        override fun log(priority: LogLevel, tag: String?, message: String, t: Throwable?) {
            if (priority < logLevel) {
                return
            }

            val messageMaker = { "${if (tag != null) "{$tag} " else ""}$message" }
            when (priority) {
                LogLevel.VERBOSE -> Logger.trace(t, messageMaker)
                LogLevel.DEBUG -> Logger.debug(t, messageMaker)
                LogLevel.INFO -> Logger.info(t, messageMaker)
                LogLevel.WARN -> Logger.warn(t, messageMaker)
                LogLevel.ERROR -> Logger.error(t, messageMaker)
                LogLevel.ASSERT -> Logger.error(t, messageMaker)
            }
        }
    }
}