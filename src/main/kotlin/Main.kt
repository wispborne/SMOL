import androidx.compose.runtime.*
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.arkivanov.decompose.Router
import config.UIConfig
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import navigation.Screen
import navigation.rememberRouter
import net.sf.sevenzipjbinding.SevenZip
import org.jetbrains.skija.impl.Platform
import org.tinylog.Logger
import org.tinylog.configuration.Configuration
import toothpick.ktp.KTP
import toothpick.ktp.binding.bind
import toothpick.ktp.binding.module
import toothpick.ktp.extension.getInstance
import util.SmolPair
import util.SmolWindowState
import util.makeFinite
import views.FileDropper

var safeMode = false

fun main() = application {
    val scope = KTP.openRootScope().installModules(module {
        bind<UIConfig>().toInstance { UIConfig(SL.moshi) }
        bind<Access>().toInstance { Access() }
    })

    val uiConfig: UIConfig = scope.getInstance()
    val access: Access = scope.getInstance()

    // Logger
    kotlin.runCatching {
        val format = "{date} {class}.{method}:{line} {level}: {message}"
        val level = if (safeMode) "trace" else "trace"
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
            Logger.error(ex)
        }
    }
        .onFailure { println(it) }

    var newState = rememberWindowState()

    if (!safeMode) {
        SevenZip.initSevenZipFromPlatformJAR()

        val currentPlatform =
            when (Platform.CURRENT) {
                Platform.WINDOWS -> config.Platform.Windows
                Platform.MACOS_X64,
                Platform.MACOS_ARM64 -> config.Platform.MacOS
                Platform.LINUX -> config.Platform.Linux
                else -> TODO()
            }

        access.checkAndSetDefaultPaths(currentPlatform)

        kotlin.runCatching {
            val savedState = uiConfig.windowState!!
            rememberWindowState(
                placement = WindowPlacement.valueOf(savedState.placement),
                isMinimized = savedState.isMinimized,
                position = WindowPosition(savedState.position.first.dp, savedState.position.second.dp),
                size = WindowSize(savedState.size.first.dp, savedState.size.second.dp)
            )
        }
            .onSuccess { newState = it }
            .onFailure {
                uiConfig.windowState = SmolWindowState(
                    "", false, SmolPair(0f, 0f), SmolPair(0f, 0f)
                )
            }
    }

    Window(
        onCloseRequest = ::exitApplication,
        state = newState,
        title = "SMOL",
        icon = painterResource("kotlin-icon.svg")
    ) {

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

        val router = rememberRouter<Screen>(
            initialConfiguration = { Screen.Home },
            handleBackButton = true
        )

        val appState by remember { mutableStateOf(AppState(router, window)) }

        appState.appView()
        appState.FileDropper()
    }
}

class AppState(
    val router: Router<Screen, Any>,
    val window: ComposeWindow
)