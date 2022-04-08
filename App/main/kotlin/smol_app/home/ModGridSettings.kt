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

package smol_app.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import smol_access.SL
import smol_access.business.UserManager
import smol_access.model.UserProfile
import smol_app.composables.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModGridSettings() {
    var showPopup by remember { mutableStateOf(false) }
    SmolTooltipArea(
        tooltip = { SmolTooltipText(text = "Column Settings") }
    ) {
        SmolIconButton(
            onClick = {
                showPopup = showPopup.not()
            },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                painter = painterResource("icon-settings.svg"),
                contentDescription = null
            )
        }
    }

    if (showPopup) {
        Popup(
            onDismissRequest = {
                showPopup = false
            },
            popupPositionProvider = TooltipPlacement.CursorPoint(
                offset = DpOffset(0.dp, 16.dp)
            ).positionProvider(),
        ) {
            Card(
                elevation = 8.dp
            ) {
                val items = (SL.userManager.activeProfile.collectAsState().value.modGridPrefs.columnSettings
                    ?: UserManager.defaultProfile.modGridPrefs.columnSettings!!)
                    .entries
                    .sortedBy { it.value.position }

                val dragState = remember {
                    ReorderableColumnState(onReorder = { oldIndex, newIndex ->
                        SL.userManager.updateUserProfile { profile ->
                            val columnSettingsAfterSwap = (profile.modGridPrefs.columnSettings
                                ?: UserManager.defaultProfile.modGridPrefs.columnSettings!!)
                                .entries
                                .associate { (key, value) ->
                                    val increasedIndex = newIndex > oldIndex
                                    val range = listOf(newIndex, oldIndex).sorted().let { it[0]..it[1] }
                                    key to (when {
                                        value.position == oldIndex -> value.copy(position = newIndex)
                                        range.contains(value.position) -> value.copy(
                                            position =
                                            if (increasedIndex) value.position - 1
                                            else value.position + 1
                                        )
                                        else -> value
                                    })
                                }

                            profile.copy(
                                modGridPrefs = profile.modGridPrefs.copy(
                                    columnSettings = columnSettingsAfterSwap
                                )
                            )
                        }
                    })
                }
                ReorderableColumn(
                    data = items,
                    state = dragState,
                    modifier = Modifier
                        .padding(16.dp)
                ) { (header, setting) ->
                    Row(Modifier.padding(vertical = 4.dp)
                        .alpha(if (setting.isVisible) 1f else 0.6f)) {
                        Row(Modifier.width(160.dp)) {
                            Icon(
                                painter = painterResource("icon-drag-horizontal.svg"),
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp).align(Alignment.CenterVertically)
                            )
                            Text(
                                text = when (header) {
                                    UserProfile.ModGridHeader.Favorites -> "♥"
                                    UserProfile.ModGridHeader.ChangeVariantButton -> "Enable/Disable"
                                    UserProfile.ModGridHeader.Name -> "Name"
                                    UserProfile.ModGridHeader.Author -> "Author"
                                    UserProfile.ModGridHeader.Version -> "Version"
                                    UserProfile.ModGridHeader.VramImpact -> "VRAM Impact"
                                    UserProfile.ModGridHeader.Icons -> "Mod Type"
                                    UserProfile.ModGridHeader.GameVersion -> "Game Version"
                                    UserProfile.ModGridHeader.Category -> "Category"
                                },
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                        IconButton(
                            onClick = {
                                SL.userManager.updateUserProfile { profile ->
                                    val columnSettingsAfterSwap = (profile.modGridPrefs.columnSettings
                                        ?: UserManager.defaultProfile.modGridPrefs.columnSettings!!)
                                        .entries
                                        .associate { (key, value) ->
                                            key to (if (key == header) value.copy(isVisible = value.isVisible.not())
                                            else value)
                                        }

                                    profile.copy(
                                        modGridPrefs = profile.modGridPrefs.copy(
                                            columnSettings = columnSettingsAfterSwap
                                        )
                                    )
                                }
                            },
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .align(Alignment.CenterVertically)
                                .size(16.dp)
                        ) {
                            Icon(
                                contentDescription = "visibility",
                                painter = painterResource(if (setting.isVisible) "icon-show.svg" else "icon-hide.svg")
                            )
                        }
                    }
                }
            }
        }
    }
}