package views

import AppState
import SL
import SmolButton
import SmolTheme
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import model.Mod
import model.ModState
import org.tinylog.Logger
import smolFullyClippedButtonShape
import util.prefer

private val buttonWidth = 180

@OptIn(
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class, ExperimentalUnitApi::class, ExperimentalDesktopApi::class,
    ExperimentalComposeUiApi::class
)
@Composable
@Preview
fun AppState.ModGridView(
    mods: SnapshotStateList<Mod>,
    modifier: Modifier = Modifier
) {
    var selectedRow: ModRow? by remember { mutableStateOf(null) }

    Box(modifier) {
        Column(Modifier.padding(16.dp)) {
            ListItem(Modifier.background(MaterialTheme.colors.background)) {
                Row {
                    Spacer(Modifier.width(buttonWidth.dp))
                    Text("Name", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Version", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                }
            }
            Box {
                LazyColumn(Modifier.fillMaxWidth()) {
                    mods
                        .groupBy { it.state }
                        .toSortedMap(compareBy { it.ordinal })
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
                                                ModState.Enabled -> "Enabled"
                                                ModState.Disabled -> "Disabled"
                                                ModState.Uninstalled -> "Uninstalled"
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
                                        Text(
                                            mod.variants.values.first().modInfo.name,
                                            Modifier.weight(1f).align(Alignment.CenterVertically)
                                        )
                                        Text(
                                            mod.variants.values.first().modInfo.version.toString(),
                                            Modifier.weight(1f).align(Alignment.CenterVertically)
                                        )
                                        CursorDropdownMenu(expanded = showContextMenu,
                                            onDismissRequest = { showContextMenu = false }) {
                                            DropdownMenuItem(onClick = {}) {
                                                Text("test")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                }
            }
        }

        if (selectedRow != null) {
            detailsPanel(modifier, selectedRow)
        }
    }
}

@OptIn(ExperimentalUnitApi::class)
@Composable
private fun BoxScope.detailsPanel(
    modifier: Modifier = Modifier,
    selectedRow: ModRow?
) {
    run {
        val row = selectedRow ?: return@run
        Card(
            modifier.width(400.dp)
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
        ) {
            Column(
                Modifier
                    .padding(16.dp)
            ) {
                val modInfo = (row.mod.findFirstEnabled ?: row.mod.variants.values.firstOrNull())
                    ?.modInfo
                Text(
                    modInfo?.name ?: "VNSector",
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = SmolTheme.orbitronSpaceFont,
                    fontSize = TextUnit(18f, TextUnitType.Sp)
                )
                Text(
                    modInfo?.id ?: "vnsector",
                    modifier = Modifier.padding(top = 4.dp),
                    fontSize = TextUnit(12f, TextUnitType.Sp),
                    fontFamily = SmolTheme.fireCodeFont
                )
                Text("Author(s)", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                Text(modInfo?.author ?: "It's always Techpriest", modifier = Modifier.padding(top = 2.dp))
                Text("Version", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                Text(modInfo?.version?.toString() ?: "no version", modifier = Modifier.padding(top = 2.dp))
                Text("Description", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                Text(modInfo?.description ?: "", modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun modStateDropdown(modifier: Modifier = Modifier, mod: Mod) {
    val menuItems = ModState.values().filter { it != mod.state }.map { it.name }

    var expanded by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf(0) }
    Box(modifier) {
        SmolButton(
            onClick = { expanded = true },
            modifier = Modifier.wrapContentWidth()
                .align(Alignment.CenterStart),
            shape = smolFullyClippedButtonShape(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = when (mod.state) {
                    ModState.Enabled -> MaterialTheme.colors.primary
                    ModState.Disabled -> MaterialTheme.colors.primaryVariant
                    ModState.Uninstalled -> MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                        .compositeOver(MaterialTheme.colors.surface)
                }
            )
        ) {
            Text(
                text = mod.state.name,
                fontWeight = FontWeight.Bold
            )
            dropdownArrow(
                Modifier.align(Alignment.CenterVertically),
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
            val coroutineScope = rememberCoroutineScope()
            menuItems.forEachIndexed { index, title ->
                DropdownMenuItem(onClick = {
                    selectedIndex = index
                    expanded = false
                    Logger.debug { "Selected $title." }

                    coroutineScope.launch {
                        kotlin.runCatching {
                            // Change mod state
                            when (ModState.valueOf(menuItems[index])) {
                                ModState.Enabled ->
                                    SL.staging.enable(mod.variants.values.prefer { !mod.isEnabled(it) }.first())
                                ModState.Disabled ->
                                    SL.staging.disable(mod.variants.values.prefer { mod.isEnabled(it) }.first())
                                ModState.Uninstalled -> SL.staging.uninstall(mod)
                            }
                        }
                            .onFailure { Logger.error(it) }
                    }
                }) {
                    Row {
                        Text(
                            text = title,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
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

private fun shouldShowAsEnabled(mod: Mod) = mod.isEnabledInGame && mod.modsFolderInfo != null

data class ModRow(
    val mod: Mod
)

private enum class Fields {
    StateDropdown,
    Name,
    Versions,
    Authors
}