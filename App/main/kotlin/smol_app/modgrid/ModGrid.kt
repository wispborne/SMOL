package smol_app.modgrid

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
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
import kotlinx.coroutines.withContext
import org.tinylog.Logger
import smol_access.Constants
import smol_access.SL
import smol_access.business.Dependencies
import smol_access.model.Mod
import smol_access.model.ModVariant
import smol_access.model.UserProfile
import smol_app.AppState
import smol_app.composables.*
import smol_app.themes.SmolTheme
import smol_app.util.*
import smol_app.views.detailsPanel
import java.awt.Cursor
import java.awt.Desktop
import kotlin.io.path.exists

private val modGridViewDropdownWidth = 180

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
    var selectedRow: ModRow? by remember { mutableStateOf(null) }
    var modInDebugDialog: Mod? by remember { mutableStateOf(null) }
    val largestVramUsage = SL.vramChecker.vramUsage.value?.values?.maxOf { it.bytesForMod }
    val profile = SL.userManager.activeProfile.collectAsState()
    val activeSortField = profile.value.modGridPrefs.sortField?.let {
        kotlin.runCatching { ModGridSortField.valueOf(it) }.getOrNull()
    }
    val contentPadding = 16.dp
    val favoritesWidth = 40.dp

    Box(modifier.padding(top = contentPadding, bottom = contentPadding)) {
        Column(Modifier) {
            ListItem(modifier = Modifier.padding(start = contentPadding, end = contentPadding)) {
                Row {
                    Spacer(Modifier.width(modGridViewDropdownWidth.dp))

                    Row(Modifier.weight(1f)) {
                        Text("Name", fontWeight = FontWeight.Bold)
                        columnSortArrow(ModGridSortField.Name, activeSortField, profile)
                    }

                    Row(Modifier.weight(1f)) {
                        val columnSortField = ModGridSortField.Author
                        Text("Author", fontWeight = FontWeight.Bold)
                        columnSortArrow(ModGridSortField.Author, activeSortField, profile)
                    }

                    Spacer(modifier = Modifier.width(favoritesWidth))
                    SmolTooltipArea(modifier = Modifier.weight(1f),
                        tooltip = { SmolTooltipText(text = "The version(s) tracked by SMOL.") }) {
                        Text(text = "Version(s)", fontWeight = FontWeight.Bold)
                    }

                    SmolTooltipArea(modifier = Modifier.weight(1f),
                        tooltip = {
                            SmolTooltipText(
                                text = "An estimate of how much VRAM the mod will use." +
                                        "\nAll images are counted, even if not used by the game."
                            )
                        }) {
                        Row {
                            Text(text = "VRAM Impact", fontWeight = FontWeight.Bold)
                            Icon(
                                modifier = Modifier.padding(start = 8.dp).width(12.dp).height(12.dp)
                                    .align(Alignment.Top),
                                painter = painterResource("more_info.png"),
                                contentDescription = null
                            )
                            columnSortArrow(ModGridSortField.VramImpact, activeSortField, profile)
                        }
                    }

                    Text("Game Version", Modifier.weight(1f), fontWeight = FontWeight.Bold)
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
                                                        ModGridSortField.Name -> modRow.mod.findFirstEnabledOrHighestVersion?.modInfo?.name
                                                        ModGridSortField.Author -> modRow.mod.findFirstEnabledOrHighestVersion?.modInfo?.author
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
                                var isHighlighted by remember { mutableStateOf(false) }

                                ListItem(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .mouseClickable {
                                            if (this.buttons.isPrimaryPressed) {
                                                selectedRow =
                                                    (if (selectedRow === modRow) null else modRow)
                                            } else if (this.buttons.isSecondaryPressed) {
                                                showContextMenu = !showContextMenu
                                            }
                                        }
                                        .background(
                                            color = if (isHighlighted || selectedRow?.mod?.id == mod.id)
                                                Color.Black.copy(alpha = .1f)
                                            else Color.Transparent
                                        )
                                        .pointerMoveFilter(
                                            onEnter = {
                                                isHighlighted = true
                                                false
                                            },
                                            onExit = {
                                                isHighlighted = false
                                                false
                                            })
                                ) {
                                    Column(modifier = Modifier.padding(start = contentPadding, end = contentPadding)) {
                                        Row(Modifier.fillMaxWidth()) {
                                            modStateDropdown(
                                                modifier = Modifier
                                                    .width(modGridViewDropdownWidth.dp)
                                                    .align(Alignment.CenterVertically),
                                                mod = mod
                                            )

                                            // Mod name
                                            Text(
                                                modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                                                text = (mod.findFirstEnabled ?: mod.findHighestVersion)?.modInfo?.name
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

                                            // Favorites
                                            Row(
                                                modifier = Modifier
                                                    .width(favoritesWidth)
                                                    .align(Alignment.CenterVertically),
                                            ) {
                                                val isFavorited = mod.id in profile.value.favoriteMods
                                                if (isFavorited || isHighlighted) {
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
                                                        tint = MaterialTheme.colors.onSurface.copy(ContentAlpha.medium)
                                                    )
                                                }
                                            }
                                            // Mod version (active or highest)
                                            Row(Modifier.weight(1f).align(Alignment.CenterVertically)) {
                                                if (highestLocalVersion != null && onlineVersion != null && onlineVersion > highestLocalVersion) {
                                                    val modThreadId =
                                                        mod.findHighestVersion?.versionCheckerInfo?.modThreadId
                                                    SmolTooltipArea(tooltip = {
                                                        SmolTooltipText(
                                                            text = "Newer version available: $onlineVersion" +
                                                                    "\n\nClick to open ${modThreadId?.getModThreadUrl()}."
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
                mods = SL.access.mods.value ?: emptyList()
            )
        }

        if (modInDebugDialog != null) {
            debugDialog(mod = modInDebugDialog!!, onDismiss = { modInDebugDialog = null })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.columnSortArrow(
    columnSortField: ModGridSortField,
    sortField: ModGridSortField?,
    profile: State<UserProfile>
) {
    Box(
        modifier = Modifier
            .align(Alignment.CenterVertically)
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
            }
    ) {
        SmolDropdownArrow(
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp, end = 12.dp),
            expanded = sortField == columnSortField && profile.value.modGridPrefs.isSortDescending
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ModContextMenu(
    showContextMenu: Boolean,
    onShowContextMenuChange: (Boolean) -> Unit,
    mod: Mod,
    modInDebugDialog: Mod?,
    onModInDebugDialogChanged: (Mod?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    CursorDropdownMenu(
        expanded = showContextMenu,
        onDismissRequest = { onShowContextMenuChange(false) }) {
        val modsFolder = (mod.findFirstEnabled
            ?: mod.findFirstDisabled)?.modsFolderInfo?.folder
        if (modsFolder?.exists() == true) {
            DropdownMenuItem(onClick = {
                kotlin.runCatching {
                    modsFolder.also {
                        Desktop.getDesktop().open(it.toFile())
                    }
                }
                    .onFailure { Logger.warn(it) { "Error trying to open file browser for $mod." } }
                onShowContextMenuChange(false)
            }) {
                Text("Open Folder")
            }
        }

        val archiveFolder = (mod.findFirstEnabled
            ?: mod.findFirstDisabled)?.archiveInfo?.folder
        if (archiveFolder?.exists() == true) {
            DropdownMenuItem(onClick = {
                kotlin.runCatching {
                    archiveFolder.also {
                        Desktop.getDesktop().open(it.toFile())
                    }
                }
                    .onFailure { Logger.warn(it) { "Error trying to open file browser for $mod." } }
                onShowContextMenuChange(false)
            }) {
                Text("Open Archive")
            }
        }

        val modThreadId = mod.getModThreadId()
        if (modThreadId != null) {
            DropdownMenuItem(
                onClick = {
                    modThreadId.openModThread()
                    onShowContextMenuChange(false)
                },
                modifier = Modifier.width(200.dp)
            ) {
                Image(
                    painter = painterResource("open-in-new.svg"),
                    colorFilter = ColorFilter.tint(MaterialTheme.colors.onSurface),
                    modifier = Modifier.padding(end = 8.dp),
                    contentDescription = null
                )
                Text(
                    text = "Forum Page",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }

        DropdownMenuItem(onClick = {
            coroutineScope.launch {
                withContext(Dispatchers.Default) {
                    SL.vramChecker.refreshVramUsage(mods = listOf(mod))
                }
            }
            onShowContextMenuChange(false)
        }) {
            Text("Check VRAM")
        }

        DropdownMenuItem(onClick = {
            onModInDebugDialogChanged(mod)
            onShowContextMenuChange(false)
        }) {
            Text("Debug Info")
        }
    }
}

@Composable
private fun DependencyFixerRow(
    mod: Mod,
    allMods: List<Mod>
) {
    val dependencies =
        (mod.findFirstEnabled ?: mod.findHighestVersion)
            ?.run { SL.dependencies.findDependencyStates(modVariant = this, mods = allMods) }
            ?.sortedWith(compareByDescending { it is Dependencies.DependencyState.Disabled })
            ?: emptyList()
    dependencies
        .filter { it is Dependencies.DependencyState.Missing || it is Dependencies.DependencyState.Disabled }
        .forEach { depState ->
            Row(Modifier.padding(start = 16.dp)) {
                Image(
                    painter = painterResource("beacon_med.png"),
                    modifier = Modifier
                        .width(38.dp)
                        .height(28.dp)
                        .padding(end = 8.dp)
                        .align(Alignment.CenterVertically),
                    contentDescription = null
                )
                Text(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    text = when (depState) {
                        is Dependencies.DependencyState.Disabled -> "Disabled dependency: ${depState.variant.modInfo.name} ${depState.variant.modInfo.version}"
                        is Dependencies.DependencyState.Missing -> "Missing dependency: ${depState.dependency.name?.ifBlank { null } ?: depState.dependency.id}${depState.dependency.version?.let { " $it" }}"
                        is Dependencies.DependencyState.Enabled -> "you should never see this"
                    }
                )
                SmolButton(
                    modifier = Modifier.align(Alignment.CenterVertically).padding(start = 16.dp),
                    onClick = {
                        when (depState) {
                            is Dependencies.DependencyState.Disabled -> GlobalScope.launch {
                                SL.access.enableModVariant(
                                    depState.variant
                                )
                            }
                            is Dependencies.DependencyState.Missing -> {
                                GlobalScope.launch {
                                    depState.outdatedModIfFound?.getModThreadId()?.openModThread()
                                        ?: "https://google.com/search?q=starsector+${depState.dependency.name ?: depState.dependency.id}+${depState.dependency.versionString}"
                                            .openAsUriInBrowser()
                                }
                            }
                            is Dependencies.DependencyState.Enabled -> TODO("you should never see this")
                        }
                    }
                ) {
                    Text(
                        text = when (depState) {
                            is Dependencies.DependencyState.Disabled -> "Enable"
                            is Dependencies.DependencyState.Missing -> "Search"
                            is Dependencies.DependencyState.Enabled -> "you should never see this"
                        }
                    )
                    if (depState is Dependencies.DependencyState.Missing) {
                        Image(
                            painter = painterResource("open-in-new.svg"),
                            colorFilter = ColorFilter.tint(SmolTheme.dimmedIconColor()),
                            modifier = Modifier.padding(start = 8.dp),
                            contentDescription = null
                        )
                    }
                }
            }
        }
}

private sealed class DropdownAction {
    data class ChangeToVariant(val variant: ModVariant) : DropdownAction()
    object Disable : DropdownAction()
    data class MigrateMod(val mod: Mod) : DropdownAction()
    data class ResetToArchive(val variant: ModVariant) : DropdownAction()
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun modStateDropdown(modifier: Modifier = Modifier, mod: Mod) {
    val firstEnabledVariant = mod.findFirstEnabled

    /**
     * Disable: at least one variant enabled
     * Switch to variant: other variant (in /mods, staging, or archives)
     * Reinstall: has archive
     * Snapshot (bring up dialog asking which variants to snapshot): at least one variant without archive
     */
    val dropdownMenuItems: List<DropdownAction> = mutableListOf<DropdownAction>()
        .run {
            val otherVariantsThanEnabled = mod.variants
                .filter { variant ->
                    firstEnabledVariant == null
                            || mod.enabledVariants.any { enabledVariant -> enabledVariant.smolId != variant.smolId }
                }

            if (otherVariantsThanEnabled.any()) {
                val otherVariants = otherVariantsThanEnabled
                    .map { DropdownAction.ChangeToVariant(variant = it) }
                this.addAll(otherVariants)
            }

            if (firstEnabledVariant != null) {
                this.add(DropdownAction.Disable)
            }

            // If the enabled variant has an archive, they can reset the state back to the archived state.
            if (firstEnabledVariant?.archiveInfo != null) {
                this.add(DropdownAction.ResetToArchive(firstEnabledVariant))
            }

            this
        }


    var expanded by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf(0) }
    Box(modifier) {
        Box(Modifier.width(IntrinsicSize.Min)) {
            SmolButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .align(Alignment.CenterStart),
                shape = SmolTheme.smolFullyClippedButtonShape(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = when (mod.state) {
                        ModState.Enabled -> MaterialTheme.colors.primary
                        else -> MaterialTheme.colors.primaryVariant
                    }
                )
            ) {
                // Text of the dropdown menu, current state of the mod
                if (mod.enabledVariants.size > 1) {
                    SmolTooltipArea(tooltip = {
                        SmolTooltipText(
                            text = "Warning: ${mod.enabledVariants.size} versions of " +
                                    "${mod.findHighestVersion!!.modInfo.name} in the mods folder." +
                                    " Remove one."
                        )
                    }) {
                        Image(
                            painter = painterResource("beacon_med.png"),
                            modifier = Modifier.width(38.dp).height(28.dp).padding(end = 8.dp),
                            contentDescription = null
                        )
                    }
                }
                Text(
                    text = when {
                        // If there is an enabled variant, show its version string.
                        mod.enabledVariants.isNotEmpty() -> mod.enabledVariants.joinToString { it.modInfo.version.toString() }
                        // If no enabled variant, show "Disabled"
                        else -> "Disabled"
                    },
                    fontWeight = FontWeight.Bold
                )
                SmolDropdownArrow(
                    Modifier.align(Alignment.CenterVertically),
                    expanded
                )
            }
            DropdownMenu(
                expanded = expanded,
                modifier = Modifier
                    .background(MaterialTheme.colors.background)
                    .border(1.dp, MaterialTheme.colors.primary, shape = SmolTheme.smolFullyClippedButtonShape()),
                onDismissRequest = { expanded = false }
            ) {
                val coroutineScope = rememberCoroutineScope()
                dropdownMenuItems.forEachIndexed { index, action ->
                    Box {
                        var background: Color? by remember { mutableStateOf(null) }
//                        val highlightColor = MaterialTheme.colors.surface
                        DropdownMenuItem(
                            modifier = Modifier.sizeIn(maxWidth = 400.dp)
                                .background(background ?: MaterialTheme.colors.background)
//                                .pointerMoveFilter( // doesn't work: https://github.com/JetBrains/compose-jb/issues/819
//                                    onEnter = {
//                                        Logger.debug { "Entered dropdown item" }
//                                        background = highlightColor;true
//                                    },
//                                    onExit = {
//                                        Logger.debug { "Exited dropdown item" }
//                                        background = null;true
//                                    }
                            ,
                            onClick = {
                                selectedIndex = index
                                expanded = false
                                Logger.debug { "Selected $action." }

                                // Don't use composition scope, we don't want
                                // it to cancel an operation due to a UI recomposition.
                                // A two-step operation will trigger a mod refresh and therefore recomposition and cancel
                                // the second part of the operation!
                                GlobalScope.launch {
                                    kotlin.runCatching {
                                        // Change mod state
                                        when (action) {
                                            is DropdownAction.ChangeToVariant -> {
                                                SL.access.changeActiveVariant(mod, action.variant)
                                            }
                                            is DropdownAction.Disable -> {
                                                SL.access.disableModVariant(firstEnabledVariant ?: return@runCatching)
                                            }
                                            is DropdownAction.MigrateMod -> {
                                                // TODO
//                                                SL.archives.compressModsInFolder(
//                                                    mod.modsFolderInfo?.folder ?: return@runCatching
//                                                )
                                            }
                                            is DropdownAction.ResetToArchive -> {
                                                // TODO
                                            }
                                        }
                                    }
                                        .onFailure { Logger.error(it) }
                                }
                            }) {
                            Text(
                                text = when (action) {
                                    is DropdownAction.ChangeToVariant -> action.variant.modInfo.version.toString()
                                    is DropdownAction.Disable -> "Disable"
                                    is DropdownAction.MigrateMod -> "Migrate to ${Constants.APP_NAME}"
                                    is DropdownAction.ResetToArchive -> "Reset to default"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun debugDialog(
    modifier: Modifier = Modifier,
    mod: Mod,
    onDismiss: () -> Unit
) {
    SmolAlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) { Text("Ok") }
        },
        text = {
            SelectionContainer {
                Text(text = mod.toString())
            }
        }
    )
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

private enum class Fields {
    StateDropdown,
    Name,
    Versions,
    Authors
}