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

package smol.app.browser

import AppScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.Markdown
import io.kamel.image.KamelImage
import io.kamel.image.lazyPainterResource
import io.ktor.http.*
import org.tinylog.kotlin.Logger
import smol.app.WindowState
import smol.app.composables.*
import smol.app.themes.LocalUsableBounds
import smol.app.themes.SmolTheme
import smol.app.themes.SmolTheme.lighten
import smol.app.util.onEnterKeyPressed
import smol.app.util.openAsUriInBrowser
import smol.app.util.parseHtml
import smol.app.util.smolPreview
import smol.mod_repo.Image
import smol.mod_repo.ModSource
import smol.mod_repo.ModUrlType
import smol.mod_repo.ScrapedMod
import smol.timber.ktx.Timber
import smol.utilities.copyToClipboard
import java.awt.Cursor
import java.util.*

private const val imageWidth = 192
private const val height = 160

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun AppScope.scrapedModCard(
    modifier: Modifier = Modifier,
    mod: ScrapedMod,
    linkLoader: (String) -> Unit
) {
    var isBeingHovered by remember { mutableStateOf(false) }
    val markdownWidth = 800
    val urls = mod.urls()
    val directDownloadDialog = {
        alertDialogSetter.invoke {
            SmolAlertDialog(
                onDismissRequest = ::dismissAlertDialog,
                title = { Text(style = SmolTheme.alertDialogTitle(), text = mod.name) },
                text = {
                    Text(
                        style = SmolTheme.alertDialogBody(),
                        text = "Do you want to download '${mod.name}'?"
                    )
                },
                confirmButton = {
                    Button(onClick = { urls[ModUrlType.DirectDownload]?.run { linkLoader.invoke(this.toString()) } }) {
                        Text("Download")
                    }
                },
                dismissButton = { Button(onClick = ::dismissAlertDialog) { Text("Cancel") } }
            )
        }
    }

    Card(
        modifier = modifier
            .height(IntrinsicSize.Min)
            .heightIn(min = height.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colors.surface.lighten(),
                shape = SmolTheme.smolFullyClippedButtonShape()
            )
            .clickable {
                when {
                    urls.containsKey(ModUrlType.Forum) ->
                        urls[ModUrlType.Forum]?.run { linkLoader.invoke(this.toString()) }
                    urls.containsKey(ModUrlType.NexusMods) ->
                        urls[ModUrlType.NexusMods]?.run { linkLoader.invoke(this.toString()) }
                    urls.containsKey(ModUrlType.DirectDownload) -> directDownloadDialog.invoke()
                }
            }
            .pointerMoveFilter(
                onEnter = { isBeingHovered = true; false },
                onExit = { isBeingHovered = false; false }
            ),
        shape = SmolTheme.smolFullyClippedButtonShape()
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxHeight()) {
            ModImage(
                modifier = Modifier
                    .align(Alignment.CenterVertically),
                mod = mod
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Top)
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                SmolTooltipArea(
                    tooltip = {
                        mod.description?.let {
                            SmolTooltipBackground {
                                CompositionLocalProvider(LocalUriHandler provides ModBrowserLinkLoader(linkLoader)) {
                                    Markdown(
                                        it,
                                        modifier = Modifier
                                            .widthIn(max = markdownWidth.dp)
                                    )
                                }
                            }
                        }
                    },
                    delayMillis = if (mod.description != null)
                        SmolTooltipArea.longDelay
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
                        if (mod.authors().isNotEmpty()) {
                            Text(
                                modifier = Modifier.padding(top = 8.dp),
                                fontSize = 11.sp,
                                fontStyle = FontStyle.Italic,
                                text = mod.authors().joinToString()
                            )
                        }

                        val summary = mod.summary?.ifBlank { null } ?: mod.description?.ifBlank { null }
                        if (summary != null) {
                            // Description text
                            SmolText(
                                text = summary
                                    .lines()
                                    .filter { it.isNotBlank() }
                                    .take(2)
                                    .joinToString(separator = "\n"),
                                style = MaterialTheme.typography.caption,
                                color = LocalContentColor.current.copy(alpha = ContentAlpha.medium),
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            // Description button
                            OutlinedButton(
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(min = 24.dp),
                                onClick = {
                                    alertDialogSetter.invoke {
                                        val description =
                                            mod.description?.ifBlank { null } ?: mod.summary?.ifBlank { null } ?: ""

                                        SmolAlertDialog(
                                            text = {
                                                CompositionLocalProvider(
                                                    LocalUriHandler provides ModBrowserLinkLoader(linkLoader)
                                                ) {
                                                    Markdown(
                                                        content = description,
                                                        modifier = Modifier
                                                            .verticalScroll(rememberScrollState())
                                                    )
                                                }
                                            },
                                            onDismissRequest = { alertDialogSetter.invoke(null) },
                                            confirmButton = {
                                                SmolButton(onClick = { alertDialogSetter.invoke(null) }) {
                                                    Text("Ok")
                                                }
                                            },
                                            modifier = Modifier
                                                .padding(24.dp)
                                                .width(markdownWidth.dp)
                                                .onEnterKeyPressed { alertDialogSetter.invoke(null); true }
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

                        Spacer(Modifier.weight(1f))
                        tags(mod = mod)
                    }
                }
            }
            Column(modifier = Modifier.align(Alignment.Top)) {
                val alphaOfHoverDimmedElements =
                    animateFloatAsState(if (isBeingHovered) 1.0f else 0.7f)
                BrowserIcon(iconModifier = Modifier.alpha(alphaOfHoverDimmedElements.value), mod = mod)
                DiscordIcon(iconModifier = Modifier.alpha(alphaOfHoverDimmedElements.value), mod = mod)
                NexusModsIcon(iconModifier = Modifier.alpha(alphaOfHoverDimmedElements.value), mod = mod)
                DirectDownloadIcon(iconModifier = Modifier.alpha(alphaOfHoverDimmedElements.value), mod = mod)
                DebugIcon(iconModifier = Modifier.alpha(alphaOfHoverDimmedElements.value), mod = mod)
            }
        }
    }
}

@Composable
private fun tags(modifier: Modifier = Modifier, mod: ScrapedMod) {
    val tags =
        mod.categories().sorted() + mod.sources()
            .sortedWith(
                compareBy<ModSource> { it == ModSource.Index }
                    .thenBy { it == ModSource.Discord }
                    .thenBy { it == ModSource.ModdingSubforum }
            )
            .map {
                when (it) {
                    ModSource.Index -> "Index"
                    ModSource.ModdingSubforum -> "Modding Subforum"
                    ModSource.Discord -> "Discord"
                    ModSource.NexusMods -> "NexusMods"
                }
            }

    if (tags.isNotEmpty()) {
        Row(
            modifier = modifier
                .padding(top = 4.dp)
        ) {
            Icon(
                modifier = Modifier.size(12.dp).align(Alignment.Bottom),
                painter = painterResource("icon-tag.svg"),
                contentDescription = null
            )
            Text(
                modifier = Modifier.align(Alignment.Bottom).padding(start = 6.dp),
                fontSize = 11.sp,
                text = tags.joinToString()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModImage(modifier: Modifier = Modifier, mod: ScrapedMod) {
    val mainImage: Image? = mod.images().entries.firstOrNull()?.value

    @Composable
    fun defaultImage() {
        Box(
            Modifier
                .width(imageWidth.dp)
                .padding(end = 16.dp)
        ) {
            Image(
                modifier = Modifier.size(64.dp)
                    .align(Alignment.Center),
                painter = painterResource("icon-image.svg"),
                contentDescription = null,
                colorFilter = ColorFilter.tint(LocalContentColor.current.copy(alpha = ContentAlpha.disabled))
            )
        }
    }

    Box(modifier) {
        if (mainImage?.url != null) {
            SmolTooltipArea(
                tooltip = {
                    SmolTooltipBackground {
                        KamelImage(
                            resource = lazyPainterResource(data = mainImage.url!!),
                            modifier = Modifier,
                            onLoading = {
                                defaultImage()
                            },
                            onFailure = {
                                defaultImage()
                            },
                            crossfade = true,
                            contentDescription = mainImage.description
                        )
                    }
                },
                delayMillis = SmolTooltipArea.longDelay
            ) {
                KamelImage(
                    resource = lazyPainterResource(data = mainImage.url!!),
                    modifier = Modifier
                        .width(imageWidth.dp)
                        .padding(end = 16.dp),
                    onLoading = {
                        defaultImage()
                    },
                    onFailure = {
                        Timber.w(it)
                        defaultImage()
                    },
                    crossfade = true,
                    contentDescription = mainImage.description
                )
            }
        } else {
            defaultImage()
        }
    }
}

@Preview
@Composable
fun scrapedModCardPreview() = smolPreview {
    AppScope(windowState = WindowState(), recomposer = currentRecomposeScope).scrapedModCard(
        mod = ScrapedMod(
            name = "Archean Order",
            summary = "test summary",
            description = "test description which is a longer summary",
            modVersion = null,
            gameVersionReq = "0.95a",
            authors = "Morrokain",
            authorsList = listOf("Morrokain"),
            forumPostLink = Url("index0026.html?topic=13183.0"),
            link = Url("index0026.html?topic=13183.0"),
            urls = mapOf(ModUrlType.Discord to Url("https://discord.com/channels/187635036525166592/537191061156659200/947258123528319097")),
            categories = listOf("Total Conversions"),
            source = ModSource.Index,
            sources = listOf(ModSource.Index, ModSource.Discord),
            images = emptyMap(),
            dateTimeCreated = Date(),
            dateTimeEdited = Date(),
        ),
        linkLoader = {}
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowserIcon(modifier: Modifier = Modifier, iconModifier: Modifier = Modifier, mod: ScrapedMod) {
    val forumUrl = mod.urls()[ModUrlType.Forum]?.toString()

    if (forumUrl?.isBlank() == false) {
        val descText = "Open in an external browser.\n${forumUrl}"
        SmolTooltipArea(
            modifier = modifier,
            tooltip = { SmolTooltipText(text = descText) }) {
            val uriHandler = LocalUriHandler.current
            Icon(
                painter = painterResource("icon-web.svg"),
                contentDescription = descText,
                modifier = iconModifier
                    .size(16.dp)
                    .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                    .mouseClickable {
                        if (this.buttons.isPrimaryPressed) {
                            runCatching {
                                forumUrl.let { uriHandler.openUri(it) }
                            }
                                .onFailure { Logger.warn(it) }
                        }
                    },
                tint = SmolTheme.dimmedIconColor()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun AppScope.DiscordIcon(modifier: Modifier = Modifier, iconModifier: Modifier = Modifier, mod: ScrapedMod) {
    val discordUrl = mod.urls()[ModUrlType.Discord]

    if (discordUrl?.toString()?.isBlank() == false) {
        val descText = "Open in Discord.\n${discordUrl}\nRight-click to copy."
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
                        runCatching {
                            if (this.buttons.isPrimaryPressed) {
                                this@DiscordIcon.alertDialogSetter.invoke {
                                    SmolAlertDialog(
                                        onDismissRequest = ::dismissAlertDialog,
                                        dialogProvider = SmolAlertDialogProvider(boundsModifier = Modifier.fillMaxHeight().width(LocalUsableBounds.width)),
                                        confirmButton = {
                                            SmolButton(onClick = {
                                                discordUrl.toString()
                                                    .replace("https://", "discord://")
                                                    .replace("http://", "discord://")
                                                    .openAsUriInBrowser()
                                                dismissAlertDialog()
                                            }) { Text("Open in Discord") }
                                        },
                                        dismissButton = {
                                            SmolSecondaryButton(onClick = ::dismissAlertDialog) {
                                                Text("Cancel")
                                            }
                                        },
                                        text = {
                                            Column {
                                                Text(text = "This will open this post in the Discord application. Do you want to continue?")
                                                SelectionContainer {
                                                    Text(
                                                        modifier = Modifier.padding(top = 8.dp),
                                                        text = discordUrl.toString().parseHtml()
                                                    )
                                                }
                                            }
                                        }
                                    )
                                }
                            } else if (this.buttons.isSecondaryPressed) {
                                copyToClipboard(discordUrl.toString())
                            }
                        }
                            .onFailure { Logger.warn(it) }
                    },
                tint = SmolTheme.dimmedIconColor()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NexusModsIcon(modifier: Modifier = Modifier, iconModifier: Modifier = Modifier, mod: ScrapedMod) {
    val nexusModsUrl = mod.urls()[ModUrlType.NexusMods]

    if (nexusModsUrl?.toString()?.isBlank() == false) {
        val descText = "Open in web NexusMods.\n$nexusModsUrl"
        SmolTooltipArea(
            modifier = modifier,
            tooltip = { SmolTooltipText(text = descText) }) {
            Icon(
                painter = painterResource("icon-nexus.svg"),
                contentDescription = descText,
                modifier = iconModifier
                    .padding(top = 8.dp)
                    .size(16.dp)
                    .padding(1.dp)
                    .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                    .mouseClickable {
                        if (this.buttons.isPrimaryPressed) {
                            runCatching {
                                nexusModsUrl.toString().openAsUriInBrowser()
                            }
                                .onFailure { Logger.warn(it) }
                        }
                    },
                tint = SmolTheme.dimmedIconColor()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DirectDownloadIcon(modifier: Modifier = Modifier, iconModifier: Modifier = Modifier, mod: ScrapedMod) {
    val downloadUrl = mod.urls()[ModUrlType.DirectDownload]

    if (downloadUrl?.toString()?.isBlank() == false) {
        val descText = "Download.\n$downloadUrl"
        SmolTooltipArea(
            modifier = modifier,
            tooltip = { SmolTooltipText(text = descText) }) {
            Icon(
                painter = painterResource("icon-direct-install.svg"),
                contentDescription = descText,
                modifier = iconModifier
                    .padding(top = 8.dp)
                    .size(16.dp)
                    .padding(1.dp)
                    .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                    .mouseClickable {
                        if (this.buttons.isPrimaryPressed) {
                            runCatching {
                                downloadUrl.toString().openAsUriInBrowser()
                            }
                                .onFailure { Logger.warn(it) }
                        }
                    },
                tint = SmolTheme.dimmedIconColor()
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun AppScope.DebugIcon(modifier: Modifier = Modifier, iconModifier: Modifier = Modifier, mod: ScrapedMod) {
    val descText = "Display all info (debug)"
    SmolTooltipArea(
        modifier = modifier,
        tooltip = { SmolTooltipText(text = descText) }) {
        Icon(
            painter = painterResource("icon-debug.svg"),
            contentDescription = descText,
            modifier = iconModifier
                .padding(top = 8.dp)
                .size(16.dp)
                .padding(1.dp)
                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                .mouseClickable {
                    if (this.buttons.isPrimaryPressed) {
                        alertDialogSetter.invoke {
                            SmolAlertDialog(
                                modifier = modifier
                                    .widthIn(min = 700.dp)
                                    .onEnterKeyPressed { dismissAlertDialog(); true },
                                dialogProvider = SmolAlertDialogProvider(boundsModifier = Modifier.fillMaxHeight().width(LocalUsableBounds.width)),
                                onDismissRequest = ::dismissAlertDialog,
                                title = { Text(text = mod.name, style = SmolTheme.alertDialogTitle()) },
                                text = {
                                    SelectionContainer {
                                        Box(
                                            Modifier
                                                .padding(top = SmolTheme.topBarHeight)
                                        ) {
                                            Column {
                                                Divider(
                                                    Modifier.padding(top = 8.dp, bottom = 4.dp).height(2.dp)
                                                        .fillMaxWidth()
                                                )
                                                Text(
                                                    "<b>Mod Info</b>\n${mod}".parseHtml(),
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                },
            tint = SmolTheme.dimmedIconColor()
        )
    }
}