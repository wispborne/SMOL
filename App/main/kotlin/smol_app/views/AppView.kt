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
import kotlinx.coroutines.flow.collect
import smol_access.SL
import smol_app.IWindowState
import smol_app.UI
import smol_app.WindowState
import smol_app.browser.ModBrowserView
import smol_app.home.homeView
import smol_app.navigation.Screen
import smol_app.themes.SmolTheme
import smol_app.themes.SmolTheme.toColors
import smol_app.toasts.Toast
import smol_app.toasts.downloadToast
import smol_app.toasts.toastInstalledCard
import smol_app.toasts.toaster
import smol_app.views.FileDropper
import smol_app.views.ProfilesView
import smol_app.settings.settingsView
import timber.ktx.Timber

@OptIn(ExperimentalStdlibApi::class, ExperimentalDecomposeApi::class)
@Composable
@Preview
fun WindowState.appView() {
    val theme = SL.themeManager.activeTheme.collectAsState()

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
                    val configuration = screen.configuration
                    when (configuration) {
                        is Screen.Home -> appState.homeView()
                        is Screen.Settings -> appState.settingsView()
                        is Screen.Profiles -> appState.ProfilesView()
                        is Screen.ModBrowser -> appState.ModBrowserView(defaultUrl = configuration.defaultUri)
                    }.run { }
                    appState.FileDropper()

                    // Toasts
                    toaster(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
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

                    LaunchedEffect(Unit) {
                        SL.access.mods.collect { modListUpdate ->
                            val addedModVariants = modListUpdate?.added ?: return@collect

                            if (addedModVariants == modListUpdate.mods.flatMap { it.variants }) {
                                Timber.d { "Added mods are the same as existing mods, this is probably startup. Not adding 'mod found' toasts." }
                                return@collect
                            }

                            addedModVariants
                                .forEach { newModVariant ->
                                    val id = "new-" + newModVariant.smolId
                                    SL.UI.toaster.addItem(Toast(
                                        id = id,
                                        timeoutMillis = null,
                                        useStandardToastFrame = true
                                    ) {
                                        toastInstalledCard(
                                            modVariant = newModVariant,
                                            requestToastDismissal = {
                                                if (!SL.UI.toaster.timersByToastId.containsKey(id)) {
                                                    SL.UI.toaster.timersByToastId[id] = 0
                                                }
                                            }
                                        )
                                    })
                                }
                        }

                    }

                    LaunchedEffect(Unit) {
                        val items = SL.UI.toaster.items
                        SL.UI.downloadManager.downloads.collect { downloads ->
                            downloads
                                .filter { it.id !in items.value.map { it.id } }
                                .map {
                                    Toast(id = it.id, timeoutMillis = null, useStandardToastFrame = true) {
                                        downloadToast(
                                            download = it,
                                            requestToastDismissal = {
                                                if (!SL.UI.toaster.timersByToastId.containsKey(it.id)) {
                                                    SL.UI.toaster.timersByToastId[it.id] = 0
                                                }
                                            })
                                    }
                                }
                                .also {
                                    SL.UI.toaster.addItems(it)
                                }
                        }
                    }
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