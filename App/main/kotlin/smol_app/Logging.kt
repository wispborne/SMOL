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

package smol_app

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.tinylog.Level
import org.tinylog.Logger
import org.tinylog.configuration.Configuration
import smol_access.Constants
import timber.LogLevel
import timber.Timber
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.name
import kotlin.properties.Delegates

object Logging {
    private var wasTinyLogConfigured = false // Dumb library doesn't let you reconfigure and crashes if you try.
    val logFlow = MutableSharedFlow<LogMessage>(
        replay = 200,
        extraBufferCapacity = 200,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val logPath = Path.of("").absolute().resolve("SMOL.log")

    data class LogMessage(val logLevel: LogLevel, val message: String)

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
        val format = "{message}"
        val tinyLogLevelLower = tinyLogLevel.name.lowercase()

        if (!wasTinyLogConfigured) {
            println("Configuring TinyLog.")
            Configuration.replace(
                mapOf(
                    "writerRoll" to "rolling file",
                    "writerRoll.level" to tinyLogLevelLower,
                    "writerRoll.format" to format,
                    "writerRoll.file" to "SMOL.{count}.log",
                    "writerRoll.latest" to logPath.name,
                    "writerRoll.buffered" to "true",
                    "writerRoll.backups" to "2",
                    "writerRoll.policies" to "size: 50mb",
                    "writerRoll.convert" to "gzip",

                    "writingthread" to "true",
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
                appenders = listOf(
                    { level, formattedMessage ->
                        logFlow.tryEmit(
                            LogMessage(
                                logLevel = level,
                                message = formattedMessage
                            )
                        )
                    },
                    { priority, formattedMessage ->
                        when (priority) {
                            LogLevel.VERBOSE -> Logger.trace { formattedMessage }
                            LogLevel.DEBUG -> Logger.debug { formattedMessage }
                            LogLevel.INFO -> Logger.info { formattedMessage }
                            LogLevel.WARN -> Logger.warn { formattedMessage }
                            LogLevel.ERROR -> Logger.error { formattedMessage }
                            LogLevel.ASSERT -> Logger.error { formattedMessage }
                        }
                    }
                )
            )
        )

//        Timber.plant(tinyLoggerTree())
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
//
//    private fun setupLog4J() {
//        val builder = ConfigurationBuilderFactory.newConfigurationBuilder()
//
//        builder.setStatusLevel(Level.ERROR)
//        builder.setConfigurationName("RollingBuilder")
//
//        // create a rolling file appender
//        builder.add(
//            builder.newAppender("rolling", "RollingFile")
//                .addAttribute("fileName", "logs/SMOL.log")
//                .addAttribute("filePattern", "logs/SMOL-%d{MM-dd-yy}.log.gz")
//                .add(
//                    builder.newLayout("PatternLayout")
//                        .addAttribute("pattern", "%d [%t] %-5level: %msg%n")
//                )
//                .addComponent(
//                    builder.newComponent("Policies")
//                        .addComponent(builder.newComponent("SizeBasedTriggeringPolicy").addAttribute("size", "50M"))
//                )
//        )
//
//        // create the new logger
//        builder.add(
//            builder.newLogger("TestLogger", Level.DEBUG)
//                .add(builder.newAppenderRef("rolling"))
//                .addAttribute("additivity", false)
//        )
//
//        builder.add(
//            builder.newRootLogger(Level.DEBUG)
//                .add(builder.newAppenderRef("rolling"))
//        )
//        val ctx: LoggerContext = Configurator.initialize(builder.build())
//    }
}