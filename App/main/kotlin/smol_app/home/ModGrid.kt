@file:OptIn(ExperimentalAnimationApi::class)

package smol_app.home

import AppState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.*
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.mouseClickable
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.push
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import smol_access.Constants
import smol_access.SL
import smol_access.model.Mod
import smol_app.WindowState
import smol_app.composables.*
import smol_app.navigation.Screen
import smol_app.themes.SmolTheme
import smol_app.util.*
import smol_app.views.detailsPanel
import timber.ktx.Timber
import java.awt.Cursor

private const val modGridViewDropdownWidth = 180

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
    val largestVramUsage by remember { mutableStateOf(SL.vramChecker.vramUsage.value?.values?.maxOf { it.bytesForMod }) }
    val profile = SL.userManager.activeProfile.collectAsState()
    val activeSortField = profile.value.modGridPrefs.sortField?.let {
        kotlin.runCatching { ModGridSortField.valueOf(it) }.getOrNull()
    }
    var showVramRefreshWarning by remember { mutableStateOf(false) }

    Box(modifier.padding(top = contentPadding, bottom = contentPadding)) {
        Column(Modifier) {
            ListItem(modifier = Modifier.padding(start = contentPadding, end = contentPadding)) {
                Row {
                    Spacer(modifier = Modifier.width(favoritesWidth).align(Alignment.CenterVertically))
                    Box(modifier = Modifier.width(modGridViewDropdownWidth.dp).align(Alignment.CenterVertically)) {
                        refreshButton {
                            GlobalScope.launch(Dispatchers.Default) {
                                reloadMods()
                            }
                        }
                    }

                    SortableHeader(
                        modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                        columnSortField = ModGridSortField.Name,
                        activeSortField = activeSortField,
                        profile = profile
                    ) {
                        Text("Name", fontWeight = FontWeight.Bold)
                    }

                    SortableHeader(
                        modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                        columnSortField = ModGridSortField.Author,
                        activeSortField = activeSortField,
                        profile = profile
                    ) {
                        Text("Author", fontWeight = FontWeight.Bold)
                    }

                    SmolTooltipArea(
                        modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                        tooltip = { SmolTooltipText(text = "The version(s) tracked by SMOL.") },
                        delayMillis = SmolTooltipArea.delay
                    ) {
                        Text(text = "Version(s)", fontWeight = FontWeight.Bold)
                    }

                    Row(modifier = Modifier.weight(1f).align(Alignment.CenterVertically)) {
                        SmolTooltipArea(
                            tooltip = {
                                SmolTooltipText(
                                    text = "An estimate of how much VRAM the mod will use." +
                                            "\nAll images are counted, even if not used by the game."
                                )
                            },
                            delayMillis = SmolTooltipArea.delay
                        ) {
                            SortableHeader(
                                columnSortField = ModGridSortField.VramImpact,
                                activeSortField = activeSortField,
                                profile = profile,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            ) {
                                Text(text = "VRAM Impact", fontWeight = FontWeight.Bold)
                            }
                        }

                        SmolTooltipArea(
                            tooltip = {
                                SmolTooltipText(
                                    text = "Calculate VRAM Impact for all mods."
                                )
                            },
                            delayMillis = SmolTooltipArea.delay
                        ) {
                            IconButton(
                                onClick = { showVramRefreshWarning = true },
                                modifier = Modifier
                                    .padding(start = 6.dp)
                                    .size(20.dp)
                                    .align(Alignment.CenterVertically)
                            ) {
                                Icon(
                                    painter = painterResource("refresh.svg"),
                                    contentDescription = null
                                )
                            }
                        }
                    }

                    Text(
                        text = "Game Version",
                        modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.width(checkboxesWidth).align(Alignment.CenterVertically)
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
                            stickyHeader {
                                Card(
                                    elevation = 8.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            top = 8.dp,
                                            bottom = 8.dp,
                                            start = contentPadding,
                                            end = contentPadding
                                        )
                                ) {
                                    Row(modifier = Modifier.mouseClickable {
                                        if (this.buttons.isPrimaryPressed) {
                                            isCollapsed.value = isCollapsed.value.not()
                                        }
                                    }) {
                                        val arrowAngle by animateFloatAsState(if (isCollapsed.value) -90f else 0f)
                                        Icon(
                                            modifier = Modifier
                                                .align(Alignment.CenterVertically)
                                                .padding(start = 4.dp)
                                                .rotate(arrowAngle),
                                            imageVector = Icons.Outlined.ArrowDropDown,
                                            contentDescription = null,
                                        )
                                        Text(
                                            text = when (modState) {
                                                true -> "Enabled (${modsInGroup.count()})"
                                                false -> "Disabled (${modsInGroup.count()})"
                                            },
                                            color = MaterialTheme.colors.onSurface,
                                            modifier = Modifier
                                                .padding(8.dp),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
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
                                                            ModGridSortField.VramImpact -> SL.vramChecker.vramUsage.value?.get(
                                                                modRow.mod.findFirstEnabledOrHighestVersion?.smolId
                                                            )?.bytesForMod
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
                                    val mod = modRow.mod
                                    var showContextMenu by remember { mutableStateOf(false) }
                                    val highestLocalVersion =
                                        mod.findHighestVersion?.versionCheckerInfo?.modVersion
                                    val onlineVersionInfo = SL.versionChecker.getOnlineVersion(modId = mod.id)
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
                                                                Icons.Default.Favorite
                                                            else Icons.Default.FavoriteBorder),
                                                            contentDescription = null,
                                                            modifier = Modifier
                                                                .padding(end = 16.dp)
                                                                .mouseClickable {
                                                                    SL.userManager.setModFavorited(
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
                                                Text(
                                                    modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                                                    text = (mod.findFirstEnabled
                                                        ?: mod.findHighestVersion)?.modInfo?.name
                                                        ?: "",
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    fontFamily = SmolTheme.orbitronSpaceFont,
                                                    fontSize = 14.sp
                                                )

                                                // Mod Author
                                                Text(
                                                    text = (mod.findFirstEnabledOrHighestVersion)?.modInfo?.author
                                                        ?: "",
                                                    color = SmolTheme.dimmedTextColor(),
                                                    modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                // Mod version (active or highest)
                                                Row(Modifier.weight(1f).align(Alignment.CenterVertically)) {
                                                    // Update badge icon
                                                    if (highestLocalVersion != null && onlineVersion != null && onlineVersion > highestLocalVersion) {
                                                        val ddUrl = onlineVersionInfo.directDownloadURL?.ifBlank { null }
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
                                                                    colorFilter = ColorFilter.tint(color = MaterialTheme.colors.secondary),
                                                                    modifier = Modifier.width(28.dp).height(28.dp)
                                                                        .padding(end = 8.dp)
                                                                        .align(Alignment.CenterVertically)
                                                                        .pointerHoverIcon(
                                                                            PointerIcon(
                                                                                Cursor.getPredefinedCursor(
                                                                                    Cursor.HAND_CURSOR
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
                                                                        append("\n\nNo mod thread provided. Click to search on Google.")
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
                                                                painter = painterResource("new-box.svg"),
                                                                contentDescription = null,
                                                                colorFilter = ColorFilter.tint(
                                                                    color =
                                                                    if (ddUrl == null) MaterialTheme.colors.secondary
                                                                    else MaterialTheme.colors.secondary.copy(alpha = ContentAlpha.disabled)
                                                                ),
                                                                modifier = Modifier.width(28.dp).height(28.dp)
                                                                    .padding(end = 8.dp)
                                                                    .align(Alignment.CenterVertically)
                                                                    .pointerHoverIcon(
                                                                        PointerIcon(
                                                                            Cursor.getPredefinedCursor(
                                                                                Cursor.HAND_CURSOR
                                                                            )
                                                                        )
                                                                    )
                                                            )
                                                        }
                                                    }
                                                    // Versions discovered
                                                    Text(
                                                        text = mod.variants
                                                            .joinToString() { it.modInfo.version.toString() },
                                                        modifier = Modifier.align(Alignment.CenterVertically),
                                                        color = SmolTheme.dimmedTextColor()
                                                    )
                                                }

                                                // VRAM
                                                Row(Modifier.weight(1f).align(Alignment.CenterVertically)) {
                                                    vramBar(mod, largestVramUsage)
                                                }

                                                // Game version (for active or highest)
                                                Row(Modifier.weight(1f).align(Alignment.CenterVertically)) {
                                                    Text(
                                                        text = (mod.findFirstEnabled
                                                            ?: mod.findHighestVersion)?.modInfo?.gameVersion ?: "",
                                                        modifier = Modifier.align(Alignment.CenterVertically),
                                                        color = SmolTheme.dimmedTextColor()
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

        if (showVramRefreshWarning) {
            SmolAlertDialog(
                onDismissRequest = { showVramRefreshWarning = false },
                confirmButton = {
                    SmolButton(onClick = {
                        showVramRefreshWarning = false
                        GlobalScope.launch {
                            SL.vramChecker.refreshVramUsage(mods = mods.toList().filterNotNull())
                        }
                    }) { Text("Calculate") }
                },
                dismissButton = {
                    SmolSecondaryButton(onClick = {
                        showVramRefreshWarning = false
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