package views

import AppState
import SL
import SmolAlertDialog
import SmolButton
import TiledImage
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
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import model.Mod
import model.ModVariant
import org.tinylog.Logger
import smolFullyClippedButtonShape
import util.*
import java.awt.Desktop

private val buttonWidth = 180

@OptIn(
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class, ExperimentalUnitApi::class, ExperimentalDesktopApi::class,
    ExperimentalComposeUiApi::class
)
@Composable
@Preview
fun AppState.ModGridView(
    modifier: Modifier = Modifier,
    mods: SnapshotStateList<Mod>
) {
    var selectedRow: ModRow? by remember { mutableStateOf(null) }
    val state = rememberLazyListState()
    var modInDebugDialog: Mod? by remember { mutableStateOf(null) }

    Box(modifier) {
        TiledImage(
            modifier = Modifier.background(Color.Gray.copy(alpha = .1f)),
            imageBitmap = imageResource("panel00_center.png")
        )
        Column(Modifier.padding(16.dp)) {
            ListItem() {
                Row {
                    Spacer(Modifier.width(buttonWidth.dp))
                    Text("Name", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Version", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                }
            }
            Box {
                LazyColumn(Modifier.fillMaxWidth()) {
                    mods
                        .groupBy { it.uiEnabled }
                        .toSortedMap(compareBy { !it }) // Flip to put Enabled at the top
                        .forEach { (modState, mods) ->
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
                                                true -> "Enabled"
                                                false -> "Disabled"
                                            },
                                            modifier = Modifier
                                                .padding(8.dp),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            this.items(
                                items = mods.map { ModRow(mod = it) }
                            ) { modRow ->
                                val mod = modRow.mod
                                var showContextMenu by remember { mutableStateOf(false) }
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
                                    Row(Modifier.fillMaxWidth()) {
                                        modStateDropdown(
                                            modifier = Modifier
                                                .width(buttonWidth.dp)
                                                .align(Alignment.CenterVertically),
                                            mod = mod
                                        )
                                        // Mod name
                                        Text(
                                            text = (mod.findFirstEnabled ?: mod.findFirstDisabled)?.modInfo?.name ?: "",
                                            modifier = Modifier.weight(1f).align(Alignment.CenterVertically)
                                        )
                                        // Mod version (active or highest)
                                        Text(
                                            text = mod.variants
                                                .joinToString() { it.modInfo.version.toString() },
                                            modifier = Modifier.weight(1f).align(Alignment.CenterVertically)
                                        )
                                        CursorDropdownMenu(
                                            expanded = showContextMenu,
                                            onDismissRequest = { showContextMenu = false }) {
                                            DropdownMenuItem(onClick = {
                                                kotlin.runCatching {
                                                    (mod.findFirstEnabled
                                                        ?: mod.findFirstDisabled)?.archiveInfo?.folder?.also {
                                                        Desktop.getDesktop().open(it.parentFile)
                                                    }
                                                }
                                                    .onFailure { Logger.warn(it) { "Error trying to open file browser for $mod." } }
                                                showContextMenu = false
                                            }) {
                                                Text("Open Archive")
                                            }
                                            val modThreadId = mod.getModThreadId()
                                            if (modThreadId != null) {
                                                DropdownMenuItem(
                                                    onClick = {
                                                        modThreadId.openModThread()
                                                        showContextMenu = false
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
                                                modInDebugDialog = mod
                                                showContextMenu = false
                                            }) {
                                                Text("Debug Info")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                }

                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(
                        scrollState = state
                    )
                )
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
                shape = smolFullyClippedButtonShape(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = when (mod.state) {
                        ModState.Enabled -> MaterialTheme.colors.primary
                        else -> MaterialTheme.colors.primaryVariant
                    }
                )
            ) {
                // Text of the dropdown menu, current state of the mod
                if (mod.enabledVariants.size > 1) {
                    BoxWithTooltip(tooltip = {
                        Text(
                            modifier = Modifier.background(MaterialTheme.colors.background).padding(8.dp),
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
                    .border(1.dp, MaterialTheme.colors.primary, shape = smolFullyClippedButtonShape()),
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
                                                SL.staging.changeActiveVariant(mod, action.variant)
                                            }
                                            is DropdownAction.Disable -> {
                                                SL.staging.disable(firstEnabledVariant ?: return@runCatching)
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
                                    is DropdownAction.MigrateMod -> "Migrate to $APP_NAME"
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
        val backgroundColor: Color,
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
            SmolButton(
                onClick = { expanded = true },
                modifier = Modifier.wrapContentWidth()
                    .align(Alignment.CenterStart),
                shape = smolFullyClippedButtonShape(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = selectedItem.backgroundColor
                )
            ) {
                Text(
                    text = selectedItem.text,
                    fontWeight = FontWeight.Bold
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
                        modifier = Modifier.background(color = item.backgroundColor),
                        onClick = {
                            selectedIndex = index
                            expanded = false
                            items[index].onClick()
                        }) {
                        Row {
                            Text(
                                text = item.text,
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold
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