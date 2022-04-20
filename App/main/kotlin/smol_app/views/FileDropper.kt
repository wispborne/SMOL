/*
 * This file is distributed under the GPLv3. An informal description follows:
 * - Anyone can copy, modify and distribute this software as long as the other points are followed.
 * - You must include the license and copyright notice with each and every distribution.
 * - You may this software for commercial purposes.
 * - If you modify it, you must indicate changes made to the code.
 * - Any modifications of this code base MUST be distributed with the same license, GPLv3.
 * - This software is provided without warranty.
 * - The software author or license can not be held liable for any damages inflicted by the software.
 * The full license is available from <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package smol_app.views

import AppScope
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
import com.arkivanov.decompose.replaceCurrent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tinylog.Logger
import smol_access.SL
import smol_app.composables.SmolAlertDialog
import smol_app.composables.SmolButton
import smol_app.composables.dashedBorder
import smol_app.navigation.Screen
import smol_app.themes.SmolTheme
import smol_app.util.parseHtml
import timber.ktx.Timber
import utilities.bytesAsShortReadableMB
import utilities.calculateFileSize
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.pathString


@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AppScope.FileDropper(
    modifier: Modifier = Modifier
) {
    var fileBeingHovered: Drop? by remember { mutableStateOf(null) }
    var isHovering: Boolean by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }
    var error: Throwable? by remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope()

    if (!initialized) {
        val listener = object : DropTarget() {
            override fun dragEnter(event: DropTargetDragEvent?) {
                super.dragEnter(event)
                if (isHovering) return

                event ?: kotlin.run {
                    fileBeingHovered = null
                    if (isHovering) isHovering = false
                    Logger.debug { "Rejected drag." }
                    return
                }

                kotlin.runCatching {
                    val droppedFiles =
                        event.transferable.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor) as List<*>
                    val url = kotlin.runCatching {
                        event.transferable.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor) as String
                    }
                        .onFailure { Timber.d(it) }
                        .recover { (droppedFiles.firstOrNull() as? File)?.name ?: "" }
                        .getOrElse { "" }

                    droppedFiles.firstOrNull()?.let {
                        val file = (it as File).toPath()
                        Logger.debug { "Accepted drag." }
                        fileBeingHovered = Drop(path = file, name = url)
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
            override fun drop(event: DropTargetDropEvent) {
                event.acceptDrop(event.dropAction)
                isHovering = false
                val droppedFiles =
                    event.transferable.getTransferData(java.awt.datatransfer.DataFlavor.javaFileListFlavor) as List<*>
                val url =
                    kotlin.runCatching { event.transferable.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor) as String }
                        .onFailure { Timber.d(it) }
                        .getOrNull()

                droppedFiles.firstOrNull()?.let { filePath ->
                    scope.launch {
                        kotlin.runCatching {
                            val path = (filePath as File).toPath()

                            if (path.pathString.endsWith(".url")) {
                                Timber.i { "User file dropped url '$url'." }
                                router.replaceCurrent(Screen.ModBrowser(url))

                            } else {
                                val destinationFolder = SL.gamePathManager.getModsPath()

                                if (destinationFolder != null) {
                                    withContext(Dispatchers.IO) {
                                        SL.access.installFromUnknownSource(
                                            inputFile = path,
                                            destinationFolder = destinationFolder,
                                            promptUserToReplaceExistingFolder = { duplicateModAlertDialogState.showDialogBooleo(it)}
                                        )
                                    }
                                    SL.access.reload()
                                }
                            }
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
            var fileSize by remember { mutableStateOf<Long?>(null) }

            LaunchedEffect(file.path.absolutePathString()) {
                withContext(Dispatchers.Default) {
                    kotlin.runCatching {
                        fileSize = file.path.calculateFileSize()
                    }
                        .onFailure { Timber.w(it) }
                }
            }

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
                                text = "<code>${
                                    if (fileSize == null)
                                        "calculating..."
                                    else fileSize?.bytesAsShortReadableMB
                                }</code>".parseHtml(),
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

private data class Drop(
    val path: Path,
    val name: String
)