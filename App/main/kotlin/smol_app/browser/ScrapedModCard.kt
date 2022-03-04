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

import AppScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.mouseClickable
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerMoveFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.http.*
import mod_repo.ModSource
import mod_repo.ScrapedMod
import org.tinylog.kotlin.Logger
import smol_app.WindowState
import smol_app.composables.SmolAlertDialog
import smol_app.composables.SmolButton
import smol_app.composables.SmolTooltipArea
import smol_app.composables.SmolTooltipText
import smol_app.themes.SmolTheme
import smol_app.themes.SmolTheme.hyperlink
import smol_app.themes.SmolTheme.lighten
import smol_app.util.MarkdownParser
import smol_app.util.openAsUriInBrowser
import smol_app.util.smolPreview
import java.awt.Cursor

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun AppScope.scrapedModCard(mod: ScrapedMod, linkLoader: MutableState<((String) -> Unit)?>) {
    var isBeingHovered by remember { mutableStateOf(false) }

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
            }
            .pointerMoveFilter(
                onEnter = { isBeingHovered = true; false },
                onExit = { isBeingHovered = false; false }
            ),
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
                                    linkColor = MaterialTheme.colors.hyperlink
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

                        // Description button
                        if (!mod.description.isNullOrBlank()) {
                            OutlinedButton(
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(min = 24.dp),
                                onClick = {
                                    alertDialogSetter.invoke {
                                        SmolAlertDialog(
                                            title = {
                                                SelectionContainer {
                                                    Text(
                                                        text = mod.name,
                                                        style = SmolTheme.alertDialogTitle(),
                                                    )
                                                }
                                            },
                                            text = {
//                                                SelectionContainer {
                                                val text = MarkdownParser.messageFormatter(
                                                    text = mod.description!!,
                                                    linkColor = MaterialTheme.colors.hyperlink
                                                )
                                                ClickableText(
                                                    text = text,
                                                    style = SmolTheme.alertDialogBody()
                                                        .copy(color = MaterialTheme.colors.onSurface),
                                                    onClick = {
                                                        text.getStringAnnotations(
                                                            tag = MarkdownParser.SymbolAnnotationType.LINK.name,
                                                            start = it,
                                                            end = it
                                                        ).firstOrNull()
                                                            ?.item
                                                            ?.also {
                                                                linkLoader.value?.invoke(it)
                                                            }
                                                    }
                                                )
//                                                }
                                            },
                                            onDismissRequest = { alertDialogSetter.invoke(null) },
                                            confirmButton = {
                                                SmolButton(onClick = { alertDialogSetter.invoke(null) }) {
                                                    Text("Ok")
                                                }
                                            }
                                        )
                                    }
                                }) {
                                Text(
                                    text = "View Desc.",
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.9f)
                                )
                            }
                        }

                        val tags = remember {
                            ((mod.categories ?: emptyList()) + when (mod.source) {
                                ModSource.Index -> "Index"
                                ModSource.ModdingSubforum -> "Modding Subforum"
                                ModSource.Discord -> "Discord"
                                null -> null
                            })
                                .filterNotNull()
                        }
                        if (tags.isNotEmpty()) {
                            Row(modifier = Modifier.padding(top = 4.dp)) {
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
            Column(modifier = Modifier.align(Alignment.Top)) {
                val alphaOfHoverDimmedElements =
                    animateFloatAsState(if (isBeingHovered) 1.0f else 0.7f)
                BrowserIcon(iconModifier = Modifier.alpha(alphaOfHoverDimmedElements.value), mod = mod)
                DiscordIcon(iconModifier = Modifier.alpha(alphaOfHoverDimmedElements.value), mod = mod)
            }
        }
    }
}

@Preview
@Composable
fun scrapedModCardPreview() = smolPreview {
    AppScope(windowState = WindowState(), recomposer = currentRecomposeScope).scrapedModCard(
        ScrapedMod(
            name = "Archean Order",
            description = "test description",
            gameVersionReq = "0.95a",
            authors = "Morrokain",
            forumPostLink = Url("index0026.html?topic=13183.0"),
            discordMessageLink = Url("https://discord.com/channels/187635036525166592/537191061156659200/947258123528319097"),
            categories = listOf("Total Conversions"),
            source = ModSource.Index
        ),
        mutableStateOf({})
    )
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun BrowserIcon(modifier: Modifier = Modifier, iconModifier: Modifier = Modifier, mod: ScrapedMod) {
    if (mod.forumPostLink?.toString()?.isBlank() == false) {
        val descText = "Open in an external browser.\n${mod.forumPostLink}"
        SmolTooltipArea(
            modifier = modifier,
            tooltip = { SmolTooltipText(text = descText) }) {
            Icon(
                painter = painterResource("icon-web.svg"),
                contentDescription = descText,
                modifier = iconModifier
                    .size(16.dp)
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

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun DiscordIcon(modifier: Modifier = Modifier, iconModifier: Modifier = Modifier, mod: ScrapedMod) {
    if (mod.discordMessageLink?.toString()?.isBlank() == false) {
        val descText = "Open in web Discord.\n${mod.discordMessageLink}"
        SmolTooltipArea(
            modifier = modifier,
            tooltip = { SmolTooltipText(text = descText) }) {
            Icon(
                painter = painterResource("icon-discord-white.svg"),
                contentDescription = descText,
                modifier = iconModifier
                    .padding(top = 8.dp)
                    .size(16.dp)
                    .padding(1.dp)
                    .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                    .mouseClickable {
                        if (this.buttons.isPrimaryPressed) {
                            runCatching {
                                mod.discordMessageLink?.toString()?.openAsUriInBrowser()
                            }
                                .onFailure { Logger.warn(it) }
                        }
                    },
                tint = SmolTheme.dimmedIconColor()
            )
        }
    }
}