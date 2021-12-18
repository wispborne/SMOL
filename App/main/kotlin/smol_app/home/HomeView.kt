package smol_app.home

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.push
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.tinylog.Logger
import smol_access.SL
import smol_access.business.Archives
import smol_access.business.GameEnabledMods.Companion.ENABLED_MODS_FILENAME
import smol_access.business.KWatchEvent
import smol_access.business.asWatchChannel
import smol_access.config.Platform
import smol_access.model.Mod
import smol_access.util.IOLock
import smol_app.*
import smol_app.cli.SmolCLI
import smol_app.composables.*
import smol_app.navigation.Screen
import smol_app.themes.SmolTheme
import smol_app.themes.SmolTheme.withBrightness
import smol_app.util.currentPlatform
import smol_app.util.filterModGrid
import smol_app.util.replaceAllUsingDifference
import smol_app.util.vmParamsManager
import smol_app.views.vmParamsContextMenu
import timber.LogLevel
import timber.ktx.Timber
import utilities.equalsAny
import utilities.toFileOrNull
import utilities.toPathOrNull
import utilities.trace
import java.awt.FileDialog
import kotlin.io.path.absolutePathString
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
    var refreshTrigger by remember { mutableStateOf(Unit) }
    val mods: SnapshotStateList<Mod> = remember { mutableStateListOf() }
    val shownMods: SnapshotStateList<Mod?> = mods.toMutableStateList()
    val onRefreshingMods = { refreshing: Boolean -> isRefreshingMods = refreshing }
    val scope = rememberCoroutineScope()
    val isWriteLocked = IOLock.stateFlow.collectAsState()
    val bottomBarHeight = 64.dp

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
                        mods.replaceAllUsingDifference(freshMods, doesOrderMatter = true)
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
    var showLogPanel by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                modifier = Modifier.height(72.dp)
            ) {
                launchButton()
                ramButton()

                installModsButton(Modifier.padding(start = 16.dp))
                refreshButton()
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

                    consoleTextField(
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .padding(end = 16.dp)
                            .align(Alignment.CenterVertically)
                    )
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

            if (showLogPanel) {
                logPanel(bottomBarHeight, scope) { showLogPanel = false }
            }

            val toasterState = remember { ToasterState() }
            toaster(toasterState = toasterState)
            LaunchedEffect(Unit) {
                toasterState.addItem(Toast { SmolButton(onClick = {}) { Text("test") } })
            }
        },
        bottomBar = {
            BottomAppBar(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row {
                        SmolTooltipArea(tooltip = { SmolTooltipText(text = "Show/Hide Logs") }) {
                            IconButton(
                                modifier = Modifier.padding(start = 8.dp),
                                onClick = { showLogPanel = showLogPanel.not() }
                            ) {
                                Icon(painter = painterResource("icon-log.svg"), contentDescription = null)
                            }
                        }

                        var newestLogLine by remember { mutableStateOf("") }
                        LaunchedEffect(Unit) {
                            scope.launch {
                                Logging.logFlow.collectLatest {
                                    if (it.trim().startsWith("E/", ignoreCase = true)) {
                                        newestLogLine = it
                                    }
                                }
                            }
                        }
                        Text(
                            text = newestLogLine,
                            modifier = Modifier.align(Alignment.CenterVertically).padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun AppState.logPanel(
    bottomBarHeight: Dp,
    scope: CoroutineScope,
    onHideModPanel: () -> Unit
) {
    val horzScrollState = rememberScrollState()
    Card(
        modifier = Modifier
            .width((window.width / 2).dp)
            .fillMaxHeight()
            .padding(bottom = bottomBarHeight, top = 8.dp, start = 8.dp, end = 8.dp),
        shape = RectangleShape
    ) {
        val linesToShow = 200
        val log = remember { mutableStateListOf<String>() }
        var text by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            scope.launch {
                Logging.logFlow.collect {
                    if (log.size >= linesToShow) log.removeFirstOrNull()
                    log.add(it)
                    text = log.joinToString(separator = "\n")
                }
            }
        }

        Box(modifier = Modifier.padding(8.dp)) {
            val lazyListState = rememberLazyListState()
            Column {
                Row {
                    val selectedLogLevel = remember { Logging.logLevel }
                    SmolDropdownWithButton(
                        modifier = Modifier.padding(bottom = 4.dp),
                        items = LogLevel.values().map {
                            SmolDropdownMenuItem(
                                text = it.name.lowercase().replaceFirstChar { it.uppercaseChar() }) {
                                Logging.logLevel = it
                            }
                        },
                        initiallySelectedIndex = selectedLogLevel.ordinal
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { onHideModPanel() }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = null)
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .horizontalScroll(horzScrollState)
                ) {
                    SelectionContainer {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth(),
                            state = lazyListState
                        ) {
                            items(log) {
                                Text(
                                    text = it,
                                    softWrap = false,
                                    fontFamily = SmolTheme.fireCodeFont,
                                    fontSize = 14.sp,
                                    color = when (it.trim().firstOrNull()?.lowercase()) {
                                        "v" -> MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
                                        "d" -> MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                                        "i" -> MaterialTheme.colors.onSurface
                                        "w" -> MaterialTheme.colors.error.copy(alpha = ContentAlpha.high)
                                        "e" -> MaterialTheme.colors.error
                                        else -> MaterialTheme.colors.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
                HorizontalScrollbar(
                    modifier = Modifier.align(Alignment.CenterHorizontally).height(8.dp).fillMaxWidth(),
                    adapter = ScrollbarAdapter(horzScrollState)
                )
            }
            VerticalScrollbar(
                modifier = Modifier.width(8.dp).align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = ScrollbarAdapter(lazyListState)
            )
        }
    }
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

private suspend fun reloadMods() {
    if (SL.access.areModsLoading.value) {
        Logger.info { "Skipping reload of mods as they are currently refreshing already." }
        return
    }
    try {
        coroutineScope {
            trace(onFinished = { _, millis -> Timber.i { "Finished reloading everything in ${millis}ms (this is not how long it took to reload just the mods)." } }) {
                Timber.d { "Reloading mods." }
                SL.access.reload()
                val mods = SL.access.mods.value ?: emptyList()

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
                        SL.vramChecker.vramUsage.value ?: SL.vramChecker.refreshVramUsage(
                            mods = mods
                        )
                    }
                ).awaitAll()
            }
        }
    } catch (e: Exception) {
        Logger.debug(e)
    }
}

@Composable
private fun AppState.settingsButton() {
    SmolButton(
        onClick = { router.push(Screen.Settings) },
        modifier = Modifier.padding(start = 16.dp)
    ) {
        Text("Settings")
    }
}

@Composable
private fun AppState.modBrowserButton() {
    SmolButton(
        onClick = { router.push(Screen.ModBrowser) },
        modifier = Modifier.padding(start = 16.dp)
    ) {
        Text("Mod Browser")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppState.refreshButton() {
    val refreshScope by remember { mutableStateOf(CoroutineScope(Job())) }

    SmolTooltipArea(
        tooltip = { SmolTooltipText(text = "Refresh modlist & VRAM impact") }
    ) {
        SmolButton(
            onClick = {
                refreshScope.launch(Dispatchers.Default) {
                    reloadMods()
                }
            },
            modifier = Modifier.padding(start = 16.dp)
        ) {
            Icon(
                painter = painterResource("refresh.svg"),
                contentDescription = "Refresh",
                tint = SmolTheme.dimmedIconColor()
            )
        }
    }
}

@Composable
private fun AppState.profilesButton() {
    SmolButton(
        onClick = { router.push(Screen.Profiles) },
        modifier = Modifier.padding(start = 16.dp)
    ) {
        Text("Profiles")
    }
}

@Composable
private fun AppState.launchButton() {
    SmolButton(
        onClick = {
            val gameLauncher = SL.appConfig.gamePath.toPathOrNull()?.resolve("starsector.exe")
            val commands = when (currentPlatform) {
                Platform.Windows -> arrayOf("cmd.exe", "/C")
                else -> arrayOf("open")
            }
            Logger.info { "Launching ${gameLauncher?.absolutePathString()} with working dir ${SL.appConfig.gamePath}." }
            Runtime.getRuntime()
                .exec(
                    arrayOf(*commands, gameLauncher?.absolutePathString() ?: "missing"),
                    null,
                    SL.appConfig.gamePath.toFileOrNull()
                )
        },
        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
        modifier = Modifier
            .padding(start = 16.dp)
            .border(
                8.dp,
                MaterialTheme.colors.primary.withBrightness(-35),
                shape = SmolTheme.smolFullyClippedButtonShape()
            ),
        shape = SmolTheme.smolFullyClippedButtonShape(),
        elevation = ButtonDefaults.elevation(defaultElevation = 4.dp, hoveredElevation = 8.dp, pressedElevation = 16.dp)
    ) {
        Text(text = "Launch", fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AppState.ramButton(modifier: Modifier = Modifier) {
    var showVmParamsMenu by remember { mutableStateOf(false) }
    SmolButton(
        onClick = { showVmParamsMenu = true },
        modifier = modifier.padding(start = 16.dp)
    ) {
        Text(text = "RAM")
    }
    vmParamsContextMenu(showVmParamsMenu) { showVmParamsMenu = it }
}

@Composable
private fun AppState.installModsButton(modifier: Modifier = Modifier) {
    SmolTooltipArea(
        tooltip = { SmolTooltipText(text = "Install mod(s)") }
    ) {
        SmolButton(
            onClick = {
                with(FileDialog(this.window, "Choose a file", FileDialog.LOAD)
                    .apply {
                        this.isMultipleMode = true
                        this.directory = SL.appConfig.lastFilePickerDirectory
                        this.isVisible = true
                    })
                {
                    SL.appConfig.lastFilePickerDirectory = this.directory

                    this.files
                        .map { it.toPath() }
                        .onEach { Logger.debug { "Chose file: $it" } }
                        .forEach {
                            GlobalScope.launch {
                                SL.access.installFromUnknownSource(inputFile = it, shouldCompressModFolder = true)
                            }
                        }
                }
            },
            modifier = modifier.padding(start = 16.dp)
        ) {
            Icon(
                painter = painterResource("plus.svg"),
                contentDescription = null,
                tint = SmolTheme.dimmedIconColor()
            )
        }
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