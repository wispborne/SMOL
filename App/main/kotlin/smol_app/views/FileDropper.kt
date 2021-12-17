package smol_app.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.Card
import androidx.compose.material.ContentAlpha
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.tinylog.Logger
import smol_access.SL
import smol_app.AppState
import smol_app.composables.SmolAlertDialog
import smol_app.composables.SmolButton
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.io.File


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AppState.FileDropper(
    modifier: Modifier = Modifier
) {
    var fileBeingHovered: File? by remember { mutableStateOf(null) }
    var isHovering: Boolean by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }
    var error: Throwable? by remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope()

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
                isHovering = false
                val droppedFiles =
                    evt.transferable.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor) as List<*>

                droppedFiles.firstOrNull()?.let {
                    scope.launch {
                        kotlin.runCatching {
                            SL.access.installFromUnknownSource(
                                inputFile = (it as File).toPath(),
                                shouldCompressModFolder = true
                            )
                        }
                            .onFailure { throwable ->
                                error = throwable
                            }
                    }
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
        @Suppress("UNUSED_VALUE")
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
        }
    }

    if (error != null) {
        SmolAlertDialog(
            title = { Text("Unable to install") },
            text = { Text("${error?.message}") },
            confirmButton = {
                SmolButton(onClick = { error = null }) {
                    Text("OK, sorry")
                }
            },
            onDismissRequest = { error = null }
        )
    }
}