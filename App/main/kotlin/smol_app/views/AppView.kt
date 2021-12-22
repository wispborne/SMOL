import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.jetbrains.Children
import com.arkivanov.decompose.extensions.compose.jetbrains.animation.child.crossfade
import smol_access.SL
import smol_app.*
import smol_app.browser.ModBrowserView
import smol_app.browser.downloadCard
import smol_app.home.homeView
import smol_app.navigation.Screen
import smol_app.themes.SmolTheme
import smol_app.themes.SmolTheme.toColors
import smol_app.views.FileDropper
import smol_app.views.ProfilesView
import smol_app.views.settingsView

@OptIn(ExperimentalStdlibApi::class, ExperimentalDecomposeApi::class)
@Composable
@Preview
fun WindowState.appView() {
    val theme = SL.themeManager.activeTheme.collectAsState()
    val toasterState = remember { ToasterState() }

    var alertDialogBuilder: @Composable (() -> Unit)? by remember { mutableStateOf(null) }
    val appState by remember {
        mutableStateOf(AppState(this).apply {
            this.alertDialogSetter = { alertDialogBuilder = it }
        })
    }

    MaterialTheme(
        colors = theme.value.second.toColors(),
        typography = Typography(
            button = TextStyle(fontFamily = SmolTheme.orbitronSpaceFont)
        )
    ) {
        Box(Modifier.background(MaterialTheme.colors.background)) {
            Children(router.state, animation = crossfade()) { screen ->
                Box {
                    when (screen.configuration) {
                        is Screen.Home -> appState.homeView()
                        is Screen.Settings -> appState.settingsView()
                        is Screen.Profiles -> appState.ProfilesView()
                        is Screen.ModBrowser -> appState.ModBrowserView()
                    }.run { }
                    appState.FileDropper()

                    // Downloads
                    val downloads = SL.UI.downloadManager.downloads.collectAsState().value
                    toaster(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                        toasterState = toasterState,
                        items = downloads.map {
                            Toast(id = it.id, timeoutMillis = null) {
                                downloadCard(download = it,
                                    requestToastDismissal = {
                                        if (!toasterState.timersByToastId.containsKey(it.id)) {
                                            toasterState.timersByToastId[it.id] = 0
                                        }
                                    })
                            }
                        },
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    )

                    // Uncomment to clear out completed downloads after 10s.
//                downloads.map { it to it.status.collectAsState() }
//                    .filter { (_, status) -> status.value is DownloadItem.Status.Completed }
//                    .forEach { (item, _) ->
//                        if (!toasterState.timersByToastId.containsKey(item.id)) {
//                            toasterState.timersByToastId[item.id] = Toaster.defaultTimeoutMillis
//                        }
//                    }
                }
            }

            if (alertDialogBuilder != null) {
                alertDialogBuilder?.invoke()
            }
        }
    }
}

class AppState(windowState: WindowState) : IWindowState by windowState {

    /**
     * Usage: alertDialogSetter.invoke { AlertDialog(...) }
     */
    lateinit var alertDialogSetter: (@Composable (() -> Unit)?) -> Unit
}