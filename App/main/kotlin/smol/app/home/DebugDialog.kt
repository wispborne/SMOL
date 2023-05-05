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

package smol.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import smol.access.Constants
import smol.access.model.Mod
import smol.app.composables.SmolAlertDialog
import smol.app.themes.SmolTheme
import smol.app.util.onEnterKeyPressed
import smol.app.util.parseHtml

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun debugDialog(
    modifier: Modifier = Modifier,
    mod: Mod,
    onDismiss: () -> Unit
) {
    SmolAlertDialog(
        modifier = modifier
            .widthIn(min = 700.dp)
            .onEnterKeyPressed { onDismiss.invoke(); true },
        onDismissRequest = onDismiss,
        title = { Text(text = mod.id, style = SmolTheme.alertDialogTitle()) },
        text = {
            SelectionContainer {
                Box(
                    Modifier
                        .padding(top = SmolTheme.topBarHeight)
                ) {
                    Column {//(Modifier.verticalScroll(scrollState)) {
//                        Text(
//                            "<b>NOTE: The wacky scrolling is due to a bug in the UI framework.</b>\n(https://github.com/JetBrains/compose-jb/issues/976)".parseHtml(),
//                            modifier = Modifier.padding(bottom = 8.dp)
//                        )
                        Text("<b>Id</b>: <code>${mod.id}</code>".parseHtml())
                        Text(
                            "<b>Enabled in ${Constants.ENABLED_MODS_FILENAME}?</b>: ${mod.isEnabledInGame}".parseHtml(),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        mod.variants.forEach { variant ->
                            Divider(Modifier.padding(top = 8.dp, bottom = 4.dp).height(2.dp).fillMaxWidth())
                            Text(
                                "<b>SMOL variant id</b>: ${variant.smolId}".parseHtml(),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                "<b>Version</b>: ${variant.modInfo.version}".parseHtml(),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                "<b>Version Checker info</b>\n${variant.versionCheckerInfo}".parseHtml(),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                "<b>/mods folder</b>: ${variant.modsFolderInfo?.folder}".parseHtml(),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Text(
                                "<b>Mod Info</b>\n${variant.modInfo}".parseHtml(),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

//                    VerticalScrollbar(
//                        modifier = Modifier.align(Alignment.CenterEnd).width(8.dp).fillMaxHeight(),
//                        adapter = rememberScrollbarAdapter(scrollState)
//                    )
                }
            }
        }
    )
}