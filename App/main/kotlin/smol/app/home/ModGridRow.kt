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
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.tinylog.Logger
import smol.access.SL
import smol.access.business.UserManager
import smol.access.model.Mod
import smol.access.model.ModVariant
import smol.access.model.UserProfile
import smol.app.composables.SmolText
import smol.app.composables.SmolTooltipArea
import smol.app.composables.SmolTooltipText
import smol.app.isAprilFools
import smol.app.themes.SmolTheme
import smol.utilities.exhaustiveWhen

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
    isFinalFavoritedRow: Boolean,
    mods: List<Mod?>
) {
    val mod = modRow.mod
    var showContextMenu by remember { mutableStateOf(false) }
    val highestLocalVCVersion =
        mod.findHighestVersion?.versionCheckerInfo?.modVersion
    val onlineVersionInfo = SL.versionChecker.onlineVersions.collectAsState().value[mod.id]?.info
    val onlineVersion = onlineVersionInfo?.modVersion
    var isRowHighlighted by remember { mutableStateOf(false) }
    val isFavorited = mod.id in profile.value.favoriteMods
    val paddingBetweenFavoritesAndRest = 8.dp

    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .onClick(onDoubleClick = {
                GlobalScope.launch {
                    runCatching {
                        // Change mod state
                        if (mod.hasEnabledVariant) {
                            SL.access.disableMod(mod = mod)
                        } else {
                            SL.access.changeActiveVariant(mod, mod.findHighestVersion)
                        }
                    }
                        .onFailure { Logger.error(it) }
                }
            }, onClick = {
                // If in "Checking rows" mode, clicking a row toggles checked.
                // Otherwise, it open/closes Details panel.
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
            })
            .onClick(matcher = PointerMatcher.mouse(PointerButton.Secondary)) {
                showContextMenu = !showContextMenu
            }.run {
                // Split into two paddings, one before color change and one after so that it's evenly divided.
                if (isFinalFavoritedRow)
                    this.padding(bottom = paddingBetweenFavoritesAndRest / 2)
                else this.padding(0.dp)
            }
            .background(
                color = if (isRowHighlighted || selectedRow.value?.mod?.id == mod.id || mod in checkedRows)
                    Color.Black.copy(alpha = .1f)
                else if (isFavorited) MaterialTheme.colors.secondary.copy(alpha = .04f)
                else Color.Transparent
            )
            .onPointerEvent(PointerEventType.Enter) {
                isRowHighlighted = true
            }
            .onPointerEvent(PointerEventType.Exit) {
                isRowHighlighted = false
            }.run {
                if (isFinalFavoritedRow)
                    this.padding(bottom = paddingBetweenFavoritesAndRest / 2)
                else this.padding(0.dp)
            }
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
                                var modAuthorName = ((mod.findFirstEnabledOrHighestVersion)?.modInfo?.author
                                    ?: "")
                                if (isAprilFools())
                                    modAuthorName =
                                        if (modAuthorName.isBlank()) "Tartiflette" else "$modAuthorName, Tartiflette"
                                SmolText(
                                    text = modAuthorName,
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
                                        val enabledVariant = mod.findFirstEnabled
                                        val enabledOrHighest = mod.findFirstEnabledOrHighestVersion

                                        SmolTooltipArea(
                                            tooltip = {
                                                SmolTooltipText(
                                                    text = buildAnnotatedString {
                                                        appendLine("Mod Info:        ${enabledOrHighest?.modInfo?.version?.raw ?: "(none)"}")
                                                        appendLine("Version Checker: ${enabledOrHighest?.versionCheckerInfo?.modVersion ?: "(none)"}")
                                                        appendLine()
                                                        appendLine("Installed")
                                                        append(
                                                            createInstalledVersionsString(
                                                                mod = mod,
                                                                enabledVariant = enabledVariant,
                                                                dimColor = MaterialTheme.colors.onSurface.copy(
                                                                    alpha = 0.4f
                                                                )
                                                            )
                                                        )
                                                    },
                                                    fontFamily = SmolTheme.fireCodeFont,
                                                    fontSize = 15.sp
                                                )
                                            }
                                        ) {
                                            SmolText(
                                                text = createInstalledVersionsString(mod, enabledVariant, dimColor),
                                                overflow = TextOverflow.Ellipsis,
                                                maxLines = 1,
                                                modifier = Modifier.padding(end = 8.dp),
                                                showTooltipOnOverflow = false
                                            )
                                        }
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
                                        isAprilFools() && listOf("Nia")
                                            .any { modInfo?.author?.contains(it, ignoreCase = true) == true } ->
                                            Image(
                                                painter = painterResource("icon-yawn.svg"),
                                                modifier = Modifier.size(24.dp),
                                                contentDescription = null,
                                                colorFilter = ColorFilter
                                                    .colorMatrix(ColorMatrix().apply {
                                                        setToSaturation(0F)
                                                    }),
                                                alpha = alpha
                                            )

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
                ) {
                    if (isCheckboxVisible) {
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

private fun createInstalledVersionsString(
    mod: Mod,
    enabledVariant: ModVariant?,
    dimColor: Color
) = buildAnnotatedString {
    mod.variants
        .forEachIndexed { index, element ->
            this.withStyle(
                SpanStyle(color = if (element === enabledVariant) Color.Unspecified else dimColor)
            ) {
                append(element.modInfo.version.toString())
                append(
                    if (index != mod.variants.count() - 1)
                        ", " else ""
                )
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

