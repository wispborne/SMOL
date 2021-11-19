package views

import AppState
import SL
import SmolButton
import SmolOutlinedTextField
import SmolTheme
import SmolTooltipText
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import business.Archives
import business.GameEnabledMods.Companion.ENABLED_MODS_FILENAME
import business.KWatchEvent
import business.asWatchChannel
import cli.SmolCLI
import com.arkivanov.decompose.push
import config.Platform
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import model.Mod
import navigation.Screen
import org.tinylog.Logger
import util.IOLock
import util.currentPlatform
import util.filterMods
import util.vmParamsManager
import utilities.equalsAny
import utilities.toFileOrNull
import utilities.toPathOrNull
import java.awt.FileDialog
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists


private var isRefreshingMods = false

@OptIn(
    ExperimentalCoroutinesApi::class, androidx.compose.material.ExperimentalMaterialApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)
@Composable
@Preview
fun AppState.homeView(
    modifier: Modifier = Modifier
) {
    val mods = remember { mutableStateListOf<Mod>() }
    val shownMods = remember { mutableStateListOf<Mod?>(*mods.toTypedArray()) }
    rememberCoroutineScope().launch { reloadMods(mods, forceRefresh = false) }

    var job = Job()

    (rememberCoroutineScope() + job).launch {
        watchDirsAndReloadOnChange(mods)
    }

//    rememberCoroutineScope().launch(Dispatchers.Default) {
//        Logger.debug { "Starting to watch IOLock." }
//        IOLock.stateFlow
//            .collect { isLocked ->
//                if (isLocked) {
//                    Logger.debug { "Canceling watch of folders." }
//                    job.cancel()
//                } else {
//                    job.cancel()
//                    job = Job()
//                    (this + job).launch {
//                        watchDirsAndReloadOnChange(mods)
//                    }
//                }
//            }
//    }

//    var showConfirmMigrateDialog: Boolean by remember { mutableStateOf(false) }
    val composableScope = rememberCoroutineScope()
    Scaffold(topBar = {
        TopAppBar(
            modifier = Modifier.height(72.dp)
        ) {
            var showVmParamsMenu by remember { mutableStateOf(false) }
            launchButton()
            SmolButton(
                onClick = { showVmParamsMenu = true },
                modifier = Modifier.padding(start = 16.dp),
                shape = SmolTheme.smolFullyClippedButtonShape()
            ) {
                Text(text = "RAM")
            }
            vmParamsContextMenu(showVmParamsMenu) { showVmParamsMenu = it }

//            SmolButton(
//                onClick = {
//                    showConfirmMigrateDialog = true
//                },
//                modifier = Modifier.padding(start = 16.dp)
//            ) {
//                Text("Migrate")
//            }

            SmolButton(
                onClick = {
                    composableScope.launch {
                        reloadMods(mods, forceRefresh = true)
                    }
                },
                modifier = Modifier.padding(start = 16.dp)
            ) {
                Text("Refresh")
            }

            profilesButton()
            settingsButton()
            installModsButton()
            modBrowserButton()
            Spacer(Modifier.weight(1f))
            consoleTextField(
                modifier = Modifier.padding(end = 16.dp)
                    .align(Alignment.CenterVertically)
            )
            filterTextField(
                Modifier
                    .padding(end = 16.dp)
                    .width(250.dp)
                    .align(Alignment.CenterVertically)
            ) { query ->
                shownMods.clear()
                if (query.isBlank()) {
                    shownMods.addAll(mods)
                } else {
                    shownMods.addAll(filterMods(query, mods).ifEmpty { listOf(null) })
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

//            if (showConfirmMigrateDialog) {
//                SmolAlertDialog(
//                    title = { Text("Warning") },
//                    text = {
//                        Text(
//                            "Are you sure you want to migrate the Starsector mods folder to be managed by $APP_NAME?" +
//                                    "\nThis will not affect the mods. It will save their current state to the Archives folder so they may be reinstalled cleanly in the future."
//                        )
//                    },
//                    onDismissRequest = { showConfirmMigrateDialog = false },
//                    confirmButton = {
//                        SmolButton(onClick = {
//                            composableScope.launch {
//                                SL.archives.compressModsInFolder(SL.gamePath.getModsPath())
//                            }
//                            showConfirmMigrateDialog = false
//                        }) { Text("Migrate...") }
//                    },
//                    dismissButton = {
//                        SmolSecondaryButton(onClick = { showConfirmMigrateDialog = false }) {
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
private suspend fun watchDirsAndReloadOnChange(
    mods: SnapshotStateList<Mod>
) {
    val NSA: List<Flow<KWatchEvent?>> = listOf(
        SL.access.getStagingPath()?.toPathOrNull()?.asWatchChannel() ?: emptyFlow(),
        SL.access.getStagingPath()?.toPathOrNull()?.resolve(Archives.ARCHIVE_MANIFEST_FILENAME) // Watch manifest.json
            ?.run { if (this.exists()) this.asWatchChannel() else emptyFlow() } ?: emptyFlow(),
        SL.gamePath.getModsPath().asWatchChannel(),
        SL.gamePath.getModsPath().resolve(ENABLED_MODS_FILENAME) // Watch enabled_mods.json
            .run { if (this.exists()) this.asWatchChannel() else emptyFlow() },
        SL.archives.getArchivesPath()?.toPathOrNull()?.asWatchChannel() ?: emptyFlow(),
    )

    reloadMods(mods, forceRefresh = false)
    Logger.info {
        "Started watching folders ${
            NSA.joinToString()
        }"
    }
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
                delay(500)
                Logger.info { "File change: $it" }
                reloadMods(mods, forceRefresh = false)
            } else {
                Logger.info { "Skipping mod reload while IO locked." }
            }
        }
}

private suspend fun reloadMods(mods: SnapshotStateList<Mod>, forceRefresh: Boolean) {
    if (isRefreshingMods) {
        Logger.info { "Skipping reload of mods as they are currently refreshing already." }
        return
    }
    try {
        coroutineScope {
            Logger.info { "Reloading mods." }
            isRefreshingMods = true
            val freshMods =
                withContext(Dispatchers.Default) { SL.access.getMods(noCache = true) }
            // TODO replace using differ rather than clear and addAll
            mods.clear()
            mods.addAll(freshMods)
            val manifest = async { SL.archives.refreshManifest() }
            val vramCheckerAsync = async { SL.vramChecker.getVramUsage(forceRefresh = forceRefresh) }

            manifest.await()
            vramCheckerAsync.await()
        }
    } catch (e: Exception) {
        Logger.debug(e)
    } finally {
        isRefreshingMods = false
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
        modifier = Modifier
            .padding(start = 16.dp)
            .border(4.dp, SmolTheme.highlight(), shape = SmolTheme.smolFullyClippedButtonShape()),
        shape = SmolTheme.smolFullyClippedButtonShape()
    ) {
        Text(text = "Launch")
    }
}

@Composable
private fun AppState.installModsButton() {
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
            modifier = Modifier.padding(start = 16.dp),
            shape = SmolTheme.smolFullyClippedButtonShape()
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
                                    vmParamsManager = SL.vmParamsManager,
                                    modLoader = SL.modLoader,
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

@Composable
private fun AppState.filterTextField(
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    SmolOutlinedTextField(
        modifier = modifier,
        label = { Text(text = "Filter") },
        value = value,
        onValueChange = {
            value = it
            onValueChange(it)
        },
        singleLine = true,
        maxLines = 1
    )
}