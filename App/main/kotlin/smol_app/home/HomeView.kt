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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import org.tinylog.Logger
import smol_access.SL
import smol_access.model.Mod
import smol_app.UI
import smol_app.cli.SmolCLI
import smol_app.composables.*
import smol_app.navigation.Screen
import smol_app.themes.SmolTheme
import smol_app.toolbar.*
import smol_app.util.filterModGrid
import smol_app.util.replaceAllUsingDifference
import utilities.IOLock
import utilities.equalsAny


@OptIn(
    ExperimentalCoroutinesApi::class, ExperimentalMaterialApi::class,
    ExperimentalComposeUiApi::class
)
@Composable
@Preview
fun AppState.homeView(
    modifier: Modifier = Modifier
) {
    val mods: SnapshotStateList<Mod> = remember { mutableStateListOf() }
    val shownMods: SnapshotStateList<Mod?> = mods.toMutableStateList()
    val isWriteLocked = IOLock.stateFlow.collectAsState()

    LaunchedEffect(Unit) {
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
                modBrowserButton()
                profilesButton()
                settingsButton()
                if (isWriteLocked.value) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(start = 16.dp)
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
                val validationResult = SL.access.validatePaths()

                if (validationResult.isSuccess) {
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
                        val errors = validationResult.failure?.flatMap { it.value }

                        if (errors?.any() == true) {
                            Text(text = errors.joinToString(separator = "\n\n") { "Error: $it" })
                        }
                        SmolButton(
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
                                    vmParamsManager = SL.UI.vmParamsManager,
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