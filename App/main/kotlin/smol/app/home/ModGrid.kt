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
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import smol.access.SL
import smol.access.business.metadata
import smol.access.config.VramCheckerCache
import smol.access.model.Mod
import smol.access.model.SmolId
import smol.app.WindowState
import smol.app.composables.SmolAlertDialog
import smol.app.composables.SmolButton
import smol.app.composables.SmolOverflowMenu
import smol.app.composables.SmolSecondaryButton
import smol.app.themes.SmolTheme
import smol.app.util.uiEnabled
import smol.timber.ktx.Timber
import smol.utilities.nullIfBlank

const val modGridViewDropdownWidth = 180

@OptIn(
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun AppScope.ModGridView(
    modifier: Modifier = Modifier,
    mods: List<Mod?>
) {
    Timber.d { "Rendering mod grid." }
    val contentPadding = 16.dp
    val favoritesWidth = 40.dp
    val checkboxesWidth = 40.dp
    val selectedRow = remember { mutableStateOf<ModRow?>(null) }
    val checkedRows = remember { mutableStateListOf<Mod>() }
    val modInDebugDialog = remember { mutableStateOf<Mod?>(null) }
    val vramUsage = SL.vramChecker.vramUsage.collectAsState().value
    val largestVramUsage =
        remember { mutableStateOf(vramUsage?.values?.maxOfOrNull { it.bytesForMod }) }
    val profile = SL.userManager.activeProfile.collectAsState()
    val activeSortField = profile.value.modGridPrefs.sortField?.let {
        kotlin.runCatching { ModGridSortField.valueOf(it) }.getOrNull()
    }
    val showVramRefreshWarning = remember { mutableStateOf(false) }

    Box(
        modifier
            .padding(top = contentPadding, bottom = contentPadding)
    ) {
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
                val listState = rememberLazyListState()
                val groupingSetting = SL.userManager.activeProfile.collectAsState().value.modGridPrefs.groupingSetting!!
                val grouping = groupingSetting.grouping.mapToGroup(SL.modMetadata)
                val collapseStates = remember { mutableStateMapOf<Any?, Boolean>() }

                LazyColumn(Modifier.fillMaxWidth(), state = listState) {
                    mods
                        .filterNotNull()
                        .groupBy { grouping.getGroupSortValue(it) }
                        .toSortedMap(if (groupingSetting.isSortDescending) compareByDescending { it } else compareBy { it })
                        .forEach { (modState, modsInGroup) ->
                            val isCollapsed = collapseStates[modState] ?: false
                            val groupName = modsInGroup.firstOrNull()?.let { grouping.getGroupName(it) } ?: ""
                            stickyHeader {
                                ModGridSectionHeader(
                                    contentPadding = contentPadding,
                                    isCollapsed = isCollapsed,
                                    setCollapsed = { collapseStates[modState] = it },
                                    groupName = groupName,
                                    modsInGroup = modsInGroup,
                                    vramPosition = vramPosition
                                )
                            }
                            if (!isCollapsed) {
                                fun getSortValue(modRow: ModRow): Comparable<Any>? {
                                    return when (activeSortField) {
                                        ModGridSortField.EnabledState -> modRow.mod.uiEnabled
                                        ModGridSortField.Name -> modRow.mod.findFirstEnabledOrHighestVersion?.modInfo?.name?.lowercase()
                                        ModGridSortField.Author -> modRow.mod.findFirstEnabledOrHighestVersion?.modInfo?.author?.lowercase()
                                        ModGridSortField.VramImpact -> getVramImpactForMod(
                                            modRow.mod,
                                            vramUsage
                                        )?.bytesForMod
                                        ModGridSortField.Category -> modRow.mod.metadata(SL.modMetadata)?.category?.lowercase()
                                            ?.nullIfBlank()
                                        null -> null
                                    } as Comparable<Any>?
                                }

                                this.items(
                                    items = modsInGroup
                                        .map { ModRow(mod = it) }
                                        .sortedWith(
                                            compareByDescending<ModRow> { it.mod.id in profile.value.favoriteMods }
                                                .let { comparator ->
                                                    when {
                                                        activeSortField == null -> comparator.thenBy { null }
                                                        profile.value.modGridPrefs.isSortDescending ->
                                                            comparator.thenByDescending(nullsFirst()) {
                                                                getSortValue(it)
                                                            }
                                                        else -> {
                                                            comparator.thenBy(nullsLast()) {
                                                                getSortValue(it)
                                                            }
                                                        }
                                                    }
                                                }
                                                .thenBy { it.mod.findFirstEnabledOrHighestVersion?.modInfo?.name }
                                        )
                                ) { modRow ->
                                    ModGridRow(
                                        modifier = Modifier,
                                        modRow = modRow,
                                        checkedRows = checkedRows,
                                        selectedRow = selectedRow,
                                        contentPadding = contentPadding,
                                        favoritesWidth = favoritesWidth,
                                        profile = profile,
                                        largestVramUsage = largestVramUsage,
                                        checkboxesWidth = checkboxesWidth,
                                        modInDebugDialog = modInDebugDialog,
                                        mods = mods
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
    }
}

fun getVramImpactForMod(mod: Mod, map: Map<SmolId, VramCheckerCache.Result>?) =
    map?.get(
        mod.findFirstEnabledOrHighestVersion?.smolId
    )

@Composable
fun AppScope.modGridBulkActionMenu(modifier: Modifier = Modifier, checkedRows: SnapshotStateList<Mod>) {
    SmolOverflowMenu(
        modifier = modifier
            .padding(end = 8.dp)
            .alpha(if (checkedRows.any()) 1f else 0f),
        items = modGridBulkActionMenuItems(checkedRows)
    )
}

@Preview
@Composable
fun previewModGrid() {
    AppScope(windowState = WindowState(), recomposer = currentRecomposeScope)
        .ModGridView(Modifier, SnapshotStateList())
}

data class ModRow(
    val mod: Mod
)