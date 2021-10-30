package views

import APP_NAME
import AppState
import SL
import SmolAlertDialog
import SmolButton
import SmolSecondaryButton
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import business.Archives
import business.GameEnabledMods.Companion.ENABLED_MODS_FILENAME
import business.KWatchEvent
import business.asWatchChannel
import cli.SmolCLI
import com.arkivanov.decompose.push
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import model.Mod
import navigation.Screen
import org.tinylog.Logger
import util.*
import java.io.File

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
    rememberCoroutineScope().launch { reloadMods(mods) }

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

    var showConfirmMigrateDialog: Boolean by remember { mutableStateOf(false) }
    val composableScope = rememberCoroutineScope()
    Scaffold(topBar = {
        TopAppBar() {
            settingsButton()

            SmolButton(
                onClick = {
                    showConfirmMigrateDialog = true
                },
                modifier = Modifier.padding(start = 16.dp)
            ) {
                Text("Migrate")
            }

            SmolButton(
                onClick = {
                    composableScope.launch {
                        reloadMods(mods)
                    }
                },
                modifier = Modifier.padding(start = 16.dp)
            ) {
                Text("Refresh")
            }
        }
    }, content = {
        Box {
            if (SL.gamePath.isValidGamePath(SL.appConfig.gamePath ?: "")) {
                ModGridView(modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(bottom = 40.dp), mods = mods)
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

            if (showConfirmMigrateDialog) {
                SmolAlertDialog(
                    title = { Text("Warning") },
                    text = {
                        Text(
                            "Are you sure you want to migrate the Starsector mods folder to be managed by $APP_NAME?" +
                                    "\nThis will not affect the mods. It will save their current state to the Archives folder so they may be reinstalled cleanly in the future."
                        )
                    },
                    onDismissRequest = { showConfirmMigrateDialog = false },
                    confirmButton = {
                        SmolButton(onClick = {
                            composableScope.launch {
                                SL.archives.compressModsInFolder(SL.gamePath.getModsPath())
                            }
                            showConfirmMigrateDialog = false
                        }) { Text("Migrate...") }
                    },
                    dismissButton = {
                        SmolSecondaryButton(onClick = { showConfirmMigrateDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    },
        bottomBar = {
            BottomAppBar(
                modifier = Modifier.fillMaxWidth().height(50.dp)
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
                Column(Modifier.weight(1f)) {
                    Row {
                        var consoleText by remember { mutableStateOf("") }
                        TextField(
                            value = consoleText,
                            label = { Text("Console") },
                            maxLines = 1,
                            onValueChange = { newStr ->
                                consoleText = newStr
                            },
                            modifier = Modifier.onKeyEvent { event ->
                                return@onKeyEvent if (event.type == KeyEventType.KeyUp && (event.key.equalsAny(
                                        Key.Enter,
                                        Key.NumPadEnter
                                    ))
                                ) {
                                    kotlin.runCatching {
                                        SmolCLI(userManager = SL.userManager)
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
        }
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun watchDirsAndReloadOnChange(
    mods: SnapshotStateList<Mod>
) {
    val NSA: List<Flow<KWatchEvent>> = listOf(
        SL.access.getStagingPath()?.toFileOrNull()?.asWatchChannel() ?: emptyFlow(),
        SL.access.getStagingPath()?.toFileOrNull()?.resolve(Archives.ARCHIVE_MANIFEST_FILENAME) // Watch manifest.json
            ?.run { if (this.exists()) this.asWatchChannel() else emptyFlow() } ?: emptyFlow(),
        SL.gamePath.getModsPath().asWatchChannel(),
        SL.gamePath.getModsPath().resolve(ENABLED_MODS_FILENAME) // Watch enabled_mods.json
            .run { if (this.exists()) this.asWatchChannel() else emptyFlow() },
        SL.archives.getArchivesPath()?.toFileOrNull()?.asWatchChannel() ?: emptyFlow(),
        SL.manualReloadTrigger.trigger.map {
            KWatchEvent(
                File("Mod reload triggered manually: $it"),
                KWatchEvent.Kind.Modified,
                null
            )
        }
    )

    reloadMods(mods)
    Logger.info {
        "Started watching folders ${
            NSA.joinToString()
        }"
    }
    NSA.merge()
        .collectLatest {
            if (!IOLock.stateFlow.value) {
                if (it.kind == KWatchEvent.Kind.Initialized)
                    return@collectLatest
                // Short delay so that if a new file change comes in during this time,
                // this is canceled in favor of the new change. This should prevent
                // refreshing 500 times if 500 files are changed in a few millis.
                delay(500)
                Logger.info { "File change: $it" }
                reloadMods(mods)
            } else {
                Logger.info { "Skipping mod reload while IO locked." }
            }
        }
}

private suspend fun reloadMods(mods: SnapshotStateList<Mod>) {
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
            mods.clear()
            mods.addAll(freshMods)
            SL.archives.refreshManifest()
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