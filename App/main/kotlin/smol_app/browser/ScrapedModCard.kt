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

package smol_app.browser

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.mouseClickable
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.http.*
import mod_repo.ModSource
import mod_repo.ScrapedMod
import org.tinylog.kotlin.Logger
import smol_app.composables.SmolTooltipArea
import smol_app.composables.SmolTooltipText
import smol_app.themes.SmolTheme
import smol_app.themes.SmolTheme.lighten
import smol_app.util.MarkdownParser
import smol_app.util.openAsUriInBrowser
import smol_app.util.smolPreview
import java.awt.Cursor

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun scrapedModCard(mod: ScrapedMod, linkLoader: MutableState<((String) -> Unit)?>) {
    Card(
        modifier = Modifier
            .wrapContentHeight()
            .border(
                width = 1.dp,
                color = MaterialTheme.colors.surface.lighten(),
                shape = SmolTheme.smolFullyClippedButtonShape()
            )
            .clickable {
                mod.forumPostLink?.run { linkLoader.value?.invoke(this.toString()) }
            },
        shape = SmolTheme.smolFullyClippedButtonShape()
    ) {

        Row(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier.align(Alignment.CenterVertically)
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                SmolTooltipArea(
                    tooltip = {
                        mod.description?.let {
                            SmolTooltipText(
                                text = MarkdownParser.messageFormatter(
                                    text = it,
                                    primary = true
                                )
                            )
                        }
                    },
                    delayMillis = if (mod.description != null)
                        SmolTooltipArea.shortDelay
                    else Int.MAX_VALUE
                ) {
                    Column {
                        Text(
                            modifier = Modifier,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            fontFamily = SmolTheme.orbitronSpaceFont,
                            text = mod.name.ifBlank { "???" }
                        )
                        if (mod.authors.isNotBlank()) {
                            Text(
                                modifier = Modifier.padding(top = 8.dp),
                                fontSize = 11.sp,
                                fontStyle = FontStyle.Italic,
                                text = mod.authors
                            )
                        }

                        val tags = remember {
                            mod.categories + when (mod.source) {
                                ModSource.Index -> "Index"
                                ModSource.ModdingSubforum -> "Modding Subforum"
                                ModSource.Discord -> "Discord"
                            }
                        }
                        if (tags.isNotEmpty()) {
                            Row(modifier = Modifier.padding(top = 12.dp)) {
                                Icon(
                                    modifier = Modifier.size(12.dp).align(Alignment.CenterVertically),
                                    painter = painterResource("icon-tag.svg"),
                                    contentDescription = null
                                )
                                Text(
                                    modifier = Modifier.align(Alignment.CenterVertically).padding(start = 6.dp),
                                    fontSize = 11.sp,
                                    text = tags.joinToString()
                                )
                            }
                        }
                    }
                }
            }
            browserIcon(modifier = Modifier.align(Alignment.Top), mod = mod)
        }
    }
}

@Preview
@Composable
fun scrapedModCardPreview() = smolPreview {
    scrapedModCard(
        ScrapedMod(
            name = "Archean Order",
            description = "test description",
            gameVersionReq = "0.95a",
            authors = "Morrokain",
            forumPostLink = Url("index0026.html?topic=13183.0"),
            categories = listOf("Total Conversions"),
            source = ModSource.Index
        ),
        mutableStateOf({})
    )
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun browserIcon(modifier: Modifier = Modifier, mod: ScrapedMod) {
    if (mod.forumPostLink?.toString()?.isBlank() == false) {
        val descText = "Open in an external browser\n${mod.forumPostLink}"
        SmolTooltipArea(
            modifier = modifier,
            tooltip = { SmolTooltipText(text = descText) }) {
            Icon(
                painter = painterResource("icon-web.svg"),
                contentDescription = descText,
                modifier = Modifier
                    .width(16.dp)
                    .height(16.dp)
                    .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                    .mouseClickable {
                        if (this.buttons.isPrimaryPressed) {
                            runCatching {
                                mod.forumPostLink?.toString()?.openAsUriInBrowser()
                            }
                                .onFailure { Logger.warn(it) }
                        }
                    },
                tint = SmolTheme.dimmedIconColor()
            )
        }
    }
}