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

package smol.app

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.sf.sevenzipjbinding.SevenZip
import org.cef.CefApp
import org.tinylog.Logger
import smol.access.Constants
import smol.access.SL
import smol.access.ServiceLocator
import smol.app.browser.chromium.CefBrowserPanel
import smol.app.navigation.Screen
import smol.app.navigation.rememberRouter
import smol.app.util.SmolPair
import smol.app.util.SmolWindowState
import smol.app.util.isJCEFEnabled
import smol.timber.LogLevel
import smol.timber.ktx.Timber
import smol.utilities.currentPlatform
import smol.utilities.makeFinite
import java.time.Instant
import java.util.*
import javax.swing.UIManager
import javax.swing.plaf.ColorUIResource
import kotlin.io.path.inputStream


var safeMode = false

fun main() = application {
    val logLevel = LogLevel.INFO
    val startTime = Instant.now().toEpochMilli()
    fun sinceStartStr() = "(since start: ${(Instant.now().minusMillis(startTime).toEpochMilli())}ms)"
    val coroutineScope = rememberCoroutineScope()

    val initServiceLocatorJob = coroutineScope.launch(Dispatchers.Default) {
        smol.utilities.trace(onFinished = { _, ms -> println("Took ${ms}ms to init ${ServiceLocator::class.simpleName} ${sinceStartStr()}.") }) {
            kotlin.runCatching {
                ServiceLocator.init()
            }
                .onFailure {
                    it.printStackTrace()
                    println("Something terrible has happened and SMOL cannot start. Try 'Run as administrator'.")
                    System.console()?.readLine()
                    // Leave console window open but don't try to open SMOL.
                    // Something awful happened (they probably need to run as admin)
                    throw it
                }
        }
    }

    // Logger
    smol.utilities.trace(onFinished = { _, ms -> println("Took ${ms}ms to init logging ${sinceStartStr()}.") }) {
        kotlin.runCatching {
            Logging.logLevel =
                if (safeMode) LogLevel.VERBOSE
                else logLevel
            Logging.setup()
        }
            .onFailure {
                println(it)
            }
    }

    smol.utilities.trace(onFinished = { _, ms -> println("Took ${ms}ms init 7zip ${sinceStartStr()}.") }) {
        SevenZip.initSevenZipFromPlatformJAR()
    }

    // Load current version
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

    var appWindowState = rememberWindowState()

    // Service Locator takes some time to init, do any work possible before it that doesn't require it.
    runBlocking { initServiceLocatorJob.join() }
    println("${ServiceLocator::class.simpleName} is ready ${sinceStartStr()}.")

    // Load UI Config
    val uiConfig = kotlin.runCatching { SL.UI.uiConfig }
        .onFailure {
            it.printStackTrace()
            Timber.e(it)
        }
        .getOrNull()

    val access = kotlin.runCatching { SL.access }
        .onFailure {
            it.printStackTrace()
            Timber.e(it)
        }
        .getOrNull()

    smol.utilities.trace(onFinished = { _, ms -> println("Took ${ms}ms to set default paths ${sinceStartStr()}.") }) {
        kotlin.runCatching {
            access?.checkAndSetDefaultPaths(currentPlatform)
        }
            .onFailure {
                if (safeMode) {
                    SL.appConfig.clear()
                    Logger.warn(it) { "SAFE MODE: Cleared app config due to error." }
                }
            }
    }

    if (!safeMode) {
        kotlin.runCatching {
            val savedState = uiConfig!!.windowState!!
            rememberWindowState(
                placement = WindowPlacement.valueOf(savedState.placement),
                isMinimized = savedState.isMinimized,
                position = WindowPosition(savedState.position.first.dp, savedState.position.second.dp),
                size = DpSize(savedState.size.first.dp, savedState.size.second.dp)
            )
        }
            .onSuccess { appWindowState = it }
            .onFailure {
                uiConfig?.windowState = SmolWindowState(
                    "", false, SmolPair(0f, 0f), SmolPair(0f, 0f)
                )
            }

        LaunchedEffect(Unit) {
            delay(4000) // Doesn't need to contribute to startup time.
            kotlin.runCatching { SL.modRepo.refreshFromInternet(SL.appConfig.updateChannel) }
                .onFailure { Timber.w(it) }
        }
    }


    Timber.i { "Launched SMOL ${Constants.APP_VERSION} ${sinceStartStr()}." }

    val onKeyEventHandlers = remember { mutableListOf<(KeyEvent) -> Boolean>() }

    Window(
        onCloseRequest = ::onQuit,
        state = appWindowState,
        title = "${Constants.APP_NAME} ${Constants.APP_VERSION}",
        icon = painterResource("smol.ico"),
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

        saveWindowParamsOnChange(appWindowState, uiConfig!!)

        smol.utilities.trace(onFinished = { _, ms -> println("Took ${ms}ms to show AppView ${sinceStartStr()}.") }) {
            smolWindowState.appView()
        }
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