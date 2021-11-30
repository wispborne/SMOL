package smol_app.views

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.tinylog.Logger
import smol_access.Constants
import smol_access.SL
import smol_access.business.DependencyState
import smol_access.business.findDependencyStates
import smol_access.model.Mod
import smol_access.model.ModVariant
import smol_app.AppState
import smol_app.themes.*
import smol_app.util.*
import java.awt.Desktop
import kotlin.io.path.exists

private val modGridViewDropdownWidth = 180

@OptIn(
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class, ExperimentalUnitApi::class,
    ExperimentalComposeUiApi::class
)
@Composable
@Preview
fun AppState.ModGridView(
    modifier: Modifier = Modifier,
    mods: SnapshotStateList<Mod?>
) {
    var selectedRow: ModRow? by remember { mutableStateOf(null) }
    var modInDebugDialog: Mod? by remember { mutableStateOf(null) }
    val largestVramUsage = SL.vramChecker.cached?.values?.maxOf { it.bytesForMod }

    Box(modifier) {
        TiledImage(
            modifier = Modifier.background(Color.Gray.copy(alpha = .1f)),
            imageBitmap = imageResource("panel00_center.png")
        )
        Column(Modifier.padding(16.dp)) {
            ListItem() {
                Row {
                    Spacer(Modifier.width(modGridViewDropdownWidth.dp))
                    Text("Name", Modifier.weight(1f), fontWeight = FontWeight.Bold)

                    Text("Author", Modifier.weight(1f), fontWeight = FontWeight.Bold)

                    TooltipArea(modifier = Modifier.weight(1f),
                        tooltip = { SmolTooltipText(text = "The version(s) tracked by SMOL.") }) {
                        Text(text = "Version", fontWeight = FontWeight.Bold)
                    }

                    TooltipArea(modifier = Modifier.weight(1f),
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
                        }
                    }
                }
            }
            Box {
                val state = rememberLazyListState()

                LazyColumn(Modifier.fillMaxWidth()) {
                    mods
                        .filterNotNull()
                        .groupBy { it.uiEnabled }
                        .toSortedMap(compareBy { !it }) // Flip to put Enabled at the top
                        .forEach { (modState, modsInGroup) ->
                            stickyHeader() {
                                Card(
                                    elevation = 8.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, bottom = 8.dp)
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
                                            modifier = Modifier
                                                .padding(8.dp),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            this.items(
                                items = modsInGroup.map { ModRow(mod = it) }
                            ) { modRow ->
                                val mod = modRow.mod
                                var showContextMenu by remember { mutableStateOf(false) }
                                val highestLocalVersion =
                                    mod.findHighestVersion?.versionCheckerInfo?.modVersion
                                val onlineVersion = SL.versionChecker.getOnlineVersion(modId = mod.id)

                                ListItem(Modifier
                                    .fillMaxWidth()
                                    .mouseClickable {
                                        if (this.buttons.isPrimaryPressed) {
                                            selectedRow =
                                                (if (selectedRow === modRow) null else modRow)
                                        } else if (this.buttons.isSecondaryPressed) {
                                            showContextMenu = !showContextMenu
                                        }
                                    }) {
                                    Column {
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
                                                text = (mod.findFirstEnabled ?: mod.findHighestVersion)?.modInfo?.author
                                                    ?: "",
                                                color = SmolTheme.dimmedTextColor(),
                                                modifier = Modifier.weight(1f).align(Alignment.CenterVertically),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            // Mod version (active or highest)
                                            Row(Modifier.weight(1f).align(Alignment.CenterVertically)) {
                                                if (highestLocalVersion != null && onlineVersion != null && onlineVersion > highestLocalVersion) {
                                                    TooltipArea(tooltip = {
                                                        SmolTooltipText(text = "Newer version available: $onlineVersion")
                                                    }, modifier = Modifier.mouseClickable {
                                                        if (this.buttons.isPrimaryPressed) {
                                                            mod.findHighestVersion?.versionCheckerInfo?.modThreadId?.openModThread()
                                                        }
                                                    }
                                                        .align(Alignment.CenterVertically)) {
                                                        Image(
                                                            painter = painterResource("new-box.svg"),
                                                            contentDescription = null,
                                                            colorFilter = ColorFilter.tint(SmolTheme.dimmedIconColor()),
                                                            modifier = Modifier.width(28.dp).height(28.dp)
                                                                .padding(end = 8.dp)
                                                                .align(Alignment.CenterVertically)
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
                                                    SL.vramChecker.cached?.get(mod.findHighestVersion?.smolId)
                                                TooltipArea(tooltip = {
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
            detailsPanel(selectedRow = selectedRow)
        }

        if (modInDebugDialog != null) {
            debugDialog(mod = modInDebugDialog!!, onDismiss = { modInDebugDialog = null })
        }
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
            ?.findDependencyStates(allMods)
            ?.sortedWith(compareByDescending { it is DependencyState.Disabled })
            ?: emptyList()
    dependencies
        .filter { it is DependencyState.Missing || it is DependencyState.Disabled }
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
                        is DependencyState.Disabled -> "Disabled dependency: ${depState.variant.modInfo.name} ${depState.variant.modInfo.version}"
                        is DependencyState.Missing -> "Missing dependency: ${depState.dependency.name?.ifBlank { null } ?: depState.dependency.id}${depState.dependency.version?.let { " $it" }}"
                        is DependencyState.Enabled -> "you should never see this"
                    }
                )
                SmolButton(
                    modifier = Modifier.align(Alignment.CenterVertically).padding(start = 16.dp),
                    onClick = {
                        when (depState) {
                            is DependencyState.Disabled -> GlobalScope.launch { SL.access.enable(depState.variant) }
                            is DependencyState.Missing -> {
                                GlobalScope.launch {
                                    depState.outdatedModIfFound?.getModThreadId()?.openModThread()
                                        ?: "https://google.com/search?q=starsector+${depState.dependency.name ?: depState.dependency.id}+${depState.dependency.versionString}"
                                            .openAsUriInBrowser()
                                }
                            }
                            is DependencyState.Enabled -> TODO("you should never see this")
                        }
                    }
                ) {
                    Text(
                        text = when (depState) {
                            is DependencyState.Disabled -> "Enable"
                            is DependencyState.Missing -> "Search"
                            is DependencyState.Enabled -> "you should never see this"
                        }
                    )
                    if (depState is DependencyState.Missing) {
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
                    TooltipArea(tooltip = {
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
                dropdownArrow(
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

                                coroutineScope.launch {
                                    kotlin.runCatching {
                                        // Change mod state
                                        when (action) {
                                            is DropdownAction.ChangeToVariant -> {
                                                SL.access.changeActiveVariant(mod, action.variant)
                                            }
                                            is DropdownAction.Disable -> {
                                                SL.access.disable(firstEnabledVariant ?: return@runCatching)
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

object SmolDropdown {
    data class DropdownMenuItem(
        val text: String,
        val backgroundColor: Color?,
        val contentColor: Color?,
        val onClick: () -> Unit
    )

    @Composable
    fun DropdownWithButton(
        modifier: Modifier = Modifier,
        items: List<DropdownMenuItem>,
        initiallySelectedIndex: Int
    ) {
        var expanded by remember { mutableStateOf(false) }
        var selectedIndex by remember { mutableStateOf(initiallySelectedIndex) }
        Box(modifier) {
            val selectedItem = items[selectedIndex]
            val backgroundColor = selectedItem.backgroundColor ?: MaterialTheme.colors.primary
            SmolButton(
                onClick = { expanded = true },
                modifier = Modifier.wrapContentWidth()
                    .align(Alignment.CenterStart),
                shape = SmolTheme.smolFullyClippedButtonShape(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = backgroundColor
                )
            ) {
                Text(
                    text = selectedItem.text,
                    fontWeight = FontWeight.Bold,
                    color = selectedItem.contentColor ?: contentColorFor(backgroundColor)
                )
                dropdownArrow(
                    Modifier
                        .align(Alignment.CenterVertically)
                        .run { if (expanded) this.rotate(90f) else this },
                    expanded
                )
            }
            DropdownMenu(
                expanded = expanded,
                modifier = Modifier.background(
                    MaterialTheme.colors.background
                ),
                onDismissRequest = { expanded = false }
            ) {
                items.forEachIndexed { index, item ->
                    DropdownMenuItem(
                        modifier = Modifier.let { if (item.backgroundColor != null) it.background(item.backgroundColor) else it },
                        onClick = {
                            selectedIndex = index
                            expanded = false
                            items[index].onClick()
                        }) {
                        Row {
                            Text(
                                text = item.text,
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold,
                                color = item.contentColor ?: contentColorFor(
                                    item.backgroundColor ?: MaterialTheme.colors.surface
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun dropdownArrow(modifier: Modifier, expanded: Boolean) {
    Image(
        modifier = modifier
            .width(18.dp)
            .run {
                if (expanded)
                    this.rotate(180f)
                        .padding(end = 8.dp)
                else
                    this.padding(start = 8.dp)
            },
        painter = painterResource("arrow_down.png"),
        contentDescription = null
    )
}

//private fun shouldShowAsEnabled(mod: Mod) = mod.isEnabledInGame && mod.modsFolderInfo != null

data class ModRow(
    val mod: Mod
)

private enum class Fields {
    StateDropdown,
    Name,
    Versions,
    Authors
}