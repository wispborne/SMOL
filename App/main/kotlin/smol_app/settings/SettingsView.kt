package smol_app.settings

import AppState
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.mouseClickable
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
import smol_access.Constants
import smol_access.SL
import smol_access.business.JreEntry
import smol_app.composables.*
import smol_app.themes.SmolTheme
import smol_app.themes.SmolTheme.toColors
import smol_app.toolbar.*
import smol_app.util.openInDesktop
import smol_app.views.jre8DownloadButton
import smol_app.views.jreSwitcher
import smol_app.views.ramButton
import timber.ktx.Timber
import utilities.rootCause
import utilities.toPathOrNull
import java.io.File
import javax.swing.JFileChooser
import kotlin.random.Random

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
            Box(modifier) {
                Column(Modifier.padding(top = 16.dp, bottom = 16.dp)) {
                    var gamePath by remember { mutableStateOf(SL.appConfig.gamePath ?: "") }
                    var archivesPath by remember { mutableStateOf(SL.appConfig.archivesPath ?: "") }
                    var stagingPath by remember { mutableStateOf(SL.appConfig.stagingPath ?: "") }
                    val alertDialogMessage = remember { mutableStateOf<String?>(null) }

                    fun saveSettings(): Boolean {
                        val errors = kotlin.runCatching {
                            SL.access.validatePaths(
                                newGamePath = gamePath.toPathOrNull(),
                                newArchivesPath = archivesPath.toPathOrNull(),
                                newStagingPath = stagingPath.toPathOrNull()
                            )
                        }
                            .recover {
                                Timber.w(it)
                                listOfNotNull(it.message)
                            }
                            .getOrNull()

                        if (errors != null && errors.isNotEmpty()) {
                            alertDialogMessage.value = errors.joinToString(separator = "\n")
                            return false
                        } else {
                            SL.appConfig.gamePath = gamePath

                            kotlin.runCatching {
                                SL.archives.changePath(archivesPath)
                                SL.access.changeStagingPath(stagingPath)
                            }
                                .onFailure { ex ->
                                    alertDialogMessage.value =
                                        "${ex.rootCause()::class.simpleName}\n${ex.rootCause().message}"
                                    return false
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

                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
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
                                gamePath = gamePathSetting(gamePath, archivesPath, stagingPath)
                                archivesPath = archivesPathSetting(gamePath, archivesPath, stagingPath)
                                stagingPath = stagingPathSetting(gamePath, archivesPath, stagingPath)
                                themeDropdown(Modifier.padding(start = 16.dp, top = 24.dp))
                            }
                        }

                        item { Divider(modifier = Modifier.padding(top = 32.dp, bottom = 8.dp)) }

                        item {
                            Text(
                                text = "Game Settings",
                                modifier = Modifier.padding(bottom = 8.dp, top = 8.dp, start = 16.dp, end = 16.dp),
                                fontWeight = FontWeight.Bold,
                                fontFamily = SmolTheme.orbitronSpaceFont,
                                fontSize = 13.sp
                            )
                        }
                        item { ramButton(modifier = Modifier.padding(start = 8.dp, top = 16.dp)) }
                        item {
                            jreSwitcher(
                                modifier = Modifier.padding(start = 8.dp, top = 24.dp),
                                recomposer = recomposer,
                                jresFound = jresFound
                            )
                        }
                        if (true) {  //|| javasFound.none { it.version == 8 }) {
                            item {
                                jre8DownloadButton(
                                    modifier = Modifier.padding(start = 8.dp, top = 24.dp),
                                    jresFound = jresFound,
                                    recomposer = recomposer
                                )
                            }
                        }
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
private fun AppState.gamePathSetting(gamePath: String, archivesPath: String, stagingPath: String): String {
    var newGamePath by remember { mutableStateOf(gamePath) }
    var isGamePathError by remember {
        mutableStateOf(
            SL.access.validatePaths(newGamePath = newGamePath.toPathOrNull()).any()
        )
    }

    Row {
        SmolTextField(
            value = newGamePath,
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
            label = { Text("Game path") },
            isError = isGamePathError,
            singleLine = true,
            onValueChange = {
                newGamePath = it
                isGamePathError = kotlin.runCatching {
                    SL.access.validatePaths(
                        newGamePath = newGamePath.toPathOrNull(),
                        newArchivesPath = archivesPath.toPathOrNull(),
                        newStagingPath = stagingPath.toPathOrNull()
                    ).any()
                }
                    .onFailure { ex -> Timber.w(ex) }
                    .getOrElse { true }
            })
        SmolButton(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(start = 16.dp),
            onClick = {
                newGamePath =
                    pickFolder(initialPath = newGamePath.ifBlank { null }
                        ?: System.getProperty("user.home"),
                        window = window)
                        ?: newGamePath
            }) {
            Text("Open")
        }
    }
    if (isGamePathError) {
        Text("Invalid game path", color = MaterialTheme.colors.error)
    }

    return newGamePath
}

@Composable
private fun AppState.archivesPathSetting(gamePath: String, archivesPath: String, stagingPath: String): String {
    fun isValidArchivesPath(path: String) = !File(path).exists()
    var isArchivesPathError by remember { mutableStateOf(isValidArchivesPath(archivesPath)) }
    var archivesPathMutable by remember { mutableStateOf(archivesPath) }

    Row {
        SmolTextField(
            value = archivesPathMutable,
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
            label = { Text("Archive storage path") },
            isError = isArchivesPathError,
            singleLine = true,
            onValueChange = {
                archivesPathMutable = it
                isArchivesPathError = kotlin.runCatching {
                    SL.access.validatePaths(
                        newGamePath = gamePath.toPathOrNull(),
                        newArchivesPath = archivesPathMutable.toPathOrNull(),
                        newStagingPath = stagingPath.toPathOrNull()
                    ).any()
                }
                    .onFailure { ex -> Timber.w(ex) }
                    .getOrElse { true }
            })
        SmolButton(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(start = 16.dp),
            onClick = {
                archivesPathMutable =
                    pickFolder(initialPath = archivesPathMutable.ifBlank { null }
                        ?: System.getProperty("user.home"),
                        window = window)
                        ?: archivesPathMutable
            }) {
            Text("Open")
        }
    }
    if (isArchivesPathError) {
        Text("Invalid path", color = MaterialTheme.colors.error)
    }

    return archivesPathMutable
}

@Composable
private fun AppState.stagingPathSetting(gamePath: String, archivesPath: String, stagingPath: String): String {
    fun isValidArchivesPath(path: String) = !File(path).exists()
    var stagingPathMutable by remember { mutableStateOf(stagingPath) }
    val stagingPathError = remember { mutableStateOf<List<String>?>(null) }

    Row {
        SmolTextField(
            value = stagingPathMutable,
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
            label = { Text("Staging path") },
            isError = !stagingPathError.value.isNullOrEmpty(),
            singleLine = true,
            onValueChange = {
                stagingPathMutable = it
                stagingPathError.value = kotlin.runCatching {
                    SL.access.validatePaths(
                        newGamePath = gamePath.toPathOrNull(),
                        newArchivesPath = archivesPath.toPathOrNull(),
                        newStagingPath = stagingPathMutable.toPathOrNull()
                    )
                }
                    .onFailure { ex -> Timber.w(ex) }
                    .getOrNull()
            })
        SmolButton(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(start = 16.dp),
            onClick = {
                stagingPathMutable =
                    pickFolder(initialPath = stagingPathMutable.ifBlank { null }
                        ?: System.getProperty("user.home"),
                        window = window)
                        ?: stagingPathMutable
            }) {
            Text("Open")
        }
    }

    if (!stagingPathError.value.isNullOrEmpty()) {
        Text(
            "Invalid path: ${stagingPathError.value?.joinToString(separator = "\n")}",
            color = MaterialTheme.colors.error
        )
    }

    return stagingPathMutable
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppState.themeDropdown(modifier: Modifier = Modifier): String {
    var themeName by remember { mutableStateOf(SL.themeManager.activeTheme.value.first) }
    val themes = SL.themeManager.getThemes()
    val recomposeScope = currentRecomposeScope

    Column(modifier) {
        Text(text = "Theme")
        Row {
            SmolDropdownWithButton(
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                items = themes
                    .map { entry ->
                        val colors = entry.value.toColors()
                        SmolDropdownMenuItemCustom(
                            backgroundColor = colors.surface,
                            onClick = {
                                themeName = entry.key
                                SL.themeManager.setActiveTheme(entry.key)
                            },
                            customItemContent = { isMenuButton ->
                                val height = 24.dp
                                Text(
                                    text = entry.key,
                                    modifier = Modifier
                                        .run { if (!isMenuButton) this.weight(1f) else this }
                                        .align(Alignment.CenterVertically),
                                    fontWeight = FontWeight.Bold,
                                    color = colors.onSurface
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(start = 16.dp)
                                        .width(height * 3)
                                        .height(height)
                                        .background(color = colors.primary)
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .width(height)
                                        .height(height)
                                        .background(color = colors.secondary)
                                )
                            }
                        )
                    },
                initiallySelectedIndex = themes.keys.indexOf(themeName).coerceAtLeast(0),
                canSelectItems = true
            )
            SmolLinkText(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .align(Alignment.CenterVertically)
                    .mouseClickable { Constants.THEME_CONFIG_PATH.openInDesktop() }, text = "Edit"
            )
            SmolLinkText(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .align(Alignment.CenterVertically)
                    .mouseClickable {
                        SL.themeManager.reloadThemes()
                        recomposeScope.invalidate()
                    },
                text = "Refresh"
            )
        }
    }

    return themeName
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