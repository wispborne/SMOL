package views

import AppState
import SL
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import model.Mod
import model.ModState

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
                Text("Name", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("Version", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            }
        }
        Box {
            LazyColumn(Modifier.fillMaxWidth()) {
                mods
                    .groupBy { shouldShowAsEnabled(it) }
                    .forEach { (isEnabled, mods) ->

                        stickyHeader {
                            val menuItems = if (isEnabled)
                                listOf("Enabled", "Disable")
                            else
                                listOf("Disabled", "Enable")
                            var expanded by remember { mutableStateOf(false) }
                            var selectedIndex by remember { mutableStateOf(0) }
                            Text(
                                menuItems.first(),
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { expanded = true }
                                    .background(MaterialTheme.colors.background),
                                fontWeight = FontWeight.Bold
                            )
                            DropdownMenu(
                                expanded = expanded,
                                modifier = Modifier.background(MaterialTheme.colors.background),
                                onDismissRequest = { expanded = false }
                            ) {
                                menuItems.forEachIndexed { index, title ->
                                    DropdownMenuItem(onClick = {
                                        selectedIndex = index
                                        expanded = false
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
                        this.items(mods) { mod ->
                            ListItem(Modifier.clickable {
                            }) {
                                Row {
                                    modStateDropdown(modifier = Modifier.weight(.5f), mod = mod)
                                    Text(mod.modVersions.values.first().modInfo.name, Modifier.weight(1f))
                                    Text(mod.modVersions.values.first().modInfo.version.toString(), Modifier.weight(1f))
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
    val menuItems = if (shouldShowAsEnabled(mod))
        listOf("Enabled", "Disable")
    else
        listOf("Disabled", "Enable")
    var expanded by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf(0) }
    Box(modifier) {
        Text(
            menuItems.first(),
            modifier = modifier.wrapContentWidth()
                .clickable { expanded = true }
                .background(
                    when (mod.state) {
                        ModState.Enabled -> MaterialTheme.colors.primary
                        ModState.Disabled -> MaterialTheme.colors.primaryVariant
                        ModState.Uninstalled -> MaterialTheme.colors.onBackground
                    }
                )
                .padding(8.dp),
            fontWeight = FontWeight.Bold
        )
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

private fun shouldShowAsEnabled(mod: Mod) = mod.isEnabledInGame && mod.modsFolderInfo != null
