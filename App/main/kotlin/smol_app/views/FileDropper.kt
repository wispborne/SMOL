package smol_app.views

import AppState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.tinylog.Logger
import smol_access.SL
import smol_app.composables.SmolAlertDialog
import smol_app.composables.SmolButton
import smol_app.composables.dashedBorder
import smol_app.themes.SmolTheme
import smol_app.util.bytesAsShortReadableMiB
import smol_app.util.parseHtml
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.io.File
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.name


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AppState.FileDropper(
    modifier: Modifier = Modifier
) {
    var fileBeingHovered: Path? by remember { mutableStateOf(null) }
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
                        val file = (it as File).toPath()
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
                    .align(Alignment.Center),
                shape = SmolTheme.smolFullyClippedButtonShape(),
                elevation = 8.dp,
                backgroundColor = MaterialTheme.colors.primarySurface
            ) {
                Box(
                    Modifier.padding(16.dp)
                        .dashedBorder(
                            width = 3.dp,
                            color = MaterialTheme.colors.onPrimary.copy(alpha = ContentAlpha.medium),
                            on = 12.dp,
                            off = 12.dp,
                            shape = RoundedCornerShape(4.dp)
                        )
                ) {
                    Box(Modifier.padding(start = 120.dp, end = 120.dp, bottom = 64.dp, top = 32.dp)) {
                        Column(modifier = Modifier.align(Alignment.TopCenter)) {
                            Image(
                                painter = painterResource("icon-new-folder.svg"),
                                modifier = Modifier
                                    .size(80.dp)
                                    .align(Alignment.CenterHorizontally)
                                    .padding(bottom = 8.dp),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(color = MaterialTheme.colors.onPrimary)
                            )
                            Text(
                                text = "Add to Starsector",
                                fontSize = 19.sp,
                                fontFamily = SmolTheme.orbitronSpaceFont,
                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 32.dp)
                            )
                            Text(
                                text = "<b><code>${file.name}</code></b>".parseHtml(),
                                fontSize = 19.sp,
                                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
                            )
                            Text(
                                text = "<code>${file.fileSize().bytesAsShortReadableMiB}</code>".parseHtml(),
                                fontSize = 16.sp,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }
            }
        }
    }

    if (error != null) {
        SmolAlertDialog(
            title = { Text("Unable to install", style = SmolTheme.alertDialogTitle()) },
            text = { Text("${error?.message}", style = SmolTheme.alertDialogBody()) },
            confirmButton = {
                SmolButton(onClick = { error = null }) {
                    Text("OK, sorry")
                }
            },
            onDismissRequest = { error = null }
        )
    }
}