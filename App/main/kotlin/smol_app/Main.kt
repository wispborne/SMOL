package smol_app

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.toArgb
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
import smol_app.browser.chromium.CefBrowserPanel
import smol_app.navigation.Screen
import smol_app.navigation.rememberRouter
import smol_app.util.SmolPair
import smol_app.util.SmolWindowState
import utilities.currentPlatform
import smol_app.util.isJCEFEnabled
import timber.LogLevel
import timber.ktx.Timber
import utilities.makeFinite
import java.util.*
import javax.swing.UIManager
import javax.swing.plaf.ColorUIResource
import kotlin.io.path.inputStream


var safeMode = false

fun main() = application {
    fixWhiteFlashOnStartup()

    // Logger
    kotlin.runCatching {
        Logging.logLevel =
            if (safeMode) LogLevel.VERBOSE
            else LogLevel.INFO
        Logging.setup()
    }
        .onFailure { println(it) }

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
            Constants.VERSION_PROPERTIES_FILE!!.let {
                val props = Properties()
                props.load(it.inputStream())
                props["smol-version"]?.toString()!!
            }
        }
            .onFailure { Timber.w(it) }
            .getOrNull()
            ?.also { Constants.APP_VERSION = it }

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

        LaunchedEffect(Unit) {
            kotlin.runCatching { SL.modRepo.refreshFromInternet() }
                .onFailure { Timber.w(it) }
        }
    }


    val onKeyEventHandlers = remember { mutableListOf<(KeyEvent) -> Boolean>() }

    Window(
        onCloseRequest = ::onQuit,
        state = appWindowState,
        title = "${Constants.APP_NAME} ${Constants.APP_VERSION}",
        icon = painterResource("smolslaught.png"),
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
    if (Constants.isJCEFEnabled()) {
        kotlin.runCatching {
            Timber.i { "Shutting down JCEF..." }
            CefApp.getInstance().dispose()
            CefBrowserPanel.browser?.close(true)
            CefBrowserPanel.cefApp?.dispose()
            Timber.i { "Shut down JCEF." }
        }
            .onFailure { Timber.e(it) }
    }

    exitApplication()
}

@Composable
private fun fixWhiteFlashOnStartup() {
    // doesn't actually work
    val background = MaterialTheme.colors.background
    remember(background) {
        UIManager.getDefaults().apply {
//            put()
        }.putDefaults(arrayOf("control", ColorUIResource(background.toArgb())))
    }
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