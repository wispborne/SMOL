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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.Checkbox
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import smol.VramChecker
import smol.access.SL
import smol.access.model.Mod
import smol.access.model.UserProfile
import smol.app.composables.SmolTooltipArea
import smol.app.composables.SmolTooltipText
import smol.app.themes.SmolTheme
import smol.app.util.replaceAllUsingDifference
import smol.app.util.uiEnabled
import smol.utilities.bytesAsShortReadableMB
import smol.utilities.exhaustiveWhen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppScope.ModGridHeader(
    favoritesWidth: Dp,
    activeSortField: ModGridSortField?,
    profile: State<UserProfile>,
    vramPosition: MutableState<Dp>,
    showVramRefreshWarning: MutableState<Boolean>,
    checkboxesWidth: Dp,
    checkedRows: SnapshotStateList<Mod>,
    mods: List<Mod?>
) {
    Row {
        SL.userManager.activeProfile.collectAsState().value.modGridPrefs.columnSettings!!
            .entries
            .filter { it.value.isVisible }
            .sortedBy { it.value.position }
            .forEach { (column, settings) ->
                when (column) {
                    UserProfile.ModGridHeader.Favorites -> {
                        Spacer(
                            modifier = Modifier.width(favoritesWidth)
                                .align(Alignment.CenterVertically)
                        )
                    }

                    UserProfile.ModGridHeader.ChangeVariantButton -> {
                        // Enabled/Disabled
                        SortableHeader(
                            modifier = Modifier.width(modGridViewDropdownWidth.dp).align(Alignment.CenterVertically),
                            columnSortField = ModGridSortField.EnabledState,
                            activeSortField = activeSortField,
                            profile = profile
                        ) {
                            refreshButton {
                                GlobalScope.launch(Dispatchers.Default) {
                                    forceReloadMods()
                                }
                            }
                        }
                    }

                    UserProfile.ModGridHeader.Name -> {
                        // Name
                        SortableHeader(
                            modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                            columnSortField = ModGridSortField.Name,
                            activeSortField = activeSortField,
                            profile = profile
                        ) {
                            Text("Name", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    UserProfile.ModGridHeader.Author -> {
                        // Author
                        SortableHeader(
                            modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                            columnSortField = ModGridSortField.Author,
                            activeSortField = activeSortField,
                            profile = profile
                        ) {
                            Text("Author", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    UserProfile.ModGridHeader.Version -> {
                        // Versions
                        SmolTooltipArea(
                            modifier = Modifier
                                .weight(1f)
                                .align(Alignment.CenterVertically)
                                .padding(start = SmolTheme.modUpdateIconSize.dp),
                            tooltip = { SmolTooltipText(text = "The version(s) tracked by SMOL.") },
                            delayMillis = SmolTooltipArea.shortDelay
                        ) {
                            SortableHeader(
                                modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                                columnSortField = ModGridSortField.Version,
                                activeSortField = activeSortField,
                                profile = profile
                            ) {
                                Text(
                                    text = "Version(s)",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    UserProfile.ModGridHeader.VramImpact -> {
                        // VRAM
                        val enabledMods = SL.access.modsFlow.collectAsState().value?.mods?.filter { it.uiEnabled }.orEmpty()
                        val vramUsage = SL.vramChecker.vramUsage.collectAsState().value
                        val allImpactsFromMods = enabledMods.map { getVramImpactForMod(it, vramUsage) }
                        val totalBytesFromMods = allImpactsFromMods.sumOf { it?.bytesForMod ?: 0L }
                        val imageImpactString =
                            "${allImpactsFromMods.sumOf { it?.imageCount ?: 0 }} images"
                        Row(modifier = Modifier
                            .weight(1f)
                            .align(Alignment.CenterVertically)
                            // Set the location of the VRAM column so that the VRAM sum on the Enabled/Disabled groups can use the same position.
                            .onGloballyPositioned { vramPosition.value = it.boundsInParent().left.dp }) {
                            SmolTooltipArea(
                                tooltip = {
                                    SmolTooltipText(
                                        text = buildString {
                                            appendLine("An estimate of how much VRAM the mod will use.")
                                            appendLine("All images are counted, even if not used by the game.")
                                            appendLine("\nEnabled Mods (${enabledMods.count()})")
                                            appendLine("${totalBytesFromMods.bytesAsShortReadableMB} ($imageImpactString)")
                                            appendLine("${VramChecker.VANILLA_GAME_VRAM_USAGE_IN_BYTES.bytesAsShortReadableMB} from vanilla")
                                            append("Σ ${(totalBytesFromMods + VramChecker.VANILLA_GAME_VRAM_USAGE_IN_BYTES).bytesAsShortReadableMB}")
                                        }
                                    )
                                },
                                delayMillis = SmolTooltipArea.shortDelay
                            ) {
                                SortableHeader(
                                    columnSortField = ModGridSortField.VramImpact,
                                    activeSortField = activeSortField,
                                    profile = profile,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                ) {
                                    Text(
                                        text = "VRAM Est.",
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f, fill = false),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            SmolTooltipArea(
                                tooltip = {
                                    SmolTooltipText(
                                        text = "Calculate VRAM Impact for all mods."
                                    )
                                },
                                delayMillis = SmolTooltipArea.shortDelay
                            ) {
                                IconButton(
                                    onClick = { showVramRefreshWarning.value = true },
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .padding(start = 4.dp)
                                        .size(20.dp)
                                ) {
                                    Icon(
                                        painter = painterResource("icon-refresh.svg"),
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    }

                    UserProfile.ModGridHeader.Icons -> {
                        // Mod Icon, paddingEnd + width
                        Spacer(Modifier.width((16 + 24).dp))
                    }

                    UserProfile.ModGridHeader.GameVersion -> {
                        // Game Version
                        Text(
                            text = "Game Version",
                            modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    UserProfile.ModGridHeader.Category ->
                        // Category

                        SortableHeader(
                            modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                            columnSortField = ModGridSortField.Category,
                            activeSortField = activeSortField,
                            profile = profile
                        ) {
                            Text(
                                "Category",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                }.exhaustiveWhen()
            }


        // Header options
        Box(Modifier.align(Alignment.CenterVertically).padding(end = 8.dp)) {
            ModGridSettings()
        }

        Row(
            modifier = Modifier.width(checkboxesWidth)
                .align(Alignment.CenterVertically)
        ) {
            modGridBulkActionMenu(
                modifier = Modifier.align(Alignment.CenterVertically),
                checkedRows = checkedRows
            )
            Checkbox(
                checked = mods.all { mod -> mod != null && mod in checkedRows },
                onCheckedChange = { isChecked ->
                    if (isChecked) {
                        checkedRows.replaceAllUsingDifference(
                            mods.filterNotNull(),
                            doesOrderMatter = false
                        )
                    } else {
                        checkedRows.clear()
                    }
                }
            )
        }
    }
}