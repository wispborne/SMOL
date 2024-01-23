/*
 * This file is distributed under the GPLv3. An informal description follows:
 * - Anyone can copy, modify and distribute this software as long as the other points are followed.
 * - You must include the license and copyright notice with each and every distribution.
 * - You may this software for commercial purposes.
 * - If you modify it, you must indicate changes made to the code.
 * - Any modifications of this code base MUST be distributed with the same license, GPLv3.
 * - This software is provided without warranty.
 * - The software author or license can not be held liable for any damages inflicted by the software.
 * The full license is available from <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

@file:OptIn(ExperimentalFoundationApi::class)
@file:Suppress("FunctionName")

package smol.app.settings

import AppScope
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import smol.access.Constants
import smol.access.SL
import smol.access.business.JreEntry
import smol.access.config.SettingsPath
import smol.app.composables.*
import smol.app.navigation.Screen
import smol.app.themes.SmolTheme
import smol.app.themes.SmolTheme.GlowingBorderColor
import smol.app.themes.SmolTheme.hyperlink
import smol.app.toolbar.toolbar
import smol.timber.ktx.Timber
import smol.utilities.exists
import smol.utilities.toPathOrNull
import java.io.File
import java.nio.file.Path
import javax.swing.JFileChooser
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString

object SettingsView {
    @Composable
    fun settingLabelStyle() = MaterialTheme.typography.body1
}

@OptIn(
    ExperimentalFoundationApi::class
)
@Composable
@Preview
fun AppScope.settingsView(
    modifier: Modifier = Modifier,
    settingsPath: SettingsPath
) {
    val showLogPanel = remember { mutableStateOf(false) }
    val userProfile = SL.userManager.activeProfile.collectAsState().value

    Scaffold(topBar = {
        SmolTopAppBar(modifier = Modifier.height(SmolTheme.topBarHeight)) {
            toolbar(router.state.value.activeChild.instance as Screen)
        }
    },
        content = {
            Row(
                modifier
                    .padding(bottom = SmolTheme.bottomBarHeight - 16.dp)
            ) {
                Column {
                    var gamePath by remember { mutableStateOf(SL.gamePathManager.path.value?.pathString) }
                    var modBackupPath by remember { mutableStateOf(SL.appConfig.modBackupPath) }
                    val scope = rememberCoroutineScope()
                    val jresFound = remember { SnapshotStateList<JreEntry>() }

                    LaunchedEffect(Unit) {
                        refreshJres(jresFound)
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
                                    Row {
                                        gamePath = FolderPathTextFieldSetting(
                                            gamePath = gamePath ?: "",
                                            label = "Starsector folder",
                                            validator = SL.access::validateStarsectorFolderPath
                                        )

                                        // Confirm/Apply button
                                        var initialGamePath by remember { mutableStateOf(gamePath) }
                                        val wasChanged = (gamePath != initialGamePath
                                                && gamePath?.toPathOrNull()?.exists() == true)
                                        SmolButton(
                                            enabled = wasChanged,
                                            modifier = Modifier.let {
                                                if (wasChanged) it.border(
                                                    1.dp,
                                                    MaterialTheme.colors.secondary
                                                ) else it
                                            },
                                            onClick = {
                                                val errors = saveGamePath(
                                                    gamePath,
                                                    jresFound,
                                                )
                                                if (errors.any()) showSimpleErrorDialog(
                                                    "Error",
                                                    errors.joinToString(separator = "\n")
                                                )
                                                else initialGamePath = gamePath
                                            }) {
                                            Text("Apply")
                                        }
                                    }

                                    if (SL.appConfig.isAlphaModBackupFeatureEnabled) {
                                        Column {
                                            var areModBackupsEnabled by remember { mutableStateOf(SL.appConfig.areAutoModBackupsEnabled) }
                                            val isModBackupPathValid =
                                                SL.access.validateBackupFolderPath(modBackupPath.toPathOrNull())
                                                    .isEmpty()

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(top = 16.dp).scale(0.9f),
                                            ) {
                                                SmolTooltipArea(tooltip = {
                                                    SmolTooltipText("Creates a 7z backup in the specified folder whenever you update or remove a mod.")
                                                }) {
                                                    SmolCheckboxWithText(
                                                        checked = areModBackupsEnabled,
                                                        onCheckedChange = { checked ->
                                                            areModBackupsEnabled = checked
                                                            SL.appConfig.areAutoModBackupsEnabled = checked
                                                        }
                                                    ) { modifier ->
                                                        Text(
                                                            text = "Enable automatic mod backups",
                                                            modifier = modifier,
                                                            style = MaterialTheme.typography.body2
                                                        )
                                                    }
                                                    SmolTooltipArea(tooltip = {
                                                        SmolTooltipText("Starts a backup of all of your mods.")
                                                    }) {
                                                        SmolClickableText(
                                                            text = "Back Up All Now",
                                                            color = MaterialTheme.colors.hyperlink,
                                                            modifier = Modifier
                                                                .align(Alignment.CenterVertically)
                                                                .padding(start = 24.dp),
                                                            textDecoration = TextDecoration.Underline,
                                                            isEnabled = isModBackupPathValid,
                                                            onClick = {
                                                                GlobalScope.launch {
                                                                    SL.access.modsFlow.value?.mods.orEmpty()
                                                                        .flatMap { it.variants }
                                                                        .forEach { SL.access.backupMod(it) }
                                                                }
                                                            },
                                                        )
                                                    }
                                                }
                                            }

                                            Row(Modifier.offset(y = (-8).dp)) {
                                                modBackupPath = FolderPathTextFieldSetting(
                                                    gamePath = modBackupPath ?: "",
                                                    label = "Mod Backup folder",
                                                    validator = SL.access::validateBackupFolderPath,
                                                    isEnabled = areModBackupsEnabled
                                                )

                                                // Confirm/Apply button
                                                var initialBackupPath by remember { mutableStateOf(modBackupPath) }
                                                val wasChanged = (initialBackupPath != modBackupPath)

                                                SmolButton(
                                                    enabled = areModBackupsEnabled && wasChanged,
                                                    border = if (areModBackupsEnabled && wasChanged) BorderStroke(
                                                        3.dp,
                                                        GlowingBorderColor()
                                                    ) else null,
                                                    onClick = {
                                                        val errors = saveBackupPath(modBackupPath)
                                                        if (errors.any()) showSimpleErrorDialog(
                                                            "Error",
                                                            errors.joinToString(separator = "\n")
                                                        )
                                                        else {
                                                            val initialBackupPathSaved = initialBackupPath
                                                            initialBackupPath = modBackupPath

                                                            if (initialBackupPathSaved != modBackupPath
                                                                && initialBackupPathSaved.toPathOrNull()
                                                                    ?.exists() == true
                                                                && modBackupPath.toPathOrNull()?.exists() == true
                                                            ) {
                                                                val backupFiles = initialBackupPathSaved.toPathOrNull()
                                                                    ?.listDirectoryEntries("*.${Constants.backupFileExtension}")
                                                                    ?: emptyList()

                                                                if (backupFiles.isNotEmpty())
                                                                    ShowMoveBackupFilesDialog(
                                                                        backupFiles,
                                                                        initialBackupPathSaved!!,
                                                                        modBackupPath!!
                                                                    )
                                                            }
                                                        }
                                                    }) {
                                                    Text("Apply")
                                                }
                                            }
                                            //                                            val isBackingUp =
                                            //                                            val modState =
                                            //                                                SL.access.modModificationState.collectAsState().value[mod.id] ?: smol.access.Access.ModModificationState.Ready
                                        }
                                    }

                                    themeDropdown(Modifier.padding(start = 16.dp, top = 24.dp))

                                    updateSection(
                                        scope = scope,
                                        modifier = Modifier.padding(start = 16.dp, top = 24.dp)
                                    )

                                    rendererSettingSection(
                                        scope = scope,
                                        modifier = Modifier.padding(start = 16.dp, top = 24.dp)
                                    )
                                }
                            }

                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 16.dp, top = 16.dp)
                                ) {
                                    val isChecked = userProfile.useOrbitronNameFont!!
                                    SmolCheckboxWithText(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            SL.userManager.updateUserProfile {
                                                it.copy(useOrbitronNameFont = checked)
                                            }
                                        }
                                    ) { modifier ->
                                        Text(
                                            text = buildAnnotatedString {
                                                append("Use ")
                                                append(
                                                    AnnotatedString(
                                                        "Starsector font",
                                                        SpanStyle(fontFamily = SmolTheme.orbitronSpaceFont)
                                                    )
                                                )
                                                append(" for mod names.")
                                            },
                                            modifier = modifier
                                        )
                                    }
                                }
                            }

                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 16.dp)
                                ) {
                                    val isChecked = userProfile.warnAboutOneClickUpdates ?: true
                                    SmolCheckboxWithText(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            SL.userManager.updateUserProfile {
                                                it.copy(warnAboutOneClickUpdates = checked)
                                            }
                                        }
                                    ) { modifier ->
                                        Text(
                                            text = "Warn about one-click updates.",
                                            modifier = modifier
                                        )
                                    }
                                }
                            }

                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 16.dp)
                                ) {
                                    var isChecked by remember { mutableStateOf(SL.appConfig.isUpdateOnCloseEnabled) }
                                    SmolCheckboxWithText(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            isChecked = checked
                                            SL.appConfig.isUpdateOnCloseEnabled = checked
                                        }
                                    ) { modifier ->
                                        Text(
                                            text = "Update SMOL in background on exit.",
                                            modifier = modifier
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
                                item { changeRamSection(modifier = Modifier.padding(start = 16.dp, top = 16.dp)) }
                                item {
                                    jreSwitcher(
                                        modifier = Modifier.padding(start = 16.dp, top = 24.dp),
                                        jresFound = jresFound,
                                        refreshJres = { refreshJres(jresFound) }
                                    )
                                }
                                item {
                                    jre8DownloadButton(
                                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                                        jresFound = jresFound,
                                        refreshJres = { refreshJres(jresFound) }
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
            SmolBottomAppBar(
                modifier = Modifier.fillMaxWidth(),
                showLogPanel = showLogPanel
            )
        }
    )
}

private fun AppScope.ShowMoveBackupFilesDialog(
    backupFiles: List<Path>,
    source: String,
    destination: String
) {
    alertDialogSetter.invoke {
        SmolAlertDialog(
            title = { Text("Move Backups") },
            text = {
                Text("Do you want SMOL to move your existing ${backupFiles.count()} backups to the new location?")
            },
            onDismissRequest = {
                alertDialogSetter.invoke(null)
            },
            confirmButton = {
                Button(onClick = {
                    alertDialogSetter.invoke {
                        var isDone by remember { mutableStateOf(false) }
                        SmolAlertDialog(
                            title = { Text("Moving Backups") },
                            text = {
                                val pathsMoved = remember { MutableStateFlow(emptyList<Path>()) }

                                LaunchedEffect(0) {
                                    SL.backupManager.moveFolder(
                                        source.toPathOrNull()!!,
                                        destination.toPathOrNull()!!,
                                        pathsMoved
                                    )
                                    isDone = true
                                }
                                Column {
                                    Text("Moving ${backupFiles.count()} backups to the new location...")
                                    Spacer(Modifier.height(16.dp))
                                    CircularProgressIndicator(
                                        progress = pathsMoved.value.count() / backupFiles.count().toFloat()
                                    )

                                    if (isDone) {
                                        Spacer(Modifier.height(16.dp))
                                        Text("Files moved!")
                                        if (pathsMoved.value.count() != backupFiles.count()) {
                                            Spacer(Modifier.height(16.dp))
                                            Text("Some files could not be moved. Check the logs for more info.")
                                        }
                                    }
                                }
                            },
                            onDismissRequest = { if (isDone) alertDialogSetter.invoke(null) },
                            confirmButton = { if (isDone) Button(onClick = { alertDialogSetter.invoke(null) }) { Text("Close") } },
                        )
                    }
                }) { Text("Move Backups") }
            },
            dismissButton = {
                Button(onClick = {
                    alertDialogSetter.invoke(null)
                }) { Text("No") }
            }
        )
    }
}

private fun AppScope.saveGamePath(
    gamePath: String?,
    jresFound: SnapshotStateList<JreEntry>
): List<String> {
    val errors = runCatching {
        SL.access.validateStarsectorFolderPath(
            newGamePath = gamePath.toPathOrNull(),
        )
    }
        .recover {
            Timber.w(it)
            listOf(it.message)
        }
        .getOrNull()
        ?.filterNotNull()

    if (errors != null && errors.any()) {
        return errors
    } else {
        SL.gamePathManager.set(gamePath!!)

        GlobalScope.launch {
            refreshJres(jresFound)
            SL.access.reload()
        }
    }

    return emptyList()
}


private fun AppScope.saveBackupPath(
    backupPath: String?
): List<String> {
    val errors = runCatching {
        SL.access.validateBackupFolderPath(backupPath.toPathOrNull())
    }
        .recover {
            Timber.w(it)
            listOf(it.message)
        }
        .getOrNull()
        ?.filterNotNull()

    if (errors != null && errors.any()) {
        return errors
    } else {
        SL.appConfig.modBackupPath = backupPath!!
    }

    return emptyList()
}

private suspend fun refreshJres(jresFound: SnapshotStateList<JreEntry>) {
    jresFound.clear()
    jresFound.addAll(
        SL.jreManager.findJREs()
            .sortedBy { it.versionString })
}

@Composable
private fun AppScope.FolderPathTextFieldSetting(
    gamePath: String,
    label: String,
    isEnabled: Boolean = true,
    validator: (Path?) -> List<String>,
): String {
    var newGamePath by remember { mutableStateOf(gamePath) }
    val errors = validator(newGamePath.toPathOrNull())

    Column {
        Row {
            // Text field
            SmolTextField(
                value = newGamePath,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .widthIn(max = 700.dp)
                    .fillMaxWidth()
                    .align(Alignment.CenterVertically),
                label = { Text(label) },
                isError = errors.any(),
                singleLine = true,
                maxLines = 1,
                enabled = isEnabled,
                onValueChange = { newGamePath = it })

            // Open folder button
            SmolIconButton(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(start = 16.dp, end = 16.dp),
                enabled = isEnabled,
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