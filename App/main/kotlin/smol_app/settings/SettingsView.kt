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
import com.arkivanov.decompose.pop
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import smol_access.SL
import smol_access.business.JreEntry
import smol_access.config.SettingsPath
import smol_app.composables.*
import smol_app.themes.SmolTheme
import smol_app.toolbar.*
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
                    .padding(top = 16.dp, bottom = 16.dp)
            ) {
                Column {
                    var gamePath by remember { mutableStateOf(SL.gamePathManager.path.value?.pathString) }
                    var archivesPath by remember { mutableStateOf(SL.appConfig.archivesPath ?: "") }
                    var stagingPath by remember { mutableStateOf(SL.appConfig.stagingPath ?: "") }
                    val alertDialogMessage = remember { mutableStateOf<String?>(null) }
                    val settingsPathErrors = remember {
                        mutableStateOf(
                            SL.access.validatePaths(
                                newGamePath = gamePath.toPathOrNull(),
                                newArchivesPath = archivesPath.toPathOrNull(),
                                newStagingPath = stagingPath.toPathOrNull()
                            ).failure
                        )
                    }

                    fun saveSettings(): Boolean {
                        val errors = kotlin.runCatching {
                            SL.access.validatePaths(
                                newGamePath = gamePath.toPathOrNull(),
                                newArchivesPath = archivesPath.toPathOrNull(),
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
                                SL.archives.changePath(archivesPath)
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
                                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
                                    gamePath = gamePathSetting(
                                        gamePath = gamePath ?: "",
                                        archivesPath = archivesPath,
                                        stagingPath = stagingPath,
                                        settingsPathErrors = settingsPathErrors
                                    )
                                    archivesPath =
                                        archivesPathSetting(
                                            gamePath = gamePath ?: "",
                                            archivesPath = archivesPath,
                                            stagingPath = stagingPath,
                                            settingsPathErrors = settingsPathErrors
                                        )
                                    stagingPath =
                                        stagingPathSetting(
                                            gamePath = gamePath ?: "",
                                            archivesPath = archivesPath,
                                            stagingPath = stagingPath,
                                            settingsPathErrors = settingsPathErrors
                                        )
                                    themeDropdown(Modifier.padding(start = 16.dp, top = 24.dp))
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
                                if (true) {  //|| javasFound.none { it.version == 8 }) {
                                    item {
                                        jre8DownloadButton(
                                            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                                            jresFound = jresFound,
                                            recomposer = recomposer
                                        )
                                    }
                                }
                            }
                        }

                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(listState),
                            modifier = Modifier.width(8.dp).fillMaxHeight()
                        )
                    }

                    // Confirm buttons
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = SmolTheme.bottomBarHeight, end = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        SmolButton(
                            modifier = Modifier.padding(end = 16.dp),
                            onClick = {
                                if (saveSettings()) {
                                    router.pop()
                                }
                            }) { Text("Ok") }
                        SmolSecondaryButton(
                            modifier = Modifier.padding(end = 16.dp),
                            onClick = { router.pop() }) { Text("Cancel") }
                        SmolSecondaryButton(onClick = { saveSettings() }) { Text("Apply") }
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
    archivesPath: String,
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
                        newArchivesPath = archivesPath.toPathOrNull(),
                        newStagingPath = stagingPath.toPathOrNull()
                    ).failure
                }
                    .onFailure { ex -> Timber.w(ex) }
                    .getOrElse { emptyMap() }
            })
        SmolButton(
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
private fun AppState.archivesPathSetting(
    gamePath: String,
    archivesPath: String,
    stagingPath: String,
    settingsPathErrors: MutableState<Map<SettingsPath, List<String>>?>
): String {
    val errors = settingsPathErrors.value?.get(SettingsPath.Archives)
    var archivesPathMutable by remember { mutableStateOf(archivesPath) }

    Row {
        SmolTextField(
            value = archivesPathMutable,
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
            label = { Text("Archive storage folder") },
            isError = errors?.any() ?: false,
            singleLine = true,
            maxLines = 1,
            onValueChange = {
                archivesPathMutable = it
                settingsPathErrors.value = kotlin.runCatching {
                    SL.access.validatePaths(
                        newGamePath = gamePath.toPathOrNull(),
                        newArchivesPath = it.toPathOrNull(),
                        newStagingPath = stagingPath.toPathOrNull()
                    ).failure
                }
                    .onFailure { ex -> Timber.w(ex) }
                    .getOrElse { emptyMap() }
            })
        SmolButton(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(start = 16.dp),
            onClick = {
                archivesPathMutable =
                    pickFolder(
                        initialPath = archivesPathMutable.ifBlank { null }
                            ?: "",
                        window = window)
                        ?: archivesPathMutable
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

    return archivesPathMutable
}

@Composable
private fun AppState.stagingPathSetting(
    gamePath: String,
    archivesPath: String,
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
                        newArchivesPath = archivesPath.toPathOrNull(),
                        newStagingPath = it.toPathOrNull()
                    ).failure
                }
                    .onFailure { ex -> Timber.w(ex) }
                    .getOrElse { emptyMap() }
            })
        SmolButton(
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