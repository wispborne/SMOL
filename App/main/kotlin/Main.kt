import androidx.compose.runtime.*
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.arkivanov.decompose.Router
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import navigation.Screen
import navigation.rememberRouter
import net.sf.sevenzipjbinding.SevenZip
import org.tinylog.Logger
import org.tinylog.configuration.Configuration
import smol_access.APP_NAME
import smol_access.SL
import timber.LogLevel
import timber.Timber
import util.SmolPair
import util.SmolWindowState
import util.currentPlatform
import utilities.makeFinite

var safeMode = false

fun main() = application {
    val uiConfig = UIConfig(SL.gson)
    val access = SL.access

    // Logger
    kotlin.runCatching {
        val format = "{date} {class}.{method}:{line} {level}: {message}"
        val level = if (safeMode) "trace" else "debug"
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
        .onFailure { println(it) }

    var newState = rememberWindowState()


    kotlin.runCatching {
        access.checkAndSetDefaultPaths(currentPlatform)
    }
        .onFailure {
            if (safeMode) {
                SL.appConfig.clear()
                Logger.warn(it) { "SAFE MODE: Cleared app config due to error." }
            }
        }

    if (!safeMode) {
        SevenZip.initSevenZipFromPlatformJAR()

        kotlin.runCatching {
            val savedState = uiConfig.windowState!!
            rememberWindowState(
                placement = WindowPlacement.valueOf(savedState.placement),
                isMinimized = savedState.isMinimized,
                position = WindowPosition(savedState.position.first.dp, savedState.position.second.dp),
                size = DpSize(savedState.size.first.dp, savedState.size.second.dp)
            )
        }
            .onSuccess { newState = it }
            .onFailure {
                uiConfig.windowState = SmolWindowState(
                    "", false, SmolPair(0f, 0f), SmolPair(0f, 0f)
                )
            }
    }

    val onKeyEventHandlers = mutableListOf<(KeyEvent) -> Boolean>()

    Window(
        onCloseRequest = ::exitApplication,
        state = newState,
        title = APP_NAME,
        icon = painterResource("kotlin-icon.svg"),
        onPreviewKeyEvent = { event -> onKeyEventHandlers.any { it(event) } }
    ) {

        val router = rememberRouter<Screen>(
            initialConfiguration = { Screen.Home },
            handleBackButton = true
        )

        val appState by remember { mutableStateOf(AppState(router, window, onKeyEventHandlers)) }

        LaunchedEffect(newState) {
            snapshotFlow { newState.size }
                .onEach {
                    uiConfig.windowState = uiConfig.windowState?.copy(
                        size = SmolPair(it.width.value.makeFinite(), it.height.value.makeFinite())
                    )
                }
                .launchIn(this)

            snapshotFlow { newState.isMinimized }
                .onEach {
                    uiConfig.windowState = uiConfig.windowState?.copy(
                        isMinimized = it
                    )
                }
                .launchIn(this)

            snapshotFlow { newState.placement }
                .onEach {
                    uiConfig.windowState = uiConfig.windowState?.copy(
                        placement = it.name
                    )
                }
                .launchIn(this)

            snapshotFlow { newState.position }
                .onEach {
                    uiConfig.windowState = uiConfig.windowState?.copy(
                        position = SmolPair(it.x.value.makeFinite(), it.y.value.makeFinite()),
                    )
                }
                .launchIn(this)
        }

        appState.appView()
    }
}

class AppState(
    val router: Router<Screen, Any>,
    val window: ComposeWindow,
    val onWindowKeyEventHandlers: MutableList<(KeyEvent) -> Boolean>
)