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

package smol_app.about

import AppScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.mouseClickable
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.replaceCurrent
import smol_access.Constants
import smol_app.composables.SmolLinkText
import smol_app.composables.SmolTooltipArea
import smol_app.composables.SmolTooltipText
import smol_app.navigation.Screen
import smol_app.themes.SmolTheme
import smol_app.toolbar.toolbar
import smol_app.util.parseHtml
import java.awt.Cursor

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppScope.AboutView(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(modifier = Modifier.height(SmolTheme.topBarHeight)) {
                toolbar(router.state.value.activeChild.instance as Screen)

                Spacer(Modifier.weight(1f))
                SmolTooltipArea(tooltip = { SmolTooltipText("About") }) {
                    IconButton(
                        onClick = { router.replaceCurrent(Screen.About) },
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Icon(
                            painter = painterResource("icon-info.svg"),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colors.secondary
                        )
                    }
                }
            }
        }, content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SelectionContainer {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .widthIn(max = 800.dp)
                            .align(Alignment.CenterHorizontally)
                    ) {
                        var showOnsmolt by remember { mutableStateOf(false) }
                        DisableSelection {
                            Image(
                                painter = if (!showOnsmolt)
                                    painterResource("smol_tart_blue_wisped.png")
                                else painterResource("smolslaught.png"),
                                contentDescription = null,
                                modifier = Modifier.size(96.dp).padding(top = 24.dp)
                                    .mouseClickable {
                                        showOnsmolt = showOnsmolt.not()
                                    }
                                    .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                            )
                        }
                        Text(
                            text = Constants.APP_NAME,
                            style = MaterialTheme.typography.h5,
                            modifier = Modifier.padding(top = 24.dp)
                        )
                        Text(
                            text = Constants.APP_VERSION,
                            style = MaterialTheme.typography.body2
                        )
                        Text(
                            text = "by Wisp",
                            style = MaterialTheme.typography.body2,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                        Text(
                            text = "Credits",
                            textDecoration = TextDecoration.Underline,
                            style = MaterialTheme.typography.h6,
                            modifier = Modifier.padding(top = 40.dp, bottom = 16.dp)
                        )
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                listOf(
                                    "<b>Fractal Softworks</b> for making Starsector and for permission to scrape the forum periodically.".parseHtml(),
                                    "<b>MesoTroniK</b> for consulting and brainstorming the whole way through.".parseHtml(),
                                    "<b>AtlanticAccent</b> for open-sourcing his Mod Manager, MOSS, allowing me to peek under the hood (I copied almost nothing, I swear!) and being a great competitor :)".parseHtml(),
                                    "<b>rubi/CeruleanPancake</b> for feedback, QA, and morale/moral support.".parseHtml(),
                                    "<b>Soren/Harmful Mechanic</b> for feedback.".parseHtml(),
                                    "<b>ruddygreat</b> for feedback and QA.".parseHtml(),
                                    "<b>Tartiflette</b> for the idea to disable mods by renaming the mod_info.json file, the SMOL icon, and other feedback.".parseHtml(),
                                    "<b>The rest of the USC moderator team</b> for feedback.".parseHtml(),
                                )
                            ) { littleHelper ->
                                Text(
                                    text = littleHelper,
                                )
                            }
                        }

                        DisableSelection {
                            Column(
                                modifier = Modifier
                                    .padding(top = 40.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    painter = painterResource("icon-github.svg"),
                                    contentDescription = null,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                SmolLinkText(
                                    text = "Source Code: https://github.com/davidwhitman/SMOL",
                                )
                                SmolLinkText(
                                    text = "Releases: https://github.com/davidwhitman/SMOL/releases"
                                )
                                SmolLinkText(
                                    text = "Mod Repo: https://github.com/davidwhitman/StarsectorModRepo"
                                )
                            }
                        }
                    }
                }
            }
        }
    )
}