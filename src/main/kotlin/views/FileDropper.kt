package views

import AppState
import SL
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.tinylog.Logger
import java.awt.dnd.*
import java.io.File


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AppState.FileDropper(
    modifier: Modifier = Modifier
) {
    val acceptableActions = DnDConstants.ACTION_COPY_OR_MOVE
    var fileBeingHovered: File? by remember { mutableStateOf(null) }
    var isHovering: Boolean by remember { mutableStateOf(false) }
    var lastDragSeen by remember { mutableStateOf(System.currentTimeMillis()) }
    var initialized by remember { mutableStateOf(false) }
    var error: Throwable? by remember { mutableStateOf(null) }

    if (!initialized) {
        val listener = object : DropTarget() {
            override fun dragEnter(dtde: DropTargetDragEvent?) {
                super.dragEnter(dtde)
                if (isHovering) return

                dtde ?: kotlin.run {
                    fileBeingHovered = null
                    if (isHovering) isHovering = false
                    Logger.debug { "Rejected drag." }
                    return
                }

                kotlin.runCatching {
                    val droppedFiles =
                        dtde.transferable.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor) as List<*>

                    droppedFiles.firstOrNull()?.let {
                        val file = (it as File)
                        Logger.debug { "Accepted drag." }
                        fileBeingHovered = file
                        lastDragSeen = System.currentTimeMillis()
                        if (!isHovering) isHovering = true
                        return
                    }
                }
                    .onFailure { Logger.error(it) }
//
                fileBeingHovered = null
                isHovering = false
            }

            @Synchronized
            override fun drop(evt: DropTargetDropEvent) {
                evt.acceptDrop(evt.dropAction)
                val droppedFiles =
                    evt.transferable.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor) as List<*>

                droppedFiles.firstOrNull()?.let {
                    val name = (it as File).absolutePath

                    kotlin.runCatching {
                        SL.archives.archiveModsInFolder(it)
                    }
                        .onFailure { throwable -> error = throwable }
                }
            }

            override fun dragOver(dtde: DropTargetDragEvent?) {
                dragEnter(dtde)
            }

            override fun dragExit(dte: DropTargetEvent?) {
                super.dragExit(dte)
                isHovering = false
            }
        }

        window.contentPane.dropTarget = listener
        initialized = true
    }

    if (isHovering) {
        Box(
            modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = ContentAlpha.medium))
        ) {
            val file = fileBeingHovered ?: return@Box
            Card(
                Modifier
                    .align(Alignment.Center)
                    .width(300.dp)
                    .height(300.dp),
                shape = CutCornerShape(8.dp),
                elevation = 8.dp,
                backgroundColor = Color.White.copy(alpha = ContentAlpha.high)
            ) {
                Box(Modifier.padding(16.dp)) {
                    Text(text = file.name, modifier = modifier)
                }
            }

            if (error != null) {
                AlertDialog(
                    onDismissRequest = { error = null },
                    confirmButton = {
                        Button(onClick = { error = null }) {
                            Text("OK, sorry")
                        }
                    },
                    title = { Text("Error") },
                    text = { Text("Unable to install.\n${error?.message}") }
                )
            }
        }
    }
}