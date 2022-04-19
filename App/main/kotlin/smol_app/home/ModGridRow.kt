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

import AppScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.mouseClickable
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import smol_access.SL
import smol_access.business.UserManager
import smol_access.model.Mod
import smol_access.model.UserProfile
import smol_app.composables.SmolText
import smol_app.composables.SmolTooltipArea
import smol_app.composables.SmolTooltipText
import smol_app.themes.SmolTheme
import utilities.exhaustiveWhen

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun AppScope.ModGridRow(
    modifier: Modifier = Modifier,
    modRow: ModRow,
    checkedRows: SnapshotStateList<Mod>,
    selectedRow: MutableState<ModRow?>,
    contentPadding: Dp,
    favoritesWidth: Dp,
    profile: State<UserProfile>,
    largestVramUsage: MutableState<Long?>,
    checkboxesWidth: Dp,
    modInDebugDialog: MutableState<Mod?>,
    mods: SnapshotStateList<Mod?>
) {
    val mod = modRow.mod
    var showContextMenu by remember { mutableStateOf(false) }
    val highestLocalVCVersion =
        mod.findHighestVersion?.versionCheckerInfo?.modVersion
    val onlineVersionInfo = smol_access.SL.versionChecker.getOnlineVersion(modId = mod.id)
    val onlineVersion = onlineVersionInfo?.modVersion
    var isRowHighlighted by remember { mutableStateOf(false) }

    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .mouseClickable {
                if (this.buttons.isPrimaryPressed) {
                    // If in "Checking rows" mode, clicking a row toggles checked.
                    // Otherwise, it open/closes Details panel
                    if (checkedRows.any()) {
                        if (mod !in checkedRows) {
                            checkedRows.add(mod)
                        } else {
                            checkedRows.remove(mod)
                        }
                    } else {
                        selectedRow.value =
                            (if (selectedRow.value == modRow) null else modRow)
                    }
                } else if (this.buttons.isSecondaryPressed) {
                    showContextMenu = !showContextMenu
                }
            }
            .background(
                color = if (isRowHighlighted || selectedRow.value?.mod?.id == mod.id || mod in checkedRows)
                    Color.Black.copy(alpha = .1f)
                else Color.Transparent
            )
            .pointerMoveFilter(
                onEnter = {
                    isRowHighlighted = true
                    false
                },
                onExit = {
                    isRowHighlighted = false
                    false
                })
    ) {
        Column(
            modifier = Modifier.padding(
                start = contentPadding,
                end = contentPadding
            )
        ) {
            Row(Modifier.fillMaxWidth()) {
                (SL.userManager.activeProfile.collectAsState().value.modGridPrefs.columnSettings
                    ?: UserManager.defaultProfile.modGridPrefs.columnSettings!!)
                    .entries
                    .filter { it.value.isVisible }
                    .sortedBy { it.value.position }
                    .forEach { (column, settings) ->
                        when (column) {
                            UserProfile.ModGridHeader.Favorites -> {
                                // Favorites
                                FavoriteButton(favoritesWidth, mod, profile, isRowHighlighted)
                            }
                            UserProfile.ModGridHeader.ChangeVariantButton -> {
                                // Mod Version Dropdown
                                ModVariantsDropdown(
                                    modifier = Modifier
                                        .width(modGridViewDropdownWidth.dp)
                                        .align(Alignment.CenterVertically),
                                    mod = mod
                                )
                            }
                            UserProfile.ModGridHeader.Name -> {
                                // Mod name
                                SmolText(
                                    modifier = Modifier.weight(1f)
                                        .align(Alignment.CenterVertically),
                                    text = (mod.findFirstEnabled
                                        ?: mod.findHighestVersion)?.modInfo?.name
                                        ?: "",
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontFamily = if (profile.value.useOrbitronNameFont!!) SmolTheme.orbitronSpaceFont else null,
                                    fontSize = if (profile.value.useOrbitronNameFont!!) 15.sp else TextUnit.Unspecified
                                )
                            }
                            UserProfile.ModGridHeader.Author -> {
                                // Mod Author
                                SmolText(
                                    text = (mod.findFirstEnabledOrHighestVersion)?.modInfo?.author
                                        ?: "",
                                    color = SmolTheme.dimmedTextColor(),
                                    modifier = Modifier.weight(1f)
                                        .align(Alignment.CenterVertically),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            UserProfile.ModGridHeader.Version -> {
                                // Mod version (active or highest)
                                Row(Modifier.weight(1f).align(Alignment.CenterVertically)) {
                                    // Update badge icon
                                    ModUpdateIcon(
                                        modifier = Modifier.align(Alignment.CenterVertically),
                                        highestLocalVersion = highestLocalVCVersion,
                                        onlineVersion = onlineVersion,
                                        onlineVersionInfo = onlineVersionInfo,
                                        mod = mod
                                    )

                                    // Versions discovered
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.CenterVertically)
                                            .weight(1f)
                                    ) {
                                        val dimColor =
                                            MaterialTheme.colors.onBackground.copy(alpha = 0.4f)

                                        SmolText(
                                            text = buildAnnotatedString {
                                                mod.variants
                                                    .forEachIndexed { index, element ->
                                                        val enabled = mod.isEnabled(element)
                                                        this.withStyle(
                                                            SpanStyle(color = if (enabled) Color.Unspecified else dimColor)
                                                        ) {
                                                            append(element.modInfo.version.toString())
                                                            append(
                                                                if (index != mod.variants.count() - 1)
                                                                    ", " else ""
                                                            )
                                                        }
                                                    }
                                            },
                                            overflow = TextOverflow.Ellipsis,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                            UserProfile.ModGridHeader.VramImpact -> {
                                // VRAM
                                Row(Modifier.weight(1f).align(Alignment.CenterVertically)) {
                                    vramBar(mod, largestVramUsage.value)
                                }
                            }
                            UserProfile.ModGridHeader.Icons -> {
                                // Mod Icon
                                Box(Modifier.padding(end = 16.dp).align(Alignment.CenterVertically)) {
                                    val modInfo = mod.findFirstEnabledOrHighestVersion?.modInfo
                                    val alpha = 0.7f

                                    when {
                                        modInfo?.isTotalConversion == true -> {
                                            SmolTooltipArea(tooltip = { SmolTooltipText(text = "Total Conversion mods should not be run with any other mods, except for Utility Mods, unless explicitly stated to be compatible.") }) {
                                                Icon(
                                                    painter = painterResource("icon-death-star.svg"),
                                                    modifier = Modifier.size(24.dp),
                                                    contentDescription = null,
                                                    tint = LocalContentColor.current.copy(alpha = alpha),
                                                )
                                            }
                                        }
                                        modInfo?.isUtilityMod == true -> {
                                            SmolTooltipArea(tooltip = { SmolTooltipText(text = "Utility mods may be added or removed from a save at will.") }) {
                                                Icon(
                                                    painter = painterResource("icon-utility-mod.svg"),
                                                    modifier = Modifier.size(24.dp),
                                                    contentDescription = null,
                                                    tint = LocalContentColor.current.copy(alpha = alpha),
                                                )
                                            }
                                        }
                                        else -> {
                                            Spacer(Modifier.size(24.dp))
                                        }
                                    }
                                }
                            }
                            UserProfile.ModGridHeader.GameVersion -> {
                                // Game version (for active or highest)
                                Row(Modifier.weight(1f).align(Alignment.CenterVertically)) {
                                    Text(
                                        text = (mod.findFirstEnabled
                                            ?: mod.findHighestVersion)?.modInfo?.gameVersion ?: "",
                                        modifier = Modifier.align(Alignment.CenterVertically),
                                        color = SmolTheme.dimmedTextColor()
                                    )
                                }
                            }
                            UserProfile.ModGridHeader.Category ->
                                // Category
                                Row(Modifier.weight(1f).align(Alignment.CenterVertically)) {
                                    val metadata = SL.modMetadata.mergedData.value[mod.id]
                                    Text(
                                        text = metadata?.category ?: "",
                                        modifier = Modifier.align(Alignment.CenterVertically),
                                        color = SmolTheme.dimmedTextColor()
                                    )
                                }
                        }.exhaustiveWhen()
                    }

                // Checkbox
                val isChecked = mod in checkedRows

                val isCheckboxVisible =
                    isRowHighlighted || isChecked || checkedRows.any()

                Row(
                    modifier = Modifier.width(checkboxesWidth)
                        .align(Alignment.CenterVertically)
                        .alpha(if (isCheckboxVisible) 1f else 0f)
                ) {
                    modGridBulkActionMenu(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        checkedRows = checkedRows
                    )
                    Checkbox(
                        modifier = Modifier.width(checkboxesWidth),
                        checked = isChecked,
                        onCheckedChange = { checked ->
                            if (checked) {
                                checkedRows.add(mod)
                            } else {
                                checkedRows.remove(mod)
                            }
                        }
                    )
                }

                // Context menu
                ModContextMenu(
                    showContextMenu = showContextMenu,
                    onShowContextMenuChange = { showContextMenu = it },
                    mod = mod,
                    modInDebugDialog = modInDebugDialog,
                    checkedMods = checkedRows
                )
            }

            Row {
                Spacer(Modifier.width(modGridViewDropdownWidth.dp))
                // Dependency warning
                DependencyFixerRow(mod, mods.filterNotNull())
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.FavoriteButton(
    favoritesWidth: Dp,
    mod: Mod,
    profile: State<UserProfile>,
    isRowHighlighted: Boolean
) {
    Row(
        modifier = Modifier
            .width(favoritesWidth)
            .align(Alignment.CenterVertically),
    ) {
        val isFavorited = mod.id in profile.value.favoriteMods
        if (isFavorited || isRowHighlighted) {
            Box(
                Modifier
                    .padding(end = 16.dp)
            ) {
                Icon(
                    imageVector =
                    (if (isFavorited)
                        Icons.Default.Favorite
                    else Icons.Default.FavoriteBorder),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .mouseClickable {
                            SL.userManager.setModFavorited(
                                modId = mod.id,
                                newFavoriteValue = isFavorited.not()
                            )
                        },
                    tint =
                    (if (isFavorited)
                        MaterialTheme.colors.secondary.copy(alpha = .6f)
                    else MaterialTheme.colors.primary),
                )
            }
        }
    }
}

