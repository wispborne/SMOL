package smol_app.settings

import AppState
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import smol_access.SL
import smol_access.business.JreEntry
import smol_access.config.SettingsPath
import smol_app.UI
import smol_app.composables.*
import smol_app.themes.SmolTheme
import smol_app.toolbar.*
import smol_app.updater.UpdateSmolToast
import timber.ktx.Timber
import utilities.exists
import utilities.rootCause
import utilities.toPathOrNull
import java.io.File
import javax.swing.JFileChooser
import kotlin.io.path.pathString
import kotlin.random.Random

object SettingsView {
    @Composable
    fun settingLabelStyle() = MaterialTheme.typography.body1
}

@OptIn(
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class
)
@Composable
@Preview
fun AppState.settingsView(
    modifier: Modifier = Modifier
) {
    val showLogPanel = remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(modifier = Modifier.height(SmolTheme.topBarHeight)) {
            launchButton()
            installModsButton()
            Spacer(Modifier.width(16.dp))
            homeButton()
            modBrowserButton()
            profilesButton()
            screenTitle(text = "Settings")
        }
    },
        content = {
            Row(
                modifier
                    .padding(bottom = SmolTheme.bottomBarHeight - 16.dp)
            ) {
                Column {
                    var gamePath by remember { mutableStateOf(SL.gamePathManager.path.value?.pathString) }
                    var stagingPath by remember { mutableStateOf(SL.appConfig.stagingPath ?: "") }
                    val alertDialogMessage = remember { mutableStateOf<String?>(null) }
                    val scope = rememberCoroutineScope()
                    val settingsPathErrors = remember {
                        mutableStateOf(
                            SL.access.validatePaths(
                                newGamePath = gamePath.toPathOrNull(),
                                newStagingPath = stagingPath.toPathOrNull()
                            ).failure
                        )
                    }

                    fun saveSettings(): Boolean {
                        val errors = kotlin.runCatching {
                            SL.access.validatePaths(
                                newGamePath = gamePath.toPathOrNull(),
                                newStagingPath = stagingPath.toPathOrNull()
                            ).failure
                        }
                            .recover {
                                Timber.w(it)
                                mapOf(SettingsPath.Game to listOf(it.message))
                            }
                            .getOrNull()
                            ?.get(SettingsPath.Game)

                        if (errors != null && errors.any()) {
                            alertDialogMessage.value = errors.joinToString(separator = "\n")
                            return false
                        } else {
                            SL.gamePathManager.set(gamePath!!)

                            kotlin.runCatching {
                                SL.access.changeStagingPath(stagingPath)
                            }
                                .onFailure { ex ->
                                    alertDialogMessage.value =
                                        "${ex.rootCause()::class.simpleName}\n${ex.rootCause().message}"
                                    return false
                                }

                            GlobalScope.launch {
                                SL.access.reload()
                            }
                        }

                        return true
                    }

                    if (alertDialogMessage.value != null) {
                        SmolAlertDialog(
                            title = { Text("Error", style = SmolTheme.alertDialogTitle()) },
                            text = {
                                alertDialogMessage.value?.let {
                                    Text(
                                        alertDialogMessage.value!!,
                                        style = SmolTheme.alertDialogBody()
                                    )
                                }
                            },
                            onDismissRequest = { alertDialogMessage.value = null },
                            confirmButton = { Button(onClick = { alertDialogMessage.value = null }) { Text("Ok") } }
                        )
                    }

                    val recomposer = currentRecomposeScope
                    val jresFound = remember { SnapshotStateList<JreEntry>() }
                    LaunchedEffect(Random.nextLong()) {
                        jresFound.clear()
                        jresFound.addAll(
                            SL.jreManager.findJREs()
                                .sortedBy { it.versionString })
                    }

                    Row(
                        modifier = Modifier.weight(1f),
                    ) {
                        val listState = rememberLazyListState()

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            state = listState
                        ) {
                            item {
                                Text(
                                    text = "Application Settings",
                                    modifier = Modifier.padding(bottom = 8.dp, start = 16.dp, end = 16.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = SmolTheme.orbitronSpaceFont,
                                    fontSize = 13.sp
                                )
                            }

                            item {
                                Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)) {
                                    Text(
                                        text = "Locations",
                                        modifier = Modifier.padding(start = 16.dp),
                                        style = SettingsView.settingLabelStyle()
                                    )
                                    gamePath = gamePathSetting(
                                        gamePath = gamePath ?: "",
                                        stagingPath = stagingPath,
                                        settingsPathErrors = settingsPathErrors
                                    )
                                    stagingPath =
                                        stagingPathSetting(
                                            gamePath = gamePath ?: "",
                                            stagingPath = stagingPath,
                                            settingsPathErrors = settingsPathErrors
                                        )

                                    // Confirm buttons
                                    Row(
                                        Modifier.padding(start = 16.dp, end = 16.dp)
                                    ) {
                                        SmolButton(onClick = { saveSettings() }) { Text("Apply") }
                                    }

                                    themeDropdown(Modifier.padding(start = 16.dp, top = 24.dp))

                                    Column(modifier = Modifier.padding(start = 16.dp, top = 24.dp)) {
                                        Text(text = "Update", style = SettingsView.settingLabelStyle())
                                        SmolSecondaryButton(
                                            onClick = {
                                                scope.launch {
                                                    val remoteConfig = SL.UI.updater.getRemoteConfig()

                                                    if (remoteConfig == null) {
                                                        Timber.w { "Unable to fetch remote config, aborting update check." }
                                                    } else {
                                                        UpdateSmolToast().createIfNeeded(
                                                            updateConfig = remoteConfig,
                                                            toasterState = SL.UI.toaster,
                                                            updater = SL.UI.updater
                                                        )
                                                    }
                                                }
                                            }
                                        ) {
                                            Text("Check for Update")
                                        }
                                        Text(
                                            text = "If an update is found, a notification will be displayed.",
                                            style = MaterialTheme.typography.caption
                                        )
                                    }
                                }
                            }

                            item { Divider(modifier = Modifier.padding(top = 32.dp, bottom = 8.dp)) }

                            if (SL.gamePathManager.path.value.exists()) {
                                item {
                                    Text(
                                        text = "Game Settings",
                                        modifier = Modifier.padding(
                                            bottom = 8.dp,
                                            top = 8.dp,
                                            start = 16.dp,
                                            end = 16.dp
                                        ),
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = SmolTheme.orbitronSpaceFont,
                                        fontSize = 13.sp
                                    )
                                }
                                item { ramButton(modifier = Modifier.padding(start = 16.dp, top = 16.dp)) }
                                item {
                                    jreSwitcher(
                                        modifier = Modifier.padding(start = 16.dp, top = 24.dp),
                                        recomposer = recomposer,
                                        jresFound = jresFound
                                    )
                                }
                                item {
                                    jre8DownloadButton(
                                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                                        jresFound = jresFound,
                                        recomposer = recomposer
                                    )
                                }
                            }
                        }

                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(listState),
                            modifier = Modifier.width(8.dp).fillMaxHeight()
                        )
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
                logButtonAndErrorDisplay(showLogPanel = showLogPanel)
            }
        }
    )
}

@Composable
private fun AppState.gamePathSetting(
    gamePath: String,
    stagingPath: String,
    settingsPathErrors: MutableState<Map<SettingsPath, List<String>>?>
): String {
    var newGamePath by remember { mutableStateOf(gamePath) }
    val errors = settingsPathErrors.value?.get(SettingsPath.Game)

    Row {
        SmolTextField(
            value = newGamePath,
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
            label = { Text("Starsector folder") },
            isError = errors?.any() ?: false,
            singleLine = true,
            maxLines = 1,
            onValueChange = {
                newGamePath = it
                settingsPathErrors.value = kotlin.runCatching {
                    SL.access.validatePaths(
                        newGamePath = it.toPathOrNull(),
                        newStagingPath = stagingPath.toPathOrNull()
                    ).failure
                }
                    .onFailure { ex -> Timber.w(ex) }
                    .getOrElse { emptyMap() }
            })
        SmolSecondaryButton(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(start = 16.dp),
            onClick = {
                newGamePath =
                    pickFolder(initialPath = newGamePath.ifBlank { null }
                        ?: "",
                        window = window)
                        ?: newGamePath
            }) {
            Text("Open")
        }
    }
    if (!errors.isNullOrEmpty()) {
        Text(
            text = errors.joinToString(separator = "\n"),
            color = MaterialTheme.colors.error,
            modifier = Modifier.padding(start = 16.dp)
        )
    }

    return newGamePath
}

@Composable
private fun AppState.stagingPathSetting(
    gamePath: String,
    stagingPath: String,
    settingsPathErrors: MutableState<Map<SettingsPath, List<String>>?>
): String {
    var stagingPathMutable by remember { mutableStateOf(stagingPath) }
    val errors = settingsPathErrors.value?.get(SettingsPath.Staging)

    Row {
        SmolTextField(
            value = stagingPathMutable,
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
            label = { Text("Staging folder") },
            isError = errors?.any() ?: false,
            singleLine = true,
            maxLines = 1,
            onValueChange = {
                stagingPathMutable = it
                settingsPathErrors.value = kotlin.runCatching {
                    SL.access.validatePaths(
                        newGamePath = gamePath.toPathOrNull(),
                        newStagingPath = it.toPathOrNull()
                    ).failure
                }
                    .onFailure { ex -> Timber.w(ex) }
                    .getOrElse { emptyMap() }
            })
        SmolSecondaryButton(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(start = 16.dp),
            onClick = {
                stagingPathMutable =
                    pickFolder(initialPath = stagingPathMutable.ifBlank { null }
                        ?: "",
                        window = window)
                        ?: stagingPathMutable
            }) {
            Text("Open")
        }
    }

    if (errors?.any() == true) {
        Text(
            text = errors.joinToString(separator = "\n"),
            color = MaterialTheme.colors.error,
            modifier = Modifier.padding(start = 16.dp)
        )
    }

    return stagingPathMutable
}

private fun pickFolder(initialPath: String, window: ComposeWindow): String? {
    JFileChooser().apply {
        currentDirectory =
            File(initialPath)
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY

        return when (showOpenDialog(window)) {
            JFileChooser.APPROVE_OPTION -> this.selectedFile.absolutePath
            else -> null
        }
    }
}