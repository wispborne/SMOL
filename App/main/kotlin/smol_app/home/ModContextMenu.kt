package smol_app.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tinylog.Logger
import smol_access.SL
import smol_access.model.Mod
import smol_app.util.getModThreadId
import smol_app.util.openModThread
import java.awt.Desktop
import kotlin.io.path.exists

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ModContextMenu(
    showContextMenu: Boolean,
    onShowContextMenuChange: (Boolean) -> Unit,
    mod: Mod,
    modInDebugDialog: Mod?,
    onModInDebugDialogChanged: (Mod?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    CursorDropdownMenu(
        expanded = showContextMenu,
        onDismissRequest = { onShowContextMenuChange(false) }) {
        val modsFolder = (mod.findFirstEnabled
            ?: mod.findFirstDisabled)?.modsFolderInfo?.folder
        if (modsFolder?.exists() == true) {
            DropdownMenuItem(onClick = {
                kotlin.runCatching {
                    modsFolder.also {
                        Desktop.getDesktop().open(it.toFile())
                    }
                }
                    .onFailure { Logger.warn(it) { "Error trying to open file browser for $mod." } }
                onShowContextMenuChange(false)
            }) {
                Text("Open Folder")
            }
        }

        val archiveFolder = (mod.findFirstEnabled
            ?: mod.findFirstDisabled)?.archiveInfo?.folder
        if (archiveFolder?.exists() == true) {
            DropdownMenuItem(onClick = {
                kotlin.runCatching {
                    archiveFolder.also {
                        Desktop.getDesktop().open(it.toFile())
                    }
                }
                    .onFailure { Logger.warn(it) { "Error trying to open file browser for $mod." } }
                onShowContextMenuChange(false)
            }) {
                Text("Open Archive")
            }
        }

        val modThreadId = mod.getModThreadId()
        if (modThreadId != null) {
            DropdownMenuItem(
                onClick = {
                    modThreadId.openModThread()
                    onShowContextMenuChange(false)
                },
                modifier = Modifier.width(200.dp)
            ) {
                Image(
                    painter = painterResource("open-in-new.svg"),
                    colorFilter = ColorFilter.tint(MaterialTheme.colors.onSurface),
                    modifier = Modifier.padding(end = 8.dp),
                    contentDescription = null
                )
                Text(
                    text = "Forum Page",
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
            Text("Check VRAM")
        }

        DropdownMenuItem(onClick = {
            onModInDebugDialogChanged(mod)
            onShowContextMenuChange(false)
        }) {
            Text("Debug Info")
        }
    }
}