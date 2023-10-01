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

package smol.app.home

import AppScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.replaceCurrent
import kotlinx.coroutines.*
import org.tinylog.Logger
import smol.access.Constants
import smol.access.SL
import smol.access.model.Mod
import smol.access.model.ModVariant
import smol.app.composables.SmolAlertDialog
import smol.app.composables.SmolButton
import smol.app.composables.SmolDropdownMenuItemTemplate
import smol.app.composables.SmolSecondaryButton
import smol.app.navigation.Screen
import smol.app.themes.SmolTheme
import smol.app.util.*
import smol.utilities.parallelMap
import java.awt.Desktop
import kotlin.io.path.exists

@Composable
fun AppScope.ModContextMenu(
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
private fun AppScope.modGridSingleModMenu(
    mod: Mod,
    onShowContextMenuChange: (Boolean) -> Unit,
    coroutineScope: CoroutineScope,
    modInDebugDialog: MutableState<Mod?>
) {
    val modsFolder = (mod.findFirstEnabledOrHighestVersion)?.modsFolderInfo?.folder
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
                modifier = Modifier.padding(end = 12.dp).size(24.dp),
                contentDescription = null
            )
            Text("Open folder")
        }
    }

    val modThreadId = mod.getModThreadId()

    if (Constants.isModBrowserEnabled()) {
        if (modThreadId != null) {
            DropdownMenuItem(
                onClick = {
                    router.replaceCurrent(Screen.ModBrowser(modThreadId.getModThreadUrl()))
                    onShowContextMenuChange(false)
                },
            ) {
                Image(
                    painter = painterResource("icon-open-in-app.svg"),
                    colorFilter = ColorFilter.tint(MaterialTheme.colors.onSurface),
                    modifier = Modifier.padding(end = 12.dp).size(24.dp),
                    contentDescription = null
                )
                Text(
                    text = "View website in SMOL",
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }

    if (modThreadId != null) {
        DropdownMenuItem(
            onClick = {
                modThreadId.openModThread()
                onShowContextMenuChange(false)
            },
        ) {
            Image(
                painter = painterResource("icon-web.svg"),
                colorFilter = ColorFilter.tint(MaterialTheme.colors.onSurface),
                modifier = Modifier.padding(end = 12.dp).size(24.dp),
                contentDescription = null
            )
            Text(
                text = "View website in browser",
                maxLines = 1,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }

    val nexusId = mod.getNexusId()
    if (nexusId != null) {
        DropdownMenuItem(
            onClick = {
                nexusId.getNexusModsUrl().openAsUriInBrowser()
                onShowContextMenuChange(false)
            },
        ) {
            Image(
                painter = painterResource("icon-nexus.svg"),
                colorFilter = ColorFilter.tint(MaterialTheme.colors.onSurface),
                modifier = Modifier.padding(end = 12.dp).size(24.dp),
                contentDescription = null
            )
            Text(
                text = "View NexusMods in browser",
                maxLines = 1,
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
            modifier = Modifier.padding(end = 12.dp).size(24.dp),
            contentDescription = null
        )
        Text("Check VRAM impact")
    }

    DropdownMenuItem(onClick = {
        this@modGridSingleModMenu.alertDialogSetter.invoke {
            BackUpModVariantDialog(
                variants = mod.variants,
                onDismiss = this@modGridSingleModMenu::dismissAlertDialog
            )
        }
        onShowContextMenuChange(false)
    }) {
        Image(
            painter = painterResource("icon-archive-create.svg"),
            colorFilter = ColorFilter.tint(MaterialTheme.colors.onSurface),
            modifier = Modifier.padding(end = 12.dp).size(24.dp),
            contentDescription = null
        )
        Text("Create backup")
    }

    DropdownMenuItem(onClick = {
        this@modGridSingleModMenu.alertDialogSetter.invoke {
            DeleteModVariantDialog(
                variantsToConfirmDeletionOf = mod.variants,
                onDismiss = this@modGridSingleModMenu::dismissAlertDialog
            )
        }
        onShowContextMenuChange(false)
    }) {
        Image(
            painter = painterResource("icon-trash.svg"),
            colorFilter = ColorFilter.tint(MaterialTheme.colors.onSurface),
            modifier = Modifier.padding(end = 12.dp).size(24.dp),
            contentDescription = null
        )
        Text("Delete files")
    }

    DropdownMenuItem(onClick = {
        modInDebugDialog.value = mod
        onShowContextMenuChange(false)
    }) {
        Image(
            painter = painterResource("icon-debug.svg"),
            colorFilter = ColorFilter.tint(MaterialTheme.colors.onSurface),
            modifier = Modifier.padding(end = 12.dp).size(24.dp),
            contentDescription = null
        )
        Text("Debug info")
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AppScope.modGridBulkActionMenuItems(checkedRows: SnapshotStateList<Mod>) =
    buildList {
        add(SmolDropdownMenuItemTemplate(
            text = "Enable all",
            onClick = {
                val allModVariants = SL.access.mods.value?.mods?.flatMap { it.variants }
                val doAnyModsHaveMultipleVariants =
                    allModVariants?.groupBy { it.modInfo.id }?.any { it.value.size > 1 } == true

                if (doAnyModsHaveMultipleVariants) {
                    alertDialogSetter.invoke {
                        SmolAlertDialog(
                            title = {
                                Text(
                                    text = "Warning",
                                    style = SmolTheme.alertDialogTitle()
                                )
                            },
                            text = {
                                Text(
                                    text = "This will enable multiple versions of one or more mods at the same time. Starsector will pick only one version of each mod to load." +
                                            "\nAre you sure you want to do this?",
                                    style = SmolTheme.alertDialogBody()
                                )
                            },
                            onDismissRequest = ::dismissAlertDialog,
                            confirmButton = {
                                SmolButton(onClick = { enableAllDisabled(allModVariants) }) { Text("Enable All") }
                            },
                            dismissButton = {
                                SmolSecondaryButton(onClick = ::dismissAlertDialog) { Text("Cancel") }
                            },
                        )
                    }
                } else {
                    enableAllDisabled(allModVariants)
                }
                true
            }
        ))

        if (checkedRows.any { it.uiEnabled }) {
            add(SmolDropdownMenuItemTemplate(
                text = "Disable all",
                onClick = {
                    GlobalScope.launch(Dispatchers.IO) {
                        checkedRows
                            .parallelMap { SL.access.disableMod(it) }
                    }
                    true
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
                true
            }
        ))
    }

private fun enableAllDisabled(modVariants: List<ModVariant>?) {
    GlobalScope.launch(Dispatchers.IO) {
        modVariants
            ?.filter { it.mod(SL.access)?.isEnabled(it) == false }
            ?.parallelMap { SL.access.changeActiveVariant(it.mod(SL.access)!!, it) }
    }
}