package smol_app.home

import AppState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.material.Checkbox
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import smol_access.model.Mod
import smol_access.model.UserProfile
import smol_app.composables.SmolTooltipArea
import smol_app.composables.SmolTooltipText
import smol_app.util.replaceAllUsingDifference

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppState.ModGridHeader(
    favoritesWidth: Dp,
    activeSortField: ModGridSortField?,
    profile: State<UserProfile>,
    vramPosition: MutableState<Dp>,
    showVramRefreshWarning: MutableState<Boolean>,
    checkboxesWidth: Dp,
    checkedRows: SnapshotStateList<Mod>,
    mods: SnapshotStateList<Mod?>
) {
    Row {
        Spacer(
            modifier = androidx.compose.ui.Modifier.width(favoritesWidth)
                .align(androidx.compose.ui.Alignment.CenterVertically)
        )
        Box(
            modifier = androidx.compose.ui.Modifier.width(modGridViewDropdownWidth.dp)
                .align(androidx.compose.ui.Alignment.CenterVertically)
        ) {
            refreshButton {
                GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                    reloadMods()
                }
            }
        }

        SortableHeader(
            modifier = androidx.compose.ui.Modifier.weight(1f).align(androidx.compose.ui.Alignment.CenterVertically),
            columnSortField = ModGridSortField.Name,
            activeSortField = activeSortField,
            profile = profile
        ) {
            Text("Name", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }

        SortableHeader(
            modifier = androidx.compose.ui.Modifier.weight(1f).align(androidx.compose.ui.Alignment.CenterVertically),
            columnSortField = ModGridSortField.Author,
            activeSortField = activeSortField,
            profile = profile
        ) {
            Text("Author", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }

        SmolTooltipArea(
            modifier = androidx.compose.ui.Modifier.weight(1f).align(androidx.compose.ui.Alignment.CenterVertically),
            tooltip = { SmolTooltipText(text = "The version(s) tracked by SMOL.") },
            delayMillis = SmolTooltipArea.shortDelay
        ) {
            Text(text = "Version(s)", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }

        Row(modifier = androidx.compose.ui.Modifier
            .weight(1f)
            .align(androidx.compose.ui.Alignment.CenterVertically)
            .onGloballyPositioned { vramPosition.value = it.boundsInParent().left.dp }) {
            SmolTooltipArea(
                tooltip = {
                    SmolTooltipText(
                        text = "An estimate of how much VRAM the mod will use." +
                                "\nAll images are counted, even if not used by the game."
                    )
                },
                delayMillis = SmolTooltipArea.shortDelay
            ) {
                SortableHeader(
                    columnSortField = ModGridSortField.VramImpact,
                    activeSortField = activeSortField,
                    profile = profile,
                    modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.CenterVertically)
                ) {
                    Text(text = "VRAM Impact", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
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
                    modifier = androidx.compose.ui.Modifier
                        .padding(start = 6.dp)
                        .size(20.dp)
                        .align(androidx.compose.ui.Alignment.CenterVertically)
                ) {
                    Icon(
                        painter = painterResource("icon-refresh.svg"),
                        contentDescription = null
                    )
                }
            }
        }

        Text(
            text = "Game Version",
            modifier = androidx.compose.ui.Modifier.weight(1f).align(androidx.compose.ui.Alignment.CenterVertically),
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        Row(
            modifier = androidx.compose.ui.Modifier.width(checkboxesWidth)
                .align(androidx.compose.ui.Alignment.CenterVertically)
        ) {
            modGridBulkActionMenu(
                modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.CenterVertically),
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