package smol_app

import androidx.compose.runtime.*
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import appView
import com.arkivanov.decompose.Router
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import net.sf.sevenzipjbinding.SevenZip
import org.tinylog.Logger
import smol_access.Constants
import smol_access.SL
import smol_app.navigation.Screen
import smol_app.navigation.rememberRouter
import smol_app.util.SmolPair
import smol_app.util.SmolWindowState
import smol_app.util.currentPlatform
import timber.LogLevel
import utilities.makeFinite


var safeMode = false

fun main() = application {
    val uiConfig = UIConfig(SL.gson)
    val access = SL.access

    // Logger
    kotlin.runCatching {
        Logging.logLevel = if (safeMode) LogLevel.VERBOSE
        else LogLevel.DEBUG
        Logging.setup()
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
        title = Constants.APP_NAME,
        icon = painterResource("kotlin-icon.svg"),
        onPreviewKeyEvent = { event -> onKeyEventHandlers.any { it(event) } }
    ) {
        val router = rememberRouter<Screen>(
            initialConfiguration = { Screen.Home },
            handleBackButton = true
        )

        val appState by remember {
            mutableStateOf(AppState().apply {
                this.router = router
                this.window = this@Window.window
                this.onWindowKeyEventHandlers = onKeyEventHandlers
            })
        }

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

class AppState {
    lateinit var router: Router<Screen, Any>
    lateinit var window: ComposeWindow
    lateinit var onWindowKeyEventHandlers: MutableList<(KeyEvent) -> Boolean>
}