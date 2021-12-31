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
import org.cef.CefApp
import org.tinylog.Logger
import smol_access.Constants
import smol_access.SL
import smol_app.navigation.Screen
import smol_app.navigation.rememberRouter
import smol_app.util.SmolPair
import smol_app.util.SmolWindowState
import smol_app.util.currentPlatform
import timber.LogLevel
import timber.ktx.Timber
import utilities.makeFinite


var safeMode = false

fun main() = application {
    // Logger
    kotlin.runCatching {
        Logging.logLevel =
            if (safeMode) LogLevel.VERBOSE
            else LogLevel.DEBUG
        Logging.setup()
    }
        .onFailure { println(it) }

    doUpdateStuff()

    var appWindowState = rememberWindowState()

    val uiConfig = SL.UI.uiConfig
    val access = SL.access

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
            .onSuccess { appWindowState = it }
            .onFailure {
                uiConfig.windowState = SmolWindowState(
                    "", false, SmolPair(0f, 0f), SmolPair(0f, 0f)
                )
            }


        SL.modRepo.refreshFromInternet()
    }


    val onKeyEventHandlers = remember { mutableListOf<(KeyEvent) -> Boolean>() }

    Window(
        onCloseRequest = ::onQuit,
        state = appWindowState,
        title = Constants.APP_NAME,
        icon = painterResource("kotlin-icon.svg"),
        onPreviewKeyEvent = { event -> onKeyEventHandlers.any { it(event) } }
    ) {
        val router = rememberRouter<Screen>(
            initialConfiguration = { Screen.Home },
            handleBackButton = true
        )

        val smolWindowState by remember {
            mutableStateOf(WindowState().apply {
                this.router = router
                this.window = this@Window.window
                this.onWindowKeyEventHandlers = onKeyEventHandlers
            })
        }

        saveWindowParamsOnChange(appWindowState, uiConfig)

        smolWindowState.appView()
    }
}

private fun ApplicationScope.onQuit() {
    kotlin.runCatching {
        CefApp.getInstance().dispose()
        Timber.i { "Shut down JCEF." }
    }
        .onFailure { Timber.d(it) }

    exitApplication()
}

fun doUpdateStuff() {
}

@Composable
private fun saveWindowParamsOnChange(
    appWindowState: androidx.compose.ui.window.WindowState,
    uiConfig: UIConfig
) {
    LaunchedEffect(appWindowState) {
        snapshotFlow { appWindowState.size }
            .onEach {
                uiConfig.windowState = uiConfig.windowState?.copy(
                    size = SmolPair(it.width.value.makeFinite(), it.height.value.makeFinite())
                )
            }
            .launchIn(this)

        snapshotFlow { appWindowState.isMinimized }
            .onEach {
                uiConfig.windowState = uiConfig.windowState?.copy(
                    isMinimized = it
                )
            }
            .launchIn(this)

        snapshotFlow { appWindowState.placement }
            .onEach {
                uiConfig.windowState = uiConfig.windowState?.copy(
                    placement = it.name
                )
            }
            .launchIn(this)

        snapshotFlow { appWindowState.position }
            .onEach {
                uiConfig.windowState = uiConfig.windowState?.copy(
                    position = SmolPair(it.x.value.makeFinite(), it.y.value.makeFinite()),
                )
            }
            .launchIn(this)
    }
}


interface IWindowState {
    var router: Router<Screen, Any>
    var window: ComposeWindow
    var onWindowKeyEventHandlers: MutableList<(KeyEvent) -> Boolean>
}

class WindowState : IWindowState {
    override lateinit var router: Router<Screen, Any>
    override lateinit var window: ComposeWindow
    override lateinit var onWindowKeyEventHandlers: MutableList<(KeyEvent) -> Boolean>
}