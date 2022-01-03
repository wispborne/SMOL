package smol_app.home

import AppState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.push
import kotlinx.coroutines.*
import org.tinylog.Logger
import smol_access.SL
import smol_access.model.Mod
import smol_app.composables.SmolDropdownMenuItemTemplate
import smol_app.navigation.Screen
import smol_app.util.getModThreadId
import smol_app.util.getModThreadUrl
import smol_app.util.openModThread
import smol_app.util.uiEnabled
import utilities.parallelMap
import java.awt.Desktop
import kotlin.io.path.exists

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AppState.ModContextMenu(
    showContextMenu: Boolean,
    onShowContextMenuChange: (Boolean) -> Unit,
    mod: Mod,
    modInDebugDialog: MutableState<Mod?>,
    checkedMods: SnapshotStateList<Mod>
) {
    val coroutineScope = rememberCoroutineScope()
    CursorDropdownMenu(
        expanded = showContextMenu,
        onDismissRequest = { onShowContextMenuChange.invoke(false) }) {
        if (checkedMods.any()) {
            modGridBulkActionMenuItems(
                checkedRows = checkedMods
            )
                .map {
                    DropdownMenuItem(onClick = {
                        onShowContextMenuChange.invoke(false)
                        it.onClick.invoke()
                    }) { Text(text = it.text) }
                }
        } else {
            modGridSingleModMenu(
                mod = mod,
                onShowContextMenuChange = onShowContextMenuChange,
                coroutineScope = coroutineScope,
                modInDebugDialog = modInDebugDialog
            )
        }
    }
}

@Composable
private fun AppState.modGridSingleModMenu(
    mod: Mod,
    onShowContextMenuChange: (Boolean) -> Unit,
    coroutineScope: CoroutineScope,
    modInDebugDialog: MutableState<Mod?>
) {
    val modsFolder = (mod.findFirstEnabled
        ?: mod.findFirstDisabled)?.modsFolderInfo?.folder
    if (modsFolder?.exists() == true) {
        DropdownMenuItem(onClick = {
            runCatching {
                modsFolder.also {
                    Desktop.getDesktop().open(it.toFile())
                }
            }
                .onFailure { Logger.warn(it) { "Error trying to open file browser for $mod." } }
            onShowContextMenuChange(false)
        }) {
            Image(
                painter = painterResource("icon-folder.svg"),
                colorFilter = ColorFilter.tint(MaterialTheme.colors.onSurface),
                modifier = Modifier.padding(end = 12.dp),
                contentDescription = null
            )
            Text("Open Folder")
        }
    }

    val archiveFolder = (mod.findFirstEnabled
        ?: mod.findFirstDisabled)?.archiveInfo?.folder
    if (archiveFolder?.exists() == true) {
        DropdownMenuItem(onClick = {
            runCatching {
                archiveFolder.also {
                    Desktop.getDesktop().open(it.toFile())
                }
            }
                .onFailure { Logger.warn(it) { "Error trying to open file browser for $mod." } }
            onShowContextMenuChange(false)
        }) {
            Image(
                painter = painterResource("icon-archive.svg"),
                colorFilter = ColorFilter.tint(MaterialTheme.colors.onSurface),
                modifier = Modifier.padding(end = 12.dp),
                contentDescription = null
            )
            Text("Open Archive")
        }
    }

    val modThreadId = mod.getModThreadId()
    if (modThreadId != null) {
        DropdownMenuItem(
            onClick = {
                router.push(Screen.ModBrowser(modThreadId.getModThreadUrl()))
                onShowContextMenuChange(false)
            },
            modifier = Modifier.width(200.dp)
        ) {
            Image(
                painter = painterResource("web.svg"),
                colorFilter = ColorFilter.tint(MaterialTheme.colors.onSurface),
                modifier = Modifier.padding(end = 12.dp),
                contentDescription = null
            )
            Text(
                text = "Forum Page (SMOL)",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }

        DropdownMenuItem(
            onClick = {
                modThreadId.openModThread()
                onShowContextMenuChange(false)
            },
            modifier = Modifier.width(200.dp)
        ) {
            Image(
                painter = painterResource("web.svg"),
                colorFilter = ColorFilter.tint(MaterialTheme.colors.onSurface),
                modifier = Modifier.padding(end = 12.dp),
                contentDescription = null
            )
            Text(
                text = "Forum Page (Browser)",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }

    DropdownMenuItem(onClick = {
        coroutineScope.launch {
            withContext(Dispatchers.Default) {
                SL.vramChecker.refreshVramUsage(mods = listOf(mod))
            }
        }
        onShowContextMenuChange(false)
    }) {
        Image(
            painter = painterResource("icon-vram-impact.svg"),
            colorFilter = ColorFilter.tint(MaterialTheme.colors.onSurface),
            modifier = Modifier.padding(end = 12.dp),
            contentDescription = null
        )
        Text("Check VRAM Impact")
    }

    DropdownMenuItem(onClick = {
        modInDebugDialog.value = mod
        onShowContextMenuChange(false)
    }) {
        Image(
            painter = painterResource("icon-bug.svg"),
            colorFilter = ColorFilter.tint(MaterialTheme.colors.onSurface),
            modifier = Modifier.padding(end = 12.dp),
            contentDescription = null
        )
        Text("Debug Info")
    }
}

@Composable
fun modGridBulkActionMenuItems(checkedRows: SnapshotStateList<Mod>) =
    buildList {
        if (checkedRows.any { it.uiEnabled }) {
            add(SmolDropdownMenuItemTemplate(
                text = "Disable All",
                onClick = {
                    GlobalScope.launch(Dispatchers.IO) {
                        checkedRows
                            .parallelMap { SL.access.disableMod(it) }
                    }
                }
            ))
        }
        add(SmolDropdownMenuItemTemplate(
            text = "Check VRAM",
            onClick = {
                GlobalScope.launch(Dispatchers.IO) {
                    checkedRows
                        .also { mods -> SL.vramChecker.refreshVramUsage(mods) }
                }
            }
        ))
    }