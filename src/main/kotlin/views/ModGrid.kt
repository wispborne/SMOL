import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

@OptIn(
    ExperimentalMaterialApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
@Preview
fun ModGrid(
    mods: List<Mod>,
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
                    .groupBy { it.isEnabled }
                    .forEach { (isEnabled, mods) ->
                        val menuItems = if (isEnabled)
                            listOf("Enabled", "Disable")
                        else
                            listOf("Disabled", "Enable")

                        stickyHeader {
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
                            ListItem {
                                Row {
                                    Text(mod.modInfo.name, Modifier.weight(1f))
                                    Text(mod.modInfo.version.toString(), Modifier.weight(1f))
                                }
                            }
                        }
                    }
            }
        }
    }
}
