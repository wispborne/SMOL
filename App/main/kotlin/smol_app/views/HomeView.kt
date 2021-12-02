package smol_app.views

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
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
import smol_access.config.Platform
import smol_access.model.Mod
import smol_access.util.IOLock
import smol_app.AppState
import smol_app.cli.SmolCLI
import smol_app.components.*
import smol_app.navigation.Screen
import smol_app.themes.SmolTheme
import smol_app.themes.SmolTheme.darken
import smol_app.themes.SmolTheme.highlight
import smol_app.themes.SmolTheme.lighten
import smol_app.util.currentPlatform
import smol_app.util.filterMods
import smol_app.util.replaceAllUsingDifference
import smol_app.util.vmParamsManager
import utilities.equalsAny
import utilities.toFileOrNull
import utilities.toPathOrNull
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
    val mods: SnapshotStateList<Mod> =
        remember { mutableStateListOf(elements = SL.access.mods.value?.toTypedArray() ?: emptyArray()) }
    val shownMods = remember { mutableStateListOf<Mod?>(elements = mods.toTypedArray()) }
    val onRefreshingMods = { refreshing: Boolean -> isRefreshingMods = refreshing }
    rememberCoroutineScope { Dispatchers.Default }.launch {
        reloadMods()
    }

    rememberCoroutineScope { Dispatchers.Default }.launch {
        SL.access.mods.collectLatest { freshMods ->
            if (freshMods != null) {
                withContext(Dispatchers.Main) {
                    mods.replaceAllUsingDifference(freshMods, doesOrderMatter = true)
                }
            }
        }
    }

    rememberCoroutineScope().launch {
        watchDirsAndReloadOnChange()
    }

//    var showConfirmMigrateDialog: Boolean by remember { mutableStateOf(false) }
    val composableScope = rememberCoroutineScope { Dispatchers.Default }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                modifier = Modifier.height(72.dp)
            ) {
                launchButton()
                ramButton()

                installModsButton(Modifier.padding(start = 16.dp))
                refreshButton(composableScope)
                profilesButton()
                settingsButton()
                modBrowserButton()
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
                                filterMods(query, mods).ifEmpty { listOf(null) },
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

//            if (showConfirmMigrateDialog) {
//                smol_app.SmolAlertDialog(
//                    title = { Text("Warning") },
//                    text = {
//                        Text(
//                            "Are you sure you want to migrate the Starsector mods folder to be managed by $SMOL_Access.APP_NAME?" +
//                                    "\nThis will not affect the mods. It will save their current state to the Archives folder so they may be reinstalled cleanly in the future."
//                        )
//                    },
//                    onDismissRequest = { showConfirmMigrateDialog = false },
//                    confirmButton = {
//                        smol_app.SmolButton(onClick = {
//                            composableScope.launch {
//                                SL.archives.compressModsInFolder(SL.gamePath.getModsPath())
//                            }
//                            showConfirmMigrateDialog = false
//                        }) { Text("Migrate...") }
//                    },
//                    dismissButton = {
//                        smol_app.SmolSecondaryButton(onClick = { showConfirmMigrateDialog = false }) {
//                            Text("Cancel")
//                        }
//                    }
//                )
//            }
            }
        },
        bottomBar = {
            BottomAppBar(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Row {
                        var status by remember { mutableStateOf("") }
                        rememberCoroutineScope().launch {
                            SL.archives.archiveMovementStatusFlow.collectLatest { status = it }
                        }
                        Text(text = status, modifier = Modifier.align(Alignment.CenterVertically).padding(8.dp))
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun watchDirsAndReloadOnChange() {
    withContext(Dispatchers.IO) {
        val NSA: List<Flow<KWatchEvent?>> = listOf(
            SL.access.getStagingPath()?.toPathOrNull()?.asWatchChannel() ?: emptyFlow(),
            SL.access.getStagingPath()?.toPathOrNull()
                ?.resolve(Archives.ARCHIVE_MANIFEST_FILENAME) // Watch manifest.json
                ?.run { if (this.exists()) this.asWatchChannel() else emptyFlow() } ?: emptyFlow(),
            SL.gamePath.getModsPath().asWatchChannel(),
            SL.gamePath.getModsPath().resolve(ENABLED_MODS_FILENAME) // Watch enabled_mods.json
                .run { if (this.exists()) this.asWatchChannel() else emptyFlow() },
            SL.archives.getArchivesPath()?.toPathOrNull()?.asWatchChannel() ?: emptyFlow(),
        )
        Logger.info {
            "Started watching folders ${
                NSA.joinToString()
            }"
        }
        NSA
            .plus(SL.manualReloadTrigger.trigger.map { null })
            .merge()
            .distinctUntilChanged()
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
            Logger.info { "Reloading mods." }
            SL.access.reload()
            val manifest = async { SL.archives.refreshManifest() }
            val vramCheckerAsync = async {
                SL.vramChecker.vramUsage.value ?: SL.vramChecker.refreshVramUsage(
                    mods = SL.access.mods.value ?: emptyList()
                )
            }

            manifest.await()
            vramCheckerAsync.await()
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
private fun AppState.refreshButton(
    composableScope: CoroutineScope
) {
    TooltipArea(
        tooltip = { SmolTooltipText(text = "Refresh modlist & VRAM impact") }
    ) {
        SmolButton(
            onClick = {
                composableScope.launch {
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
            .border(6.dp, MaterialTheme.colors.primary.highlight(), shape = SmolTheme.smolFullyClippedButtonShape()),
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
    TooltipArea(
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