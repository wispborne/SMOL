package views

import AppState
import SL
import SmolButton
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.push
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge
import model.Mod
import navigation.Screen
import org.tinylog.Logger
import util.KWatchEvent
import util.asWatchChannel
import util.toFileOrNull

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
@Preview
fun AppState.homeView(
    modifier: Modifier = Modifier
) {
    val mods = remember { mutableStateListOf<Mod>() }
    val scope = rememberCoroutineScope()

    scope.launch(Dispatchers.Default) {
        val stagingWatcher = SL.staging.getStagingPath()?.toFileOrNull()?.asWatchChannel(scope = this)
        val gameModsWatcher = SL.gamePath.getModsPath().asWatchChannel(scope = this)
        val archivesWatcher =
            SL.archives.getArchivesPath()?.toFileOrNull()?.asWatchChannel(scope = this)

        merge(stagingWatcher ?: emptyFlow(), gameModsWatcher, archivesWatcher ?: emptyFlow())
            .also { NSA ->
                NSA
                    .collectLatest {
                        if (it.kind == KWatchEvent.Kind.Initialized)
                            return@collectLatest
                        // Short delay so that if a new file change comes in during this time,
                        // this is canceled in favor of the new change. This should prevent
                        // refreshing 500 times if 500 files are changed in a few millis.
                        delay(100)
                        Logger.debug { "File change: $it" }
                        mods.clear()
                        mods.addAll(SL.modLoader.getMods())
                    }
            }
    }

    Scaffold(topBar = {
        TopAppBar() {
            settingsButton()
            migrateButton()

            val composableScope = rememberCoroutineScope()
            SmolButton(
                onClick = {
                    composableScope.launch {
                        mods.clear()
                        mods.addAll(SL.modLoader.getMods())
                    }
                },
                modifier = Modifier.padding(start = 16.dp)
            ) {
                Text("Refresh")
            }
        }
    }, content = {
        if (SL.gamePath.isValidGamePath(SL.appConfig.gamePath ?: "")) {
            mods.clear()
            mods.addAll(SL.modLoader.getMods())
            ModGridView(
                mods,
                Modifier.padding(16.dp)
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

@Composable
private fun AppState.settingsButton() {
    SmolButton(
        onClick = { router.push(Screen.Settings) },
        modifier = Modifier.padding(start = 16.dp)
    ) {
        Text("Settings")
    }
}

@OptIn(InternalCoroutinesApi::class)
@Composable
private fun AppState.migrateButton() {
    val composableScope = rememberCoroutineScope()
    SmolButton(
        onClick = {
            composableScope.launch {
                SL.archives.archiveModsInFolder(SL.gamePath.getModsPath())
                    .collect { mod ->
//                        Logger.info { "Archived: $mod" }
                    }
            }
        },
        modifier = Modifier.padding(start = 16.dp)
    ) {
        Text("Migrate")
    }
}