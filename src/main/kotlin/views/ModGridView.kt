package views

import AppState
import SL
import SmolButton
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import model.Mod
import model.ModState
import smolFullyClippedButtonShape

private val buttonWidth = 150

@OptIn(
    ExperimentalMaterialApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
@Preview
fun AppState.ModGridView(
    mods: SnapshotStateList<Mod>,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
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
                    .groupBy { shouldShowAsEnabled(it) }
                    .forEach { (isEnabled, mods) ->
                        stickyHeader() {
                            Card(
                                elevation = 8.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                Row {
                                    Icon(
                                        modifier = Modifier
                                            .align(Alignment.CenterVertically)
                                            .padding(start = 4.dp),
                                        imageVector = Icons.Outlined.ArrowDropDown,
                                        contentDescription = null,
                                    )
//                                    Image(
//                                        modifier = modifier
//                                            .padding(start = 4.dp, top = 4.dp)
//                                            .width(16.dp),
//                                        contentDescription = null
//                                    )
//                                    dropdownArrow(Modifier.align(Alignment.CenterVertically))
                                    Text(
                                        text = if (isEnabled) "Enabled" else "Disabled",
                                        modifier = Modifier
                                            .padding(8.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        this.items(mods) { mod ->
                            ListItem(Modifier
                                .fillMaxWidth()
                                .clickable { }) {
                                Row(Modifier.fillMaxWidth()) {
                                    modStateDropdown(
                                        modifier = Modifier
                                            .width(buttonWidth.dp)
                                            .align(Alignment.CenterVertically),
                                        mod = mod
                                    )
                                    Text(
                                        mod.modVersions.values.first().modInfo.name,
                                        Modifier.weight(1f).align(Alignment.CenterVertically)
                                    )
                                    Text(
                                        mod.modVersions.values.first().modInfo.version.toString(),
                                        Modifier.weight(1f).align(Alignment.CenterVertically)
                                    )
                                }
                            }
                        }
                    }
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
                    ModState.Uninstalled -> MaterialTheme.colors.onBackground
                }
            )
        ) {
            Text(
                text = mod.state.name,
                fontWeight = FontWeight.Bold
            )
            dropdownArrow(Modifier.align(Alignment.CenterVertically))
        }
        DropdownMenu(
            expanded = expanded,
            modifier = Modifier.background(
                MaterialTheme.colors.background
            ),
            onDismissRequest = { expanded = false }
        ) {
            menuItems.forEachIndexed { index, title ->
                val coroutineScope = rememberCoroutineScope()
                DropdownMenuItem(onClick = {
                    selectedIndex = index
                    expanded = false

                    // Change mod state
                    coroutineScope.launch {
                        kotlin.runCatching {
                            if (shouldShowAsEnabled(mod)) {
                                SL.staging.disable(mod.modVersions.values.first { it.isEnabledInSmol })
                            } else {
                                SL.staging.enable(mod.modVersions.values.first { !it.isEnabledInSmol })
                            }
                        }
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

@Composable
private fun dropdownArrow(modifier: Modifier) {
    Image(
        modifier = modifier
            .padding(start = 4.dp, top = 4.dp)
            .width(16.dp),
        painter = painterResource("arrow_down.png"),
        contentDescription = null
    )
}

private fun shouldShowAsEnabled(mod: Mod) = mod.isEnabledInGame && mod.modsFolderInfo != null
