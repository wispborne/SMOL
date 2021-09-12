import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.arkivanov.decompose.Router
import navigation.Screen
import navigation.rememberRouter
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import org.tinylog.Logger
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

    SL.appConfig.stagingPath = File(System.getProperty("user.home"), "SMOL").absolutePath
    SL.installer.loadManifest()
        .also { Logger.debug { "Staging manifest: ${it?.manifestItems?.keys?.joinToString()}" } }
    SL.installer.addModsFolderToStagingFolder()

    Window(onCloseRequest = ::exitApplication) {
        val router = rememberRouter<Screen>(
            initialConfiguration = { Screen.Home },
            handleBackButton = true
        )

        val appState by remember { mutableStateOf(AppState(router, window)) }

        appState.AppView()
        appState.Dropper()
    }
}


class AppState(
    val router: Router<Screen, Any>,
    val window: ComposeWindow
) {

}

@Composable
fun AppState.Dropper(
    modifier: Modifier = Modifier
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