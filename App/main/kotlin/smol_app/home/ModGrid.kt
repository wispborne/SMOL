@file:OptIn(ExperimentalAnimationApi::class)

package smol_app.home

import AppState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.Checkbox
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import smol_access.SL
import smol_access.model.Mod
import smol_access.model.ModVariant
import smol_app.WindowState
import smol_app.composables.SmolAlertDialog
import smol_app.composables.SmolButton
import smol_app.composables.SmolPopupMenu
import smol_app.composables.SmolSecondaryButton
import smol_app.themes.SmolTheme
import smol_app.util.bytesAsShortReadableMB
import smol_app.util.uiEnabled
import timber.ktx.Timber
import utilities.calculateFileSize
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.name

const val modGridViewDropdownWidth = 180

@OptIn(
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class, ExperimentalUnitApi::class,
    ExperimentalComposeUiApi::class
)
@Composable
fun AppState.ModGridView(
    modifier: Modifier = Modifier,
    mods: SnapshotStateList<Mod?>
) {
    val contentPadding = 16.dp
    val favoritesWidth = 40.dp
    val checkboxesWidth = 40.dp
    val selectedRow = remember { mutableStateOf<ModRow?>(null) }
    val checkedRows = remember { mutableStateListOf<Mod>() }
    val modInDebugDialog = remember { mutableStateOf<Mod?>(null) }
    val largestVramUsage = remember { mutableStateOf(SL.vramChecker.vramUsage.value?.values?.maxOfOrNull { it.bytesForMod }) }
    val profile = SL.userManager.activeProfile.collectAsState()
    val activeSortField = profile.value.modGridPrefs.sortField?.let {
        kotlin.runCatching { ModGridSortField.valueOf(it) }.getOrNull()
    }
    val showVramRefreshWarning = remember { mutableStateOf(false) }
    val variantToConfirmDeletionOf = remember { mutableStateOf<ModVariant?>(null) }

    Box(modifier.padding(top = contentPadding, bottom = contentPadding)) {
        Column(Modifier) {
            val vramPosition = remember { mutableStateOf(0.dp) }
            ListItem(modifier = Modifier.padding(start = contentPadding, end = contentPadding)) {
                ModGridHeader(
                    favoritesWidth = favoritesWidth,
                    activeSortField = activeSortField,
                    profile = profile,
                    vramPosition = vramPosition,
                    showVramRefreshWarning = showVramRefreshWarning,
                    checkboxesWidth = checkboxesWidth,
                    checkedRows = checkedRows,
                    mods = mods
                )
            }
            Box {
                val isEnabledCollapsed = remember { mutableStateOf(false) }
                val isDisabledCollapsed = remember { mutableStateOf(false) }
                val listState = rememberLazyListState()
                LazyColumn(Modifier.fillMaxWidth().animateContentSize(), state = listState) {
                    mods
                        .filterNotNull()
                        .groupBy { it.uiEnabled }
                        .toSortedMap(compareBy { !it }) // Flip to put Enabled at the top
                        .forEach { (modState, modsInGroup) ->
                            val isCollapsed = if (modState) isEnabledCollapsed else isDisabledCollapsed
                            val groupName = when (modState) {
                                true -> "Enabled"
                                false -> "Disabled"
                            }
                            stickyHeader {
                                ModGridSectionHeader(contentPadding, isCollapsed, groupName, modsInGroup, vramPosition)
                            }
                            if (!isCollapsed.value) {
                                this.items(
                                    items = modsInGroup
                                        .map { ModRow(mod = it) }
                                        .sortedWith(
                                            compareByDescending<ModRow> { it.mod.id in profile.value.favoriteMods }
                                                .run {
                                                    fun getSortValue(modRow: ModRow): Comparable<*>? {
                                                        return when (activeSortField) {
                                                            ModGridSortField.Name -> modRow.mod.findFirstEnabledOrHighestVersion?.modInfo?.name?.lowercase()
                                                            ModGridSortField.Author -> modRow.mod.findFirstEnabledOrHighestVersion?.modInfo?.author?.lowercase()
                                                            ModGridSortField.VramImpact -> getVramImpactForMod(modRow.mod)?.bytesForMod
                                                            else -> null
                                                        }
                                                    }
                                                    return@run when {
                                                        activeSortField == null -> thenBy { null }
                                                        profile.value.modGridPrefs.isSortDescending ->
                                                            thenByDescending { getSortValue(it) }
                                                        else -> {
                                                            thenBy { getSortValue(it) }
                                                        }
                                                    }
                                                }
                                                .thenBy { it.mod.findFirstEnabledOrHighestVersion?.modInfo?.name })
                                ) { modRow ->
                                    ModGridRow(
                                        modRow,
                                        checkedRows,
                                        selectedRow,
                                        contentPadding,
                                        favoritesWidth,
                                        profile,
                                        largestVramUsage,
                                        checkboxesWidth,
                                        modInDebugDialog,
                                        mods,
                                        variantToConfirmDeletionOf
                                    )
                                }
                            }
                        }
                }

                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(
                        scrollState = listState
                    )
                )
            }
        }

        if (selectedRow.value != null) {
            detailsPanel(
                modifier = Modifier.padding(bottom = contentPadding),
                selectedRow = selectedRow,
                mods = SL.access.mods.value?.mods ?: emptyList()
            )
        }

        if (modInDebugDialog.value != null) {
            debugDialog(mod = modInDebugDialog.value!!, onDismiss = { modInDebugDialog.value = null })
        }

        if (showVramRefreshWarning.value) {
            SmolAlertDialog(
                onDismissRequest = { showVramRefreshWarning.value = false },
                confirmButton = {
                    SmolButton(onClick = {
                        showVramRefreshWarning.value = false
                        GlobalScope.launch {
                            SL.vramChecker.refreshVramUsage(mods = mods.toList().filterNotNull())
                        }
                    }) { Text("Calculate") }
                },
                dismissButton = {
                    SmolSecondaryButton(onClick = {
                        showVramRefreshWarning.value = false
                    }) { Text("Cancel") }
                },
                title = { Text(text = "Calculate VRAM Impact", style = SmolTheme.alertDialogTitle()) },
                text = {
                    Text(
                        text = "Calculating VRAM may take a long time, with high CPU and disk usage causing SMOL to stutter.\n\nAre you sure you want to continue?",
                        style = SmolTheme.alertDialogBody()
                    )
                }
            )
        }

        if (variantToConfirmDeletionOf.value != null) {
            val modVariantBeingRemoved = variantToConfirmDeletionOf.value!!
            var shouldRemoveArchive by remember { mutableStateOf(true) }
            var shouldRemoveStagingAndMods by remember { mutableStateOf(true) }

            SmolAlertDialog(
                title = {
                    Text(
                        text = "Delete ${modVariantBeingRemoved.modInfo.name} ${modVariantBeingRemoved.modInfo.version}?",
                        style = SmolTheme.alertDialogTitle()
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Are you sure you want to permanently delete:",
                            modifier = Modifier.padding(bottom = 8.dp),
                            style = SmolTheme.alertDialogBody()
                        )

                        if (modVariantBeingRemoved.archiveInfo?.folder?.exists() == true) {
                            Row {
                                Checkbox(
                                    modifier = Modifier.align(Alignment.CenterVertically),
                                    checked = shouldRemoveArchive,
                                    onCheckedChange = { shouldRemoveArchive = shouldRemoveArchive.not() }
                                )
                                Text(
                                    text = "${modVariantBeingRemoved.archiveInfo?.folder?.name} ${
                                        kotlin.runCatching { modVariantBeingRemoved.archiveInfo?.folder?.fileSize() }
                                            .getOrNull()?.bytesAsShortReadableMB?.let { "($it)" }
                                    }",
                                    modifier = Modifier.align(Alignment.CenterVertically),
                                    style = SmolTheme.alertDialogBody()
                                )
                            }
                        } else {
                            shouldRemoveArchive = false
                        }

                        val looseFilesToShow = modVariantBeingRemoved.stagingInfo?.folder.let {
                            if (it?.exists() != true)
                                modVariantBeingRemoved.modsFolderInfo?.folder
                            else it
                        }

                        if (looseFilesToShow?.exists() == true) {
                            Row {
                                Checkbox(
                                    modifier = Modifier.align(Alignment.CenterVertically),
                                    checked = shouldRemoveStagingAndMods,
                                    onCheckedChange = { shouldRemoveStagingAndMods = shouldRemoveStagingAndMods.not() }
                                )
                                var folderSize by remember { mutableStateOf<String?>("calculating") }

                                LaunchedEffect(looseFilesToShow.absolutePathString()) {
                                    withContext(Dispatchers.Default) {
                                        folderSize = kotlin.runCatching {
                                            looseFilesToShow.calculateFileSize()
                                        }
                                            .onFailure { Timber.w(it) }
                                            .getOrNull()?.bytesAsShortReadableMB
                                    }
                                }

                                Text(
                                    text = "${looseFilesToShow.name} ${
                                        folderSize.let { "($it)" }
                                    }",
                                    modifier = Modifier.align(Alignment.CenterVertically),
                                    style = SmolTheme.alertDialogBody()
                                )
                            }
                        } else {
                            shouldRemoveStagingAndMods = false
                        }
                    }
                },
                onDismissRequest = { variantToConfirmDeletionOf.value = null },
                dismissButton = {
                    SmolSecondaryButton(onClick = { variantToConfirmDeletionOf.value = null }) {
                        Text("Cancel")
                    }
                },
                confirmButton = {
                    SmolButton(
                        modifier = Modifier.padding(end = 4.dp),
                        onClick = {
                            variantToConfirmDeletionOf.value = null
                            SL.access.deleteVariant(
                                modVariant = modVariantBeingRemoved,
                                removeArchive = shouldRemoveArchive,
                                removeUncompressedFolder = shouldRemoveStagingAndMods
                            )
                        }) {
                        Text(text = "Delete")
                    }
                }
            )
        }
    }
}

fun getVramImpactForMod(mod: Mod) =
    SL.vramChecker.vramUsage.value?.get(
        mod.findFirstEnabledOrHighestVersion?.smolId
    )

@Composable
fun modGridBulkActionMenu(modifier: Modifier = Modifier, checkedRows: SnapshotStateList<Mod>) {
    SmolPopupMenu(
        modifier = modifier
            .padding(end = 8.dp)
            .alpha(if (checkedRows.any()) 1f else 0f),
        items = modGridBulkActionMenuItems(checkedRows)
    )
}

@Preview
@Composable
fun previewModGrid() {
    AppState(WindowState())
        .ModGridView(Modifier, SnapshotStateList())
}

data class ModRow(
    val mod: Mod
)