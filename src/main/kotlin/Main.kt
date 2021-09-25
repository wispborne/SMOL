import androidx.compose.runtime.*
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.res.painterResource
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
import util.SmolPair
import util.SmolWindowState
import util.toFileOrNull
import views.FileDropper
import java.io.File

var safeMode = false

fun main() = application {
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
            Logger.error(ex)
        }
    }
        .onFailure { println(it) }

    var newState = rememberWindowState()

    if (!safeMode) {
        SevenZip.initSevenZipFromPlatformJAR()

        checkAndSetDefaultPaths()

        kotlin.runCatching {
            val savedState = SL.appConfig.windowState!!
            rememberWindowState(
                placement = WindowPlacement.valueOf(savedState.placement),
                isMinimized = savedState.isMinimized,
                position = WindowPosition(savedState.position.first.dp, savedState.position.second.dp),
                size = WindowSize(savedState.size.first.dp, savedState.size.second.dp)
            )
        }
            .onSuccess { newState = it }
            .onFailure {
                SL.appConfig.windowState = SmolWindowState(
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
                    SL.appConfig.windowState = SL.appConfig.windowState?.copy(
                        size = SmolPair(it.width.value.makeFinite(), it.height.value.makeFinite())
                    )
                }
                .launchIn(this)

            snapshotFlow { newState.isMinimized }
                .onEach {
                    SL.appConfig.windowState = SL.appConfig.windowState?.copy(
                        isMinimized = it
                    )
                }
                .launchIn(this)

            snapshotFlow { newState.placement }
                .onEach {
                    SL.appConfig.windowState = SL.appConfig.windowState?.copy(
                        placement = it.name
                    )
                }
                .launchIn(this)

            snapshotFlow { newState.position }
                .onEach {
                    SL.appConfig.windowState = SL.appConfig.windowState?.copy(
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

fun Float.makeFinite() =
    if (!this.isFinite()) 0f
    else this

fun checkAndSetDefaultPaths() {
    val appConfig = SL.appConfig

    if (!SL.gamePath.isValidGamePath(appConfig.gamePath ?: "")) {
        appConfig.gamePath = SL.gamePath.getDefaultStarsectorPath()?.absolutePath
    }

    if (appConfig.archivesPath.toFileOrNull()?.exists() != true) {
        appConfig.archivesPath = File(System.getProperty("user.home"), "SMOL/archives").absolutePath
    }

    SL.archives.getArchivesManifest()
        .also { Logger.debug { "Archives folder manifest: ${it?.manifestItems?.keys?.joinToString()}" } }

    if (appConfig.stagingPath.toFileOrNull()?.exists() != true) {
        appConfig.stagingPath = File(System.getProperty("user.home"), "SMOL/staging").absolutePath
    }

    Logger.debug { "Game: ${appConfig.gamePath}" }
    Logger.debug { "Archives: ${appConfig.archivesPath}" }
    Logger.debug { "Staging: ${appConfig.stagingPath}" }
}

class AppState(
    val router: Router<Screen, Any>,
    val window: ComposeWindow
)