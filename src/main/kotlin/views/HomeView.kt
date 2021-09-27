package views

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
import androidx.compose.ui.unit.dp
import business.KWatchEvent
import business.asWatchChannel
import com.arkivanov.decompose.push
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge
import model.Mod
import navigation.Screen
import org.tinylog.Logger
import util.*

private var isRefreshingMods = false

@OptIn(ExperimentalCoroutinesApi::class, androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
@Preview
fun AppState.homeView(
    modifier: Modifier = Modifier
) {
    val mods = remember { mutableStateListOf<Mod>() }
    reloadMods(mods)

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
                ModGridView(
                    mods
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

            if (showConfirmMigrateDialog) {
                SmolAlertDialog(
                    title = { Text("Warning") },
                    text = {
                        Text(
                            "Are you sure you want to migrate the Starsector mods folder to be managed by $appName?" +
                                    "\nThis will not affect the mods. It will save their current state to the Archives folder so they may be reinstalled cleanly in the future."
                        )
                    },
                    onDismissRequest = { showConfirmMigrateDialog = false },
                    confirmButton = {
                        SmolButton(onClick = {
                            composableScope.launch {
                                SL.archives.archiveModsInFolder(SL.gamePath.getModsPath())
                                    .collect { mod ->
                                        // TODO
                                    }
                            }
                            showConfirmMigrateDialog = false
                        }) { Text("Migrate") }
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
                modifier = Modifier.fillMaxWidth().height(40.dp)
            ) {
                var status by remember { mutableStateOf("") }
                rememberCoroutineScope().launch {
                    SL.archives.archiveMovementStatusFlow.collectLatest { status = it }
                }
                Text(text = status, modifier = Modifier.align(Alignment.CenterVertically).padding(8.dp))
            }
        }
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun watchDirsAndReloadOnChange(
    mods: SnapshotStateList<Mod>
) {
    val stagingWatcher = SL.staging.getStagingPath()?.toFileOrNull()?.asWatchChannel()
    val gameModsWatcher = SL.gamePath.getModsPath().asWatchChannel()
    val archivesWatcher =
        SL.archives.getArchivesPath()?.toFileOrNull()?.asWatchChannel()

    reloadMods(mods)
    Logger.debug {
        "Started watching folders ${
            listOf(
                stagingWatcher?.file?.path,
                gameModsWatcher.file.path,
                archivesWatcher?.file?.path
            ).joinToString()
        }"
    }
    merge(stagingWatcher ?: emptyFlow(), gameModsWatcher, archivesWatcher ?: emptyFlow())
        .also { NSA ->
            NSA
                .collectLatest {
                    if (!IOLock.stateFlow.value) {
                        if (it.kind == KWatchEvent.Kind.Initialized)
                            return@collectLatest
                        // Short delay so that if a new file change comes in during this time,
                        // this is canceled in favor of the new change. This should prevent
                        // refreshing 500 times if 500 files are changed in a few millis.
                        delay(100)
                        Logger.debug { "File change: $it" }
                        reloadMods(mods)
                    } else {
                        Logger.debug { "Skipping mod reload while IO locked." }
                    }
                }
        }
}

private fun reloadMods(mods: SnapshotStateList<Mod>) {
    if (isRefreshingMods) {
        Logger.debug { "Skipping reload of mods as they are currently refreshing already." }
        return
    }
    try {
        Logger.debug { "Reloading mods." }
        isRefreshingMods = true
        mods.clear()
        mods.addAll(SL.modLoader.getMods())
    } catch (e: Exception) {
        Logger.trace(e)
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