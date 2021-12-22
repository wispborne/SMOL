@file:OptIn(ExperimentalAnimationApi::class)

package smol_app.home

import androidx.compose.animation.ExperimentalAnimationApi
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import smol_access.SL
import smol_access.model.Mod
import smol_access.model.ModId
import smol_access.model.UserProfile
import smol_app.AppState
import smol_app.UI
import smol_app.composables.*
import smol_app.themes.SmolTheme
import smol_app.util.*
import smol_app.views.detailsPanel
import utilities.parallelMap
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
    var selectedRow: ModRow? by remember { mutableStateOf(null) }
    val checkedRows = remember { mutableStateListOf<ModId>() }
    var modInDebugDialog: Mod? by remember { mutableStateOf(null) }
    val largestVramUsage = SL.vramChecker.vramUsage.value?.values?.maxOf { it.bytesForMod }
    val profile = SL.userManager.activeProfile.collectAsState()
    val activeSortField = profile.value.modGridPrefs.sortField?.let {
        kotlin.runCatching { ModGridSortField.valueOf(it) }.getOrNull()
    }

    Box(modifier.padding(top = contentPadding, bottom = contentPadding)) {
        Column(Modifier) {
            ListItem(modifier = Modifier.padding(start = contentPadding, end = contentPadding)) {
                Row {
                    Spacer(modifier = Modifier.width(favoritesWidth).align(Alignment.CenterVertically))
                    Spacer(Modifier.width(modGridViewDropdownWidth.dp).align(Alignment.CenterVertically))

                    SortableHeader(
                        modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                        columnSortField = ModGridSortField.Name,
                        sortField = activeSortField,
                        profile = profile
                    ) {
                        Text("Name", fontWeight = FontWeight.Bold)
                    }

                    SortableHeader(
                        modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                        columnSortField = ModGridSortField.Author,
                        sortField = activeSortField,
                        profile = profile
                    ) {
                        Text("Author", fontWeight = FontWeight.Bold)
                    }

                    SmolTooltipArea(modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                        tooltip = { SmolTooltipText(text = "The version(s) tracked by SMOL.") }) {
                        Text(text = "Version(s)", fontWeight = FontWeight.Bold)
                    }

                    SmolTooltipArea(modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                        tooltip = {
                            SmolTooltipText(
                                text = "An estimate of how much VRAM the mod will use." +
                                        "\nAll images are counted, even if not used by the game."
                            )
                        }) {
                        SortableHeader(
                            modifier = Modifier.weight(1f),
                            columnSortField = ModGridSortField.VramImpact,
                            sortField = activeSortField,
                            profile = profile
                        ) {
                            Text(text = "VRAM Impact", fontWeight = FontWeight.Bold)
                            Icon(
                                modifier = Modifier.padding(start = 8.dp).width(12.dp).height(12.dp)
                                    .align(Alignment.Top),
                                painter = painterResource("more_info.png"),
                                contentDescription = null
                            )
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
                            checked = mods.all { mod -> mod != null && mod.id in checkedRows },
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    checkedRows.replaceAllUsingDifference(
                                        mods.mapNotNull { it?.id },
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
                LazyColumn(Modifier.fillMaxWidth()) {
                    mods
                        .filterNotNull()
                        .groupBy { it.uiEnabled }
                        .toSortedMap(compareBy { !it }) // Flip to put Enabled at the top
                        .forEach { (modState, modsInGroup) ->
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
                                    Row {
                                        Icon(
                                            modifier = Modifier
                                                .align(Alignment.CenterVertically)
                                                .padding(start = 4.dp),
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
                                val onlineVersion = SL.versionChecker.getOnlineVersion(modId = mod.id)
                                var isRowHighlighted by remember { mutableStateOf(false) }

                                ListItem(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .mouseClickable {
                                            if (this.buttons.isPrimaryPressed) {
                                                // If in "Checking rows" mode, clicking a row toggles checked.
                                                // Otherwise, it open/closes Details panel
                                                if (checkedRows.any()) {
                                                    if (mod.id !in checkedRows) {
                                                        checkedRows.add(mod.id)
                                                    } else {
                                                        checkedRows.remove(mod.id)
                                                    }
                                                } else {
                                                    selectedRow =
                                                        (if (selectedRow === modRow) null else modRow)
                                                }
                                            } else if (this.buttons.isSecondaryPressed) {
                                                showContextMenu = !showContextMenu
                                            }
                                        }
                                        .background(
                                            color = if (isRowHighlighted || selectedRow?.mod?.id == mod.id)
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
                                                overflow = TextOverflow.Ellipsis
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
                                                    val ddUrl =
                                                        mod.findHighestVersion?.versionCheckerInfo?.directDownloadURL
                                                    if (ddUrl != null) {
                                                        SmolTooltipArea(tooltip = {
                                                            SmolTooltipText(
                                                                text = buildString {
                                                                    append("Newer version available: $onlineVersion")
                                                                    append("\n\nClick to download and update.")
                                                                }
                                                            )
                                                        }, modifier = Modifier.mouseClickable {
                                                            if (this.buttons.isPrimaryPressed) {
                                                                SL.UI.downloadManager.downloadFromUrl(
                                                                    url = ddUrl,
                                                                    shouldInstallAfter = true
                                                                )
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
                                                    SmolTooltipArea(tooltip = {
                                                        SmolTooltipText(
                                                            text = buildString {
                                                                append("Newer version available: $onlineVersion")
                                                                if (ddUrl == null) append("This mod does not support direct download and should be downloaded manually.")
                                                                append("\n\nClick to open ${modThreadId?.getModThreadUrl()}.")
                                                            }
                                                        )
                                                    }, modifier = Modifier.mouseClickable {
                                                        if (this.buttons.isPrimaryPressed) {
                                                            modThreadId?.openModThread()
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
                                                val vramResult =
                                                    SL.vramChecker.vramUsage.value?.get(mod.findHighestVersion?.smolId)
                                                SmolTooltipArea(tooltip = {
                                                    if (vramResult != null) {
                                                        val impactText =
                                                            vramResult.bytesForMod.bytesAsReadableMiB
                                                                .let { if (vramResult.bytesForMod == 0L) "No impact" else it }
                                                        SmolTooltipText(
                                                            text = buildString {
                                                                appendLine("Version: ${vramResult.version}")
                                                                appendLine(impactText)
                                                                append("${vramResult.imageCount} images")
                                                            }
                                                        )
                                                    }
                                                }) {
                                                    // VRAM relative size bar
                                                    if (vramResult?.bytesForMod != null && vramResult.bytesForMod > 0 && largestVramUsage != null) {
                                                        val widthWeight =
                                                            (vramResult.bytesForMod.toFloat() / largestVramUsage)
                                                                .coerceIn(0.01f, 0.99f)
                                                        Row(Modifier.height(28.dp)) {
                                                            Box(
                                                                Modifier
                                                                    .background(
                                                                        color = MaterialTheme.colors.primary,
                                                                        shape = SmolTheme.smolNormalButtonShape()
                                                                    )
                                                                    .weight(widthWeight)
                                                                    .height(8.dp)
                                                                    .align(Alignment.Bottom)
                                                            )
                                                            Spacer(Modifier.weight(1f - widthWeight))
                                                        }
                                                    }
                                                    Text(
                                                        text =
                                                        vramResult?.bytesForMod?.bytesAsReadableMiB
                                                            ?.let { if (vramResult.bytesForMod == 0L) "None" else it }
                                                            ?: "Unavailable",
                                                        modifier = Modifier.fillMaxSize()
                                                            .align(Alignment.CenterVertically),
                                                        color = SmolTheme.dimmedTextColor()
                                                    )
                                                }
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
                                            val isChecked = mod.id in checkedRows

                                            val isCheckboxVisible =
                                                isRowHighlighted || isChecked || checkedRows.any()

                                            Row(
                                                modifier = Modifier.width(checkboxesWidth)
                                                    .align(Alignment.CenterVertically)
                                                    .alpha(if (isCheckboxVisible) 1f else 0f)
                                            ) {
                                                modGridBulkActionMenu(checkedRows = checkedRows)
                                                Checkbox(
                                                    modifier = Modifier.width(checkboxesWidth),
                                                    checked = isChecked,
                                                    onCheckedChange = { checked ->
                                                        if (checked) {
                                                            checkedRows.add(mod.id)
                                                        } else {
                                                            checkedRows.remove(mod.id)
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
                                                onModInDebugDialogChanged = { modInDebugDialog = it })
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

                // Bugged in Compose: java.lang.IllegalArgumentException: Index should be non-negative (-1)
//                VerticalScrollbar(
//                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
//                    adapter = rememberScrollbarAdapter(
//                        scrollState = state
//                    )
//                )
            }
        }

        if (selectedRow != null) {
            detailsPanel(
                modifier = Modifier.padding(bottom = contentPadding),
                selectedRow = selectedRow,
                mods = SL.access.mods.value ?: emptyList(),
                closePanel = { selectedRow = null }
            )
        }

        if (modInDebugDialog != null) {
            debugDialog(mod = modInDebugDialog!!, onDismiss = { modInDebugDialog = null })
        }
    }
}

@Composable
private fun modGridBulkActionMenu(modifier: Modifier = Modifier, checkedRows: SnapshotStateList<ModId>) {
    SmolPopupMenu(
        modifier = modifier
            .padding(end = 8.dp)
            .alpha(if (checkedRows.any()) 1f else 0f),
        items = listOf(SmolDropdownMenuItemTemplate(
            text = "Disable All",
            onClick = {
                GlobalScope.launch(Dispatchers.IO) {
                    checkedRows
                        .mapNotNull { modId -> SL.access.mods.value?.firstOrNull { it.id == modId } }
                        .parallelMap { SL.access.disableMod(it) }
                }
            }
        ))
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.SortableHeader(
    modifier: Modifier = Modifier,
    columnSortField: ModGridSortField,
    sortField: ModGridSortField?,
    profile: State<UserProfile>,
    content: @Composable (() -> Unit)?
) {
    Row(modifier
        .mouseClickable {
            SL.userManager.updateUserProfile {
                it.copy(
                    modGridPrefs = it.modGridPrefs.copy(
                        sortField = columnSortField.name,
                        isSortDescending = if (sortField == columnSortField) {
                            profile.value.modGridPrefs.isSortDescending.not()
                        } else {
                            true
                        }
                    )
                )
            }
        }) {
        content?.invoke()
        Box(
            modifier = Modifier
                .align(Alignment.CenterVertically)
        ) {
            SmolDropdownArrow(
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
                expanded = sortField == columnSortField && profile.value.modGridPrefs.isSortDescending
            )
        }
    }
}

@Preview
@Composable
fun previewModGrid() {
    AppState()
        .ModGridView(Modifier, SnapshotStateList())
}

data class ModRow(
    val mod: Mod
)