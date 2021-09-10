import androidx.compose.desktop.DesktopMaterialTheme
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.io.RandomAccessFile


fun main() = application {
    try {
        SevenZip.initSevenZipFromPlatformJAR()
    } catch (e: Exception) {
        throw e
    }

    Window(onCloseRequest = ::exitApplication) {
        App()
        Dropper(window = window)
    }
}

@OptIn(ExperimentalStdlibApi::class)
@Composable
@Preview
fun App() {
    var text by remember { mutableStateOf("Hello, World!") }

    DesktopMaterialTheme(colors = DarkColors) {
        Scaffold(topBar = { TopAppBar { } }) {
            Column(
                Modifier.padding(16.dp)
            ) {
                Button(onClick = {
                    text = "Hello, Desktop!"
                }) {
                    Text(text)
                }
                ModsGrid(
                    Loader.getMods()
//                    buildList(30) {
//                        repeat(30) { this.add(Mod(ModInfo(it.toString(), "$it.5.2"))) }
//                    }
                )
            }
        }
    }
}

@OptIn(
    ExperimentalMaterialApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
@Composable
@Preview
fun ModsGrid(
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
                        val items = if (isEnabled)
                            listOf("Enabled", "Disable")
                        else
                            listOf("Disabled", "Enable")

                        stickyHeader {
                            var expanded by remember { mutableStateOf(false) }
                            var selectedIndex by remember { mutableStateOf(0) }
                            Text(
                                items.first(),
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
                                items.forEachIndexed { index, title ->
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
                        items(mods) { mod ->
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

@Composable
fun Dropper(
    modifier: Modifier = Modifier,
    window: ComposeWindow
) {
    var name by remember { mutableStateOf("") }
    val target = object : DropTarget() {
        override fun dragOver(dtde: DropTargetDragEvent?) {
            super.dragOver(dtde)
        }

        @Synchronized
        override fun drop(evt: DropTargetDropEvent) {
            evt.acceptDrop(DnDConstants.ACTION_REFERENCE)
            val droppedFiles = evt.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>

            droppedFiles.first()?.let {
                name = (it as File).absolutePath

                SevenZip.openInArchive(null, RandomAccessFileInStream(RandomAccessFile(it, "r")))
                    .numberOfItems
                    .also { println("Files in $name: $it") }
            }
        }
    }
    window.contentPane.dropTarget = target

    Text(text = name, modifier = modifier)
}