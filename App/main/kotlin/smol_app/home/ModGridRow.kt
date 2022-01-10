package smol_app.home

import AppState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.mouseClickable
import androidx.compose.material.*
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.push
import smol_access.Constants
import smol_access.model.Mod
import smol_access.model.UserProfile
import smol_app.composables.SmolText
import smol_app.composables.SmolTooltipArea
import smol_app.composables.SmolTooltipText
import smol_app.navigation.Screen
import smol_app.util.*
import timber.ktx.Timber

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun AppState.ModGridRow(
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
    val highestLocalVersion =
        mod.findHighestVersion?.versionCheckerInfo?.modVersion
    val onlineVersionInfo = smol_access.SL.versionChecker.getOnlineVersion(modId = mod.id)
    val onlineVersion = onlineVersionInfo?.modVersion
    var isRowHighlighted by remember { mutableStateOf(false) }

    ListItem(
        modifier = Modifier
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
                // Favorites
                Row(
                    modifier = Modifier
                        .width(favoritesWidth)
                        .align(Alignment.CenterVertically),
                ) {
                    val isFavorited = mod.id in profile.value.favoriteMods
                    if (isFavorited || isRowHighlighted) {
                        Icon(
                            imageVector =
                            (if (isFavorited)
                                androidx.compose.material.icons.Icons.Default.Favorite
                            else androidx.compose.material.icons.Icons.Default.FavoriteBorder),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .mouseClickable {
                                    smol_access.SL.userManager.setModFavorited(
                                        modId = mod.id,
                                        newFavoriteValue = isFavorited.not()
                                    )
                                },
                            tint = MaterialTheme.colors.primary
                        )
                    }
                }

                // Mod Version Dropdown
                modStateDropdown(
                    modifier = Modifier
                        .width(modGridViewDropdownWidth.dp)
                        .align(Alignment.CenterVertically),
                    mod = mod
                )

                // Mod name
                SmolText(
                    modifier = Modifier.weight(1f)
                        .align(Alignment.CenterVertically),
                    text = (mod.findFirstEnabled
                        ?: mod.findHighestVersion)?.modInfo?.name
                        ?: "",
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    fontFamily = smol_app.themes.SmolTheme.orbitronSpaceFont,
                    fontSize = 14.sp
                )

                // Mod Author
                SmolText(
                    text = (mod.findFirstEnabledOrHighestVersion)?.modInfo?.author
                        ?: "",
                    color = smol_app.themes.SmolTheme.dimmedTextColor(),
                    modifier = Modifier.weight(1f)
                        .align(Alignment.CenterVertically),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                // Mod version (active or highest)
                Row(Modifier.weight(1f).align(Alignment.CenterVertically)) {
                    // Update badge icon
                    if (highestLocalVersion != null && onlineVersion != null && onlineVersion > highestLocalVersion) {
                        val ddUrl =
                            onlineVersionInfo.directDownloadURL?.ifBlank { null }
                                ?: mod.findHighestVersion?.versionCheckerInfo?.directDownloadURL
                        if (ddUrl != null) {
                            SmolTooltipArea(tooltip = {
                                SmolTooltipText(
                                    text = buildString {
                                        append("Newer version available: ${onlineVersionInfo.modVersion}")
                                        append("\n\nClick to download and update.")
                                    }
                                )
                            }, modifier = Modifier.mouseClickable {
                                if (this.buttons.isPrimaryPressed) {
                                    alertDialogSetter {
                                        DirectDownloadAlertDialog(
                                            ddUrl = ddUrl,
                                            mod = mod,
                                            onlineVersion = onlineVersion
                                        )
                                    }
                                }
                            }
                                .align(Alignment.CenterVertically)) {
                                Image(
                                    painter = painterResource("icon-direct-install.svg"),
                                    contentDescription = null,
                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color = MaterialTheme.colors.secondary),
                                    modifier = Modifier.width(28.dp).height(28.dp)
                                        .padding(end = 8.dp)
                                        .align(Alignment.CenterVertically)
                                        .pointerHoverIcon(
                                            PointerIcon(
                                                java.awt.Cursor.getPredefinedCursor(
                                                    java.awt.Cursor.HAND_CURSOR
                                                )
                                            )
                                        )
                                )
                            }
                        }

                        val modThreadId =
                            mod.findHighestVersion?.versionCheckerInfo?.modThreadId
                        val hasModThread = modThreadId?.isNotBlank() == true
                        SmolTooltipArea(tooltip = {
                            SmolTooltipText(
                                text = buildAnnotatedString {
                                    append("Newer version available: ${onlineVersionInfo.modVersion}.")
                                    append("\n\n<i>Update information is provided by the mod author, not SMOL, and cannot be guaranteed.</i>".parseHtml())
                                    if (ddUrl == null) append("\n<i>This mod does not support direct download and should be downloaded manually.</i>".parseHtml())
                                    if (hasModThread) {
                                        append("\n\nClick to open <code>${modThreadId?.getModThreadUrl()}</code>.".parseHtml())
                                    } else {
                                        append("\n\n<b>No mod thread provided. Click to search on Google.</b>".parseHtml())
                                    }
                                }
                            )
                        }, modifier = Modifier.mouseClickable {
                            if (this.buttons.isPrimaryPressed) {
                                if (hasModThread) {
                                    if (Constants.isJCEFEnabled()) {
                                        router.push(Screen.ModBrowser(modThreadId?.getModThreadUrl()))
                                    } else {
                                        kotlin.runCatching {
                                            modThreadId?.getModThreadUrl()
                                                ?.openAsUriInBrowser()
                                        }
                                            .onFailure { Timber.w(it) }
                                    }
                                } else {
                                    createGoogleSearchFor("starsector ${mod.findHighestVersion?.modInfo?.name}")
                                        .openAsUriInBrowser()
                                }
                            }
                        }
                            .align(Alignment.CenterVertically)) {
                            Image(
                                painter = painterResource(
                                    if (hasModThread) "icon-new-update.svg"
                                    else "icon-new-update-search.svg"
                                ),
                                contentDescription = null,
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                    color =
                                    if (ddUrl == null) MaterialTheme.colors.secondary
                                    else MaterialTheme.colors.secondary.copy(alpha = ContentAlpha.disabled)
                                ),
                                modifier = Modifier.width(28.dp).height(28.dp)
                                    .padding(end = 8.dp)
                                    .align(Alignment.CenterVertically)
                                    .pointerHoverIcon(
                                        PointerIcon(
                                            java.awt.Cursor.getPredefinedCursor(
                                                java.awt.Cursor.HAND_CURSOR
                                            )
                                        )
                                    )
                            )
                        }
                    }

                    // Versions discovered
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .weight(1f)
                    ) {
                        val dimColor =
                            MaterialTheme.colors.onBackground.copy(alpha = 0.4f)
                        mod.variants
                            .forEachIndexed { index, element ->
                                val enabled = mod.isEnabled(element)
                                Text(
                                    text = buildString {
                                        append(element.modInfo.version.toString())
                                        append(
                                            if (index != mod.variants.count() - 1)
                                                ", " else ""
                                        )
                                    },
                                    color = if (enabled)
                                        Color.Unspecified
                                    else dimColor
                                )
                            }
                    }
                }

                // VRAM
                Row(Modifier.weight(1f).align(Alignment.CenterVertically)) {
                    vramBar(mod, largestVramUsage.value)
                }

                // Game version (for active or highest)
                Row(Modifier.weight(1f).align(Alignment.CenterVertically)) {
                    Text(
                        text = (mod.findFirstEnabled
                            ?: mod.findHighestVersion)?.modInfo?.gameVersion ?: "",
                        modifier = Modifier.align(Alignment.CenterVertically),
                        color = smol_app.themes.SmolTheme.dimmedTextColor()
                    )
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