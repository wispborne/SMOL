@file:OptIn(ExperimentalFoundationApi::class)

package smol_app.home

import AppState
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.push
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.tinylog.Logger
import smol_access.SL
import smol_access.business.Archives
import smol_access.business.GameEnabledMods.Companion.ENABLED_MODS_FILENAME
import smol_access.business.KWatchEvent
import smol_access.business.asWatchChannel
import smol_access.model.Mod
import smol_app.cli.SmolCLI
import smol_app.composables.*
import smol_app.navigation.Screen
import smol_app.themes.SmolTheme
import smol_app.toolbar.*
import smol_app.util.filterModGrid
import smol_app.util.replaceAllUsingDifference
import smol_app.util.vmParamsManager
import timber.ktx.Timber
import utilities.IOLock
import utilities.equalsAny
import utilities.toPathOrNull
import utilities.trace
import kotlin.io.path.exists


@OptIn(
    ExperimentalCoroutinesApi::class, ExperimentalMaterialApi::class,
    ExperimentalComposeUiApi::class
)
@Composable
@Preview
fun AppState.homeView(
    modifier: Modifier = Modifier
) {
    var isRefreshingMods = false
    val refreshTrigger by remember { mutableStateOf(Unit) }
    val mods: SnapshotStateList<Mod> = remember { mutableStateListOf() }
    val shownMods: SnapshotStateList<Mod?> = mods.toMutableStateList()
    val onRefreshingMods = { refreshing: Boolean -> isRefreshingMods = refreshing }
    val scope = rememberCoroutineScope()
    val isWriteLocked = IOLock.stateFlow.collectAsState()

    LaunchedEffect(refreshTrigger) {
        scope.launch {
            Timber.d { "Initial mod refresh." }
            reloadMods()
        }
    }

    scope.launch {
        withContext(Dispatchers.Default) {
            SL.access.mods.collectLatest { freshMods ->
                if (freshMods != null) {
                    withContext(Dispatchers.Main) {
                        mods.replaceAllUsingDifference(freshMods.mods, doesOrderMatter = true)
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val handle = scope.launch {
            watchDirsAndReloadOnChange(scope)
        }
        onDispose { handle.cancel() }
    }


//    var showConfirmMigrateDialog: Boolean by remember { mutableStateOf(false) }
    val showLogPanel = remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(modifier = Modifier.height(SmolTheme.topBarHeight)) {
                launchButton()
                installModsButton()
                Spacer(Modifier.width(16.dp))
                screenTitle(text = "Home")
                profilesButton()
                settingsButton()
                modBrowserButton()
                if (isWriteLocked.value) {
                    CircularProgressIndicator(
                        modifier = Modifier
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    smolSearchField(
                        modifier = Modifier
                            .focusRequester(searchFocusRequester())
                            .widthIn(max = 300.dp)
                            .padding(end = 16.dp)
                            .offset(y = (-3).dp)
                            .align(Alignment.CenterVertically),
                        tooltipText = "Hotkey: Ctrl-F",
                        label = "Filter"
                    ) { query ->
                        if (query.isBlank()) {
                            shownMods.replaceAllUsingDifference(mods, doesOrderMatter = false)
                        } else {
                            shownMods.replaceAllUsingDifference(
                                filterModGrid(query, mods, access = SL.access).ifEmpty { listOf(null) },
                                doesOrderMatter = true
                            )
                        }
                    }

                    // Hide console for now, it's not useful
                    if (false) {
                        consoleTextField(
                            modifier = Modifier
                                .widthIn(max = 300.dp)
                                .padding(end = 16.dp)
                                .offset(y = (-3).dp)
                                .align(Alignment.CenterVertically)
                        )
                    }
                }
            }
        }, content = {
            Box {
                if (SL.gamePath.isValidGamePath(SL.appConfig.gamePath ?: "")) {
                    ModGridView(
                        modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(bottom = 40.dp),
                        mods = (if (shownMods.isEmpty()) mods else shownMods) as SnapshotStateList<Mod?>
                    )
                } else {
                    Column(
                        Modifier.fillMaxWidth().fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "I can't find any mods! Did you set your game path yet?")
                        OutlinedButton(
                            onClick = { router.push(Screen.Settings) },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Settings")
                        }
                    }
                }
            }

            if (showLogPanel.value) {
                logPanel { showLogPanel.value = false }
            }
        },
        bottomBar = {
            BottomAppBar(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    logButtonAndErrorDisplay(showLogPanel = showLogPanel)
                }
            }
        }
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun watchDirsAndReloadOnChange(scope: CoroutineScope) {
    val NSA: List<Flow<KWatchEvent?>> = listOf(
        SL.access.getStagingPath()?.toPathOrNull()?.asWatchChannel(scope = scope) ?: emptyFlow(),
        SL.access.getStagingPath()?.toPathOrNull()
            ?.resolve(Archives.ARCHIVE_MANIFEST_FILENAME) // Watch manifest.json
            ?.run { if (this.exists()) this.asWatchChannel(scope = scope) else emptyFlow() } ?: emptyFlow(),
        SL.gamePath.getModsPath().asWatchChannel(scope = scope),
        SL.gamePath.getModsPath().resolve(ENABLED_MODS_FILENAME) // Watch enabled_mods.json
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
                    reloadMods()
                } else {
                    Logger.info { "Skipping mod reload while IO locked." }
                }
            }
    }
}

suspend fun reloadMods() {
    if (SL.access.areModsLoading.value) {
        Logger.info { "Skipping reload of mods as they are currently refreshing already." }
        return
    }
    try {
        coroutineScope {
            trace(onFinished = { _, millis -> Timber.i { "Finished reloading everything in ${millis}ms (this is not how long it took to reload just the mods)." } }) {
                Timber.d { "Reloading mods." }
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
                    }
                ).awaitAll()
            }
        }
    } catch (e: Exception) {
        Logger.debug(e)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AppState.consoleTextField(
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row {
            var consoleText by remember { mutableStateOf("") }
            SmolOutlinedTextField(
                value = consoleText,
                label = { Text("Console") },
                maxLines = 1,
                singleLine = true,
                onValueChange = { newStr ->
                    consoleText = newStr
                },
                leadingIcon = { Icon(painter = painterResource("console-line.svg"), contentDescription = null) },
                modifier = Modifier
                    .onKeyEvent { event ->
                        return@onKeyEvent if (event.type == KeyEventType.KeyUp && (event.key.equalsAny(
                                Key.Enter,
                                Key.NumPadEnter
                            ))
                        ) {
                            kotlin.runCatching {
                                SmolCLI(
                                    userManager = SL.userManager,
                                    userModProfileManager = SL.userModProfileManager,
                                    vmParamsManager = SL.vmParamsManager,
                                    access = SL.access,
                                    gamePath = SL.gamePath
                                )
                                    .parse(consoleText)
                                consoleText = ""
                            }
                                .onFailure { Logger.warn(it) }
                            true
                        } else false
                    }
            )
        }
    }
}