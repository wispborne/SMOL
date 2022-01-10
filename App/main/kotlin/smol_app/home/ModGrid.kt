@file:OptIn(ExperimentalAnimationApi::class)

package smol_app.home

import AppState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import smol_access.SL
import smol_access.model.Mod
import smol_app.WindowState
import smol_app.composables.*
import smol_app.themes.SmolTheme
import smol_app.util.*
import smol_app.views.detailsPanel

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
    val largestVramUsage = remember { mutableStateOf(SL.vramChecker.vramUsage.value?.values?.maxOf { it.bytesForMod }) }
    val profile = SL.userManager.activeProfile.collectAsState()
    val activeSortField = profile.value.modGridPrefs.sortField?.let {
        kotlin.runCatching { ModGridSortField.valueOf(it) }.getOrNull()
    }
    val showVramRefreshWarning = remember { mutableStateOf(false) }

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
                LazyColumn(Modifier.fillMaxWidth()) {
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
                                        mods
                                    )
                                }
                            }
                        }
                }

                // Bugged in Compose: java.lang.IllegalArgumentException: Index should be non-negative (-1)
//                VerticalScrollbar(
//                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
//                    adapter = rememberScrollbarAdapter(
//                        scrollState = state
//                    )
//                )
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