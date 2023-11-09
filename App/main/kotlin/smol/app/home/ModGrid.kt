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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
import smol.app.isAprilFools
import smol.app.themes.SmolTheme
import smol.app.util.uiEnabled
import smol.timber.ktx.Timber
import smol.utilities.nullIfBlank
import kotlin.math.ceil

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
        runCatching { ModGridSortField.valueOf(it) }.getOrNull()
    }
    val showVramRefreshWarning = remember { mutableStateOf(false) }
    val secondaryColor = MaterialTheme.colors.secondary

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
                                        ModGridSortField.Version -> {
                                            // Check if there's an update available.
                                            val onlineVersion =
                                                SL.versionChecker.onlineVersions.value[modRow.mod.id]?.info?.modVersion
                                            val localVersion =
                                                modRow.mod.findFirstEnabledOrHighestVersion?.versionCheckerInfo?.modVersion

                                            if (onlineVersion != null && localVersion != null) {
                                                localVersion.compareTo(onlineVersion)
                                            } else {
                                                1
                                            }
                                        }

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

                                val modItemsToDisplay = modsInGroup
                                    .map { ModRow(mod = it) }
                                    .sortedWith(
                                        compareByDescending<ModRow> {
                                            isFavorited(
                                                it.mod,
                                                profile.value.favoriteMods
                                            )
                                        }
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
                                this.items(items = modItemsToDisplay) { modRow ->
                                    val isFavorited = isFavorited(modRow.mod, profile.value.favoriteMods)
                                    val nextModRow =
                                        modItemsToDisplay.getOrNull(modItemsToDisplay.indexOf(modRow) + 1)
                                    val isFinalFavoritedRow =
                                        isFavorited && nextModRow?.mod?.id !in profile.value.favoriteMods

                                    Column {
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
                                            isFinalFavoritedRow = isFinalFavoritedRow,
                                            mods = mods
                                        )
                                        // Disabled wavy line for now, it's too distracting.
                                        if (false) {

                                            if (isFinalFavoritedRow) {
                                                Canvas(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .padding(
                                                            start = contentPadding + 32.dp,
                                                            end = contentPadding + 32.dp,
                                                            top = 4.dp,
                                                            bottom = 8.dp
                                                        )
                                                ) {
                                                    val wavyPath = Path().apply {
                                                        val halfPeriod = 64.dp.toPx() / 2
                                                        val amplitude = 2.dp.toPx()
                                                        moveTo(x = -halfPeriod / 2, y = amplitude)
                                                        repeat(ceil(size.width / halfPeriod + 1).toInt()) { i ->
                                                            relativeQuadraticBezierTo(
                                                                dx1 = halfPeriod / 2,
                                                                dy1 = 2 * amplitude * (if (i % 2 == 0) 1 else -1),
                                                                dx2 = halfPeriod,
                                                                dy2 = 0f,
                                                            )
                                                        }
//                                                    lineTo(size.width, size.height)
//                                                    lineTo(0f, size.height)
                                                    }

                                                    drawPath(
                                                        color = secondaryColor.copy(alpha = .2f),
                                                        path = wavyPath,
                                                        style = Stroke(
                                                            width = 1.dp.toPx(),
//                                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                                                        )
                                                    )
                                                }
//                                            Divider(
//                                                modifier = Modifier.padding(
//                                                    horizontal = contentPadding * 2,
//                                                    vertical = 8.dp
//                                                ),
//                                                color = secondaryColor.copy(alpha = .2f)
//                                            )
                                            }
                                        }
                                    }
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
                mods = SL.access.modsFlow.value?.mods ?: emptyList()
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

private fun isFavorited(
    mod: Mod,
    favoriteModIds: List<Any>
) = (mod.id in favoriteModIds
        || (isAprilFools() && mod.findFirstEnabledOrHighestVersion
    ?.modInfo?.author?.contains("Wisp", ignoreCase = true) == true))

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