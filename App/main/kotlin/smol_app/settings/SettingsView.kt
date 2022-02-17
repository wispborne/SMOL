package smol_app.settings

import AppScope
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
import androidx.compose.ui.res.painterResource
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
fun AppScope.settingsView(
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
                    val alertDialogMessage = remember { mutableStateOf<String?>(null) }
                    val scope = rememberCoroutineScope()
                    val settingsPathErrors = remember {
                        mutableStateOf(
                            SL.access.validatePaths(
                                newGamePath = gamePath.toPathOrNull()
                            ).failure
                        )
                    }

                    fun saveSettings(): Boolean {
                        val errors = kotlin.runCatching {
                            SL.access.validatePaths(
                                newGamePath = gamePath.toPathOrNull()
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
                                    modifier = Modifier.padding(bottom = 8.dp, start = 16.dp, end = 16.dp, top = 8.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = SmolTheme.orbitronSpaceFont,
                                    fontSize = 13.sp
                                )
                            }

                            item {
                                Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)) {
                                    Text(
                                        text = "Locations",
                                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                                        style = SettingsView.settingLabelStyle()
                                    )
                                    gamePath = gamePathSetting(
                                        gamePath = gamePath ?: "",
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
                                        SmolButton(
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
private fun AppScope.gamePathSetting(
    gamePath: String,
    settingsPathErrors: MutableState<Map<SettingsPath, List<String>>?>
): String {
    var newGamePath by remember { mutableStateOf(gamePath) }
    val errors = settingsPathErrors.value?.get(SettingsPath.Game)

    Row {
        SmolTextField(
            value = newGamePath,
            modifier = Modifier
                .padding(start = 16.dp)
                .widthIn(max = 700.dp)
                .fillMaxWidth()
                .align(Alignment.CenterVertically),
            label = { Text("Starsector folder") },
            isError = errors?.any() ?: false,
            singleLine = true,
            maxLines = 1,
            onValueChange = {
                newGamePath = it
                settingsPathErrors.value = kotlin.runCatching {
                    SL.access.validatePaths(
                        newGamePath = it.toPathOrNull()
                    ).failure
                }
                    .onFailure { ex -> Timber.w(ex) }
                    .getOrElse { emptyMap() }
            })
        SmolIconButton(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(start = 16.dp, end = 16.dp),
            onClick = {
                newGamePath =
                    pickFolder(initialPath = newGamePath.ifBlank { null }
                        ?: "",
                        window = window)
                        ?: newGamePath
            }) {
            Icon(
                painter = painterResource("icon-open-folder.svg"),
                tint = MaterialTheme.colors.onBackground,
                contentDescription = null
            )
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