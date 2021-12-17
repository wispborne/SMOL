package smol_app.views

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.mouseClickable
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.pop
import smol_access.Constants
import smol_access.SL
import smol_app.AppState
import smol_app.composables.*
import smol_app.themes.SmolTheme.toColors
import smol_app.util.openInDesktop
import utilities.rootCause
import java.io.File
import javax.swing.JFileChooser

@OptIn(
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class
)
@Composable
@Preview
fun AppState.settingsView(
    modifier: Modifier = Modifier
) {
    Scaffold(topBar = {
        TopAppBar {
            SmolButton(onClick = router::pop, modifier = Modifier.padding(start = 16.dp)) {
                Text("Back")
            }
        }
    }) {
        Box(modifier) {
            Column(Modifier.padding(16.dp)) {
                var gamePath by remember { mutableStateOf(SL.appConfig.gamePath ?: "") }
                var archivesPath by remember { mutableStateOf(SL.appConfig.archivesPath ?: "") }
                var stagingPath by remember { mutableStateOf(SL.appConfig.stagingPath ?: "") }
                var alertDialogMessage: String? by remember { mutableStateOf(null) }

                fun save(): Boolean {
                    SL.appConfig.gamePath = gamePath

                    kotlin.runCatching {
                        SL.archives.changePath(archivesPath)
                        SL.access.changeStagingPath(stagingPath)
                    }
                        .onFailure { ex ->
                            alertDialogMessage =
                                "${ex.rootCause()::class.simpleName}\n${ex.rootCause().message}"
                            return false
                        }

                    return true
                }

                if (alertDialogMessage != null) {
                    SmolAlertDialog(
                        title = { Text("Error") },
                        text = { alertDialogMessage?.let { Text(alertDialogMessage!!) } },
                        onDismissRequest = { alertDialogMessage = null },
                        confirmButton = { Button(onClick = { alertDialogMessage = null }) { Text("Ok") } }
                    )
                }

                LazyColumn(Modifier.weight(1f)) {
                    item {
                        Column {
                            gamePath = gamePathSetting(gamePath)
                            archivesPath = archivesPathSetting(archivesPath)
                            stagingPath = stagingPathSetting(stagingPath)
                            themeDropdown(Modifier.padding(start = 16.dp, top = 16.dp))
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    SmolButton(modifier = Modifier.padding(end = 16.dp), onClick = {
                        if (save()) {
                            router.pop()
                        }
                    }) { Text("Ok") }
                    SmolSecondaryButton(
                        modifier = Modifier.padding(end = 16.dp),
                        onClick = { router.pop() }) { Text("Cancel") }
                    SmolSecondaryButton(onClick = { save() }) { Text("Apply") }
                }
            }
        }
    }
}


@Composable
private fun AppState.gamePathSetting(gamePath: String): String {
    var newGamePath by remember { mutableStateOf(gamePath) }
    var isGamePathError by remember { mutableStateOf(!SL.gamePath.isValidGamePath(newGamePath)) }

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
                isGamePathError = !SL.gamePath.isValidGamePath(it)
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
private fun AppState.archivesPathSetting(archivesPath: String): String {
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
                isArchivesPathError = isValidArchivesPath(it)
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
private fun AppState.stagingPathSetting(stagingPath: String): String {
    fun isValidArchivesPath(path: String) = !File(path).exists()
    var stagingPathMutable by remember { mutableStateOf(stagingPath) }
    var isStagingPathError = false

    Row {
        SmolTextField(
            value = stagingPathMutable,
            modifier = Modifier
                .weight(1f)
                .align(Alignment.CenterVertically),
            label = { Text("Staging path") },
            isError = isStagingPathError,
            singleLine = true,
            onValueChange = {
                stagingPathMutable = it
//                isStagingPathError = isValidArchivesPath(it)
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
    if (isStagingPathError) {
        Text("Invalid path", color = MaterialTheme.colors.error)
    }

    return stagingPathMutable
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppState.themeDropdown(modifier: Modifier = Modifier): String {
    var themeName by remember { mutableStateOf(SL.themeManager.activeTheme.value.first) }
    val themes = SL.themeManager.getThemes()
    val recomposeScope = currentRecomposeScope

    Row(modifier) {
        Text(modifier = Modifier.align(Alignment.CenterVertically), text = "Theme")
        SmolDropdownWithButton(
            modifier = Modifier.padding(start = 16.dp).align(Alignment.CenterVertically),
            items = themes
                .map {
                    SmolDropdownMenuItem(
                        text = it.key,
                        backgroundColor = it.value.toColors().surface,
                        contentColor = it.value.toColors().onSurface,
                        onClick = {
                            themeName = it.key
                            SL.themeManager.setActiveTheme(it.key)
                        }
                    )
                },
            initiallySelectedIndex = themes.keys.indexOf(themeName).coerceAtLeast(0)
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
                .mouseClickable { recomposeScope.invalidate() },
            text = "Refresh"
        )
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