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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.tinylog.Logger
import smol_access.Constants
import smol_access.SL
import smol_access.business.Archives
import smol_access.business.KWatchEvent
import smol_access.business.asWatchChannel
import smol_app.IWindowState
import smol_app.UI
import smol_app.WindowState
import smol_app.browser.ModBrowserView
import smol_app.home.homeView
import smol_app.navigation.Screen
import smol_app.settings.settingsView
import smol_app.themes.SmolTheme
import smol_app.themes.SmolTheme.toColors
import smol_app.toasts.Toast
import smol_app.toasts.downloadToast
import smol_app.toasts.toastInstalledCard
import smol_app.toasts.toaster
import smol_app.views.FileDropper
import smol_app.views.ProfilesView
import timber.ktx.Timber
import utilities.IOLock
import utilities.toPathOrNull
import kotlin.io.path.exists

@OptIn(ExperimentalStdlibApi::class, ExperimentalDecomposeApi::class)
@Composable
@Preview
fun WindowState.appView() {
    val theme = SL.themeManager.activeTheme.collectAsState()

    var alertDialogBuilder: @Composable ((dismiss: () -> Unit) -> Unit)? by remember { mutableStateOf(null) }
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
                                Timber.i { "Added mods are the same as existing mods, this is probably startup. Not adding 'mod found' toasts." }
                                return@collect
                            }

                            addedModVariants
                                .forEach { newModVariant ->
                                    Timber.i { "Found new mod ${newModVariant.modInfo.id} ${newModVariant.modInfo.version}." }
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

            val scope = rememberCoroutineScope()
            var isRefreshingMods = false
            val refreshTrigger by remember { mutableStateOf(Unit) }
            val onRefreshingMods = { refreshing: Boolean -> isRefreshingMods = refreshing }

            LaunchedEffect(refreshTrigger) {
                scope.launch {
                    Timber.i { "Initial mod refresh." }
                    reloadModsInner()
                }
            }

            DisposableEffect(Unit) {
                val handle = scope.launch {
                    watchDirsAndReloadOnChange(scope)
                }
                onDispose { handle.cancel() }
            }

            if (alertDialogBuilder != null) {
                alertDialogBuilder?.invoke { appState.alertDialogSetter(null) }
            }
        }
    }
}

class AppState(windowState: WindowState) : IWindowState by windowState {

    /**
     * Usage: alertDialogSetter.invoke { AlertDialog(...) }
     */
    lateinit var alertDialogSetter: (@Composable ((dismiss: () -> Unit) -> Unit)?) -> Unit

    suspend fun reloadMods() = reloadModsInner()
}


@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun watchDirsAndReloadOnChange(scope: CoroutineScope) {
    val NSA: List<Flow<KWatchEvent?>> = listOf(
        SL.access.getStagingPath()?.toPathOrNull()?.asWatchChannel(scope = scope) ?: emptyFlow(),
        SL.access.getStagingPath()?.toPathOrNull()
            ?.resolve(Archives.ARCHIVE_MANIFEST_FILENAME) // Watch manifest.json
            ?.run { if (this.exists()) this.asWatchChannel(scope = scope) else emptyFlow() } ?: emptyFlow(),
        SL.gamePath.getModsPath().asWatchChannel(scope = scope),
        SL.gamePath.getModsPath().resolve(Constants.ENABLED_MODS_FILENAME) // Watch enabled_mods.json
            .run { if (this.exists()) this.asWatchChannel(scope = scope) else emptyFlow() },
        SL.archives.getArchivesPath()?.toPathOrNull()?.asWatchChannel(scope = scope) ?: emptyFlow(),
    )
    Timber.i {
        "Started watching folders ${
            NSA.joinToString()
        }"
    }
    withContext(Dispatchers.Default) {
        NSA
            .plus(SL.manualReloadTrigger.trigger.map { null })
            .merge()
            .collectLatest {
                if (!IOLock.stateFlow.value) {
                    if (it?.kind == KWatchEvent.Kind.Initialized)
                        return@collectLatest
                    // Short delay so that if a new file change comes in during this time,
                    // this is canceled in favor of the new change. This should prevent
                    // refreshing 500 times if 500 files are changed in a few millis.
                    delay(1000)
                    Logger.info { "Trying to reload to due to file change: $it" }
                    reloadModsInner()
                } else {
                    Logger.info { "Skipping mod reload while IO locked." }
                }
            }
    }
}

private suspend fun reloadModsInner() {
    if (SL.access.areModsLoading.value) {
        Logger.info { "Skipping reload of mods as they are currently refreshing already." }
        return
    }
    try {
        coroutineScope {
            utilities.trace(onFinished = { _, millis -> Timber.i { "Finished reloading everything in ${millis}ms (this is not how long it took to reload just the mods)." } }) {
                Timber.i { "Reloading mods." }
                SL.access.reload()
                val mods = SL.access.mods.value?.mods ?: emptyList()

                listOf(
                    async {
                        SL.versionChecker.lookUpVersions(
                            forceLookup = false,
                            mods = mods
                        )
                    },
                    async {
                        SL.archives.refreshArchivesManifest()
                    },
                    async {
                        SL.saveReader.readAllSaves()
                    }
                ).awaitAll()
            }
        }
    } catch (e: Exception) {
        Logger.debug(e)
    }
}