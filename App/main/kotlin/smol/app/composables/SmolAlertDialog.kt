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

package smol.app.composables

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import smol.app.util.onEnterKeyPressed
import java.awt.event.KeyEvent

/**
 * Alert dialog is a Dialog which interrupts the user with urgent information, details or actions.
 *
 * The dialog will position its buttons based on the available space. By default it will try to
 * place them horizontally next to each other and fallback to horizontal placement if not enough
 * space is available. There is also another version of this composable that has a slot for buttons
 * to provide custom buttons layout.
 *
 * Sample of dialog:
 * @sample androidx.compose.material.samples.AlertDialogSample
 *
 * @param onDismissRequest Callback that will be called when the user closes the dialog.
 * @param confirmButton A button which is meant to confirm a proposed action, thus resolving
 * what triggered the dialog. The dialog does not set up any events for this button so they need
 * to be set up by the caller.
 * @param modifier Modifier to be applied to the layout of the dialog.
 * @param dismissButton A button which is meant to dismiss the dialog. The dialog does not set up
 * any events for this button so they need to be set up by the caller.
 * @param title The title of the Dialog which should specify the purpose of the Dialog. The title
 * is not mandatory, because there may be sufficient information inside the [text]. Provided text
 * style will be [Typography.subtitle1].
 * @param text The text which presents the details regarding the Dialog's purpose. Provided text
 * style will be [Typography.body2].
 * @param shape Defines the Dialog's shape
 * @param backgroundColor The background color of the dialog.
 * @param contentColor The preferred content color provided by this dialog to its children.
 * @param dialogProvider Defines how to create dialog in which will be placed AlertDialog's content.
 */
@Composable
@ExperimentalMaterialApi
fun SmolAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit = { SmolButton(onClick = { onDismissRequest() }) { Text("Ok") } },
    underlayModifier: Modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight()
        .background(Color.Black.copy(alpha = ContentAlpha.medium)),
    modifier: Modifier = Modifier.width(400.dp)
        .onEnterKeyPressed { onDismissRequest(); true },
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    backgroundColor: Color = MaterialTheme.colors.surface,
    contentColor: Color = contentColorFor(backgroundColor),
    dialogProvider: AlertDialogProvider = PopupAlertDialogProvider
) {
    Box(
        modifier = underlayModifier
    ) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier
                .clickable(enabled = false) {}, // Don't dismiss the dialog when you click on it!,
            dismissButton = dismissButton,
            title = title,
            text = text,
            shape = shape,
            backgroundColor = backgroundColor,
            contentColor = contentColor,
            dialogProvider = dialogProvider
        )
    }
}

/**
 * Shows Alert dialog as popup in the middle of the window.
 */
@ExperimentalMaterialApi
class SmolAlertDialogProvider(val boundsModifier: Modifier = Modifier.fillMaxSize()) : AlertDialogProvider {
    @Composable
    override fun AlertDialog(
        onDismissRequest: () -> Unit,
        content: @Composable () -> Unit
    ) {
        Popup(
            popupPositionProvider = object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize
                ): IntOffset = IntOffset.Zero
            },
            focusable = true,
            onDismissRequest = onDismissRequest,
            onKeyEvent = {
                if (it.awtEventOrNull?.keyCode == KeyEvent.VK_ESCAPE) {
                    onDismissRequest()
                    true
                } else {
                    false
                }
            },
        ) {
            Box(
                modifier = boundsModifier
                    .pointerInput(onDismissRequest) {
                        detectTapGestures(onPress = { onDismissRequest() })
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(elevation = 24.dp) {
                    content()
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SmolScrollableDialog(
    modifier: Modifier = Modifier,
    underlayModifier: Modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight()
        .background(Color.Black.copy(alpha = ContentAlpha.medium)),
    onDismissRequest: () -> Unit,
    confirmButton: @Composable (() -> Unit)? = { SmolButton(onClick = { onDismissRequest() }) { Text("Ok") } },
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    backgroundColor: Color = MaterialTheme.colors.surface,
    content: @Composable () -> Unit
) {
    Box(
        modifier = underlayModifier
    ) {
        Popup(
            popupPositionProvider = object : PopupPositionProvider {
                override fun calculatePosition(
                    anchorBounds: IntRect,
                    windowSize: IntSize,
                    layoutDirection: LayoutDirection,
                    popupContentSize: IntSize
                ): IntOffset = IntOffset(
                    windowSize.height / 2 - popupContentSize.height / 2,
                    windowSize.width / 2 - popupContentSize.width / 2
                )
            },
            focusable = true,
            onDismissRequest = onDismissRequest,
            onKeyEvent = {
                if ((it.type == KeyEventType.KeyDown) && (it.key == Key.Escape)) {
                    onDismissRequest()
                    true
                } else {
                    false
                }
            },
        ) {
            val scrimColor = Color.Black.copy(alpha = 0.32f)
            Box(
                modifier = modifier
                    // TODO shrink to fit, make this only the max size, not min.
                    .fillMaxSize(0.8f)
                    .background(scrimColor)
                    .pointerInput(onDismissRequest) {
                        detectTapGestures(onPress = { onDismissRequest() })
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    Modifier
                        .clip(shape)
                        .background(backgroundColor)
                        .pointerInput(onDismissRequest) {
                            detectTapGestures(onPress = {
                                // Workaround to disable clicks on Surface background https://github.com/JetBrains/compose-jb/issues/2581
                            })
                        }, elevation = 24.dp
                ) {
                    val scrollState = rememberScrollState()
                    Box(
                        Modifier
                            .padding(16.dp)
                    ) {
                        Column {
                            if (title != null) {
                                title()
                                Spacer(Modifier.height(8.dp))
                            }
                            Box(Modifier.weight(1f)) {
                                Column(Modifier.verticalScroll(scrollState)) {
                                    content()
                                }

                                VerticalScrollbar(
                                    adapter = rememberScrollbarAdapter(scrollState),
                                    modifier = Modifier
                                        .width(8.dp)
                                        .align(Alignment.CenterEnd)
                                        .fillMaxHeight()
                                )
                            }
                            if (confirmButton != null || dismissButton != null) {
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.padding(end = 4.dp).align(Alignment.End)) {
                                    if (dismissButton != null) {
                                        dismissButton()
                                    }
                                    if (confirmButton != null) {
                                        Box(Modifier.padding(start = 8.dp)) {
                                            confirmButton()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}