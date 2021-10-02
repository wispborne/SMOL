package views

import AppState
import SL
import SmolButton
import TiledImage
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
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import model.Mod
import model.ModState
import model.ModVariant
import org.tinylog.Logger
import smolFullyClippedButtonShape
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

    Box(modifier) {
        TiledImage(
            modifier = Modifier.background(Color.Gray.copy(alpha = .1f)),
            imageBitmap = imageResource("panel00_center.png")
        )
        Column(Modifier.padding(16.dp)) {
            ListItem(Modifier.background(MaterialTheme.colors.background.copy(alpha = ContentAlpha.medium))) {
                Row {
                    Spacer(Modifier.width(buttonWidth.dp))
                    Text("Name", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Version", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                }
            }
            Box {
                LazyColumn(Modifier.fillMaxWidth()) {
                    mods
                        .groupBy { it.findFirstEnabled != null }
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
                                        Text(
                                            text = (mod.findFirstEnabled ?: mod.findFirstDisabled)?.modInfo?.name ?: "",
                                            modifier = Modifier.weight(1f).align(Alignment.CenterVertically)
                                        )
                                        Text(
                                            text = (mod.findFirstEnabled ?: mod.findFirstDisabled)?.modInfo?.version.toString() ?: "",
                                            modifier = Modifier.weight(1f).align(Alignment.CenterVertically)
                                        )
                                        CursorDropdownMenu(expanded = showContextMenu,
                                            onDismissRequest = { showContextMenu = false }) {
                                            DropdownMenuItem(onClick = {
                                                kotlin.runCatching {
                                                    (mod.findFirstEnabled
                                                        ?: mod.findFirstDisabled)?.archiveInfo?.folder?.also {
                                                        Desktop.getDesktop().open(it.parentFile)
                                                    }
                                                }
                                                    .onFailure { Logger.warn(it) { "Error trying to open file browser for $mod." } }
                                            }) {
                                                Text("Open Archive")
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
            detailsPanel(selectedRow = selectedRow)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun modStateDropdown(modifier: Modifier = Modifier, mod: Mod) {
    val menuItems = mod.variants.values
        .asSequence()
        .filter { it != mod.findFirstEnabled }
        .map { it.modInfo.version.toString() to it as ModVariant? }
        .toMutableList()
        .run {
            if (mod.findFirstEnabled != null)
                this.add("Disable" to null)
            this
        }
        .toList()

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
                Text(
                    text = mod.findFirstEnabled?.modInfo?.version?.toString() ?: ModState.Disabled.name,
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
                menuItems.forEachIndexed { index, labelAndVariant ->
                    DropdownMenuItem(
                        modifier = Modifier.sizeIn(maxWidth = 400.dp),
                        onClick = {
                            selectedIndex = index
                            expanded = false
                            Logger.debug { "Selected $labelAndVariant." }

                            coroutineScope.launch {
                                kotlin.runCatching {
                                    // Change mod state
                                    if (labelAndVariant.second != null) {
                                        SL.staging.changeActiveVariant(mod, labelAndVariant.second)
                                    } else {
                                        SL.staging.disable(mod.findFirstEnabled ?: return@runCatching)
                                    }
                                }
                                    .onFailure { Logger.error(it) }
                            }
                        }) {
                        Text(
                            text = labelAndVariant.first,
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