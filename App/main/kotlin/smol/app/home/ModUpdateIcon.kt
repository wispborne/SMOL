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

import AppScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.PointerMatcher
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.onClick
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.replaceCurrent
import io.ktor.client.request.*
import io.ktor.client.statement.*
import smol.access.Constants
import smol.access.SL
import smol.access.model.Mod
import smol.access.model.VersionCheckerInfo
import smol.app.UI
import smol.app.composables.SmolScrollableDialog
import smol.app.composables.SmolTooltipArea
import smol.app.composables.SmolTooltipBackground
import smol.app.composables.SmolTooltipText
import smol.app.navigation.Screen
import smol.app.themes.SmolTheme
import smol.app.util.*
import smol.timber.ktx.Timber
import smol.utilities.nullIfBlank
import java.awt.Cursor
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppScope.ModUpdateIcon(
    modifier: Modifier = Modifier,
    highestLocalVersion: VersionCheckerInfo.Version?,
    onlineVersion: VersionCheckerInfo.Version?,
    onlineVersionInfo: VersionCheckerInfo?,
    mod: Mod
) {
    val alpha = 0.4f

    Row(modifier) {
        // If successfully version checked...
        if (highestLocalVersion != null && onlineVersion != null) {
            val hasUpdate = onlineVersion > highestLocalVersion && onlineVersionInfo != null
            val changelogUrl = onlineVersionInfo?.changelogUrl?.nullIfBlank()
                ?: mod.findHighestVersion?.versionCheckerInfo?.changelogUrl?.nullIfBlank()

            if (changelogUrl != null) {
                ChangelogIcon(
                    onlineVersionInfo,
                    mod,
                    hasUpdate,
                    alpha,
                    changelogUrl,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }

            // ...and if an update was found.
            if (hasUpdate) {
                val ddUrl =
                    onlineVersionInfo?.directDownloadUrl?.nullIfBlank()
                        ?: mod.findHighestVersion?.versionCheckerInfo?.directDownloadUrl?.nullIfBlank()

                // Has direct download URL
                if (ddUrl != null) {
                    SmolTooltipArea(tooltip = {
                        SmolTooltipText(
                            text = buildAnnotatedString {
                                append("Newer version available: ${onlineVersionInfo?.modVersion}")
                                append("\nCurrent version: $highestLocalVersion.")
                                append("\n\n<i>Update information is provided by the mod author, not SMOL, and cannot be guaranteed.</i>".parseHtml())
                                append("\n\nClick to download and update.")
                            }
                        )
                    }, modifier = Modifier.onClick(
                        matcher = PointerMatcher.mouse(PointerButton.Primary),
                        onClick = {
                            if (SL.userManager.activeProfile.value.warnAboutOneClickUpdates == false) {
                                SL.UI.downloadManager.downloadFromUrl(
                                    url = ddUrl,
                                    name = mod.findFirstEnabledOrHighestVersion?.modInfo?.name ?: "Unknown",
                                    appScope = this@ModUpdateIcon,
                                    // If the mod was enabled, switch to new version. Otherwise, keep disabled
                                    shouldSwitchToAfter = mod.hasEnabledVariant
                                )
                            } else {
                                alertDialogSetter {
                                    DirectDownloadAlertDialog(
                                        ddUrl = ddUrl,
                                        mod = mod,
                                        onlineVersion = onlineVersion
                                    )
                                }
                            }
                        }
                    )
                        .align(Alignment.CenterVertically)) {
                        Icon(
                            painter = painterResource("icon-direct-install.svg"),
                            contentDescription = null,
                            tint = MaterialTheme.colors.secondary,
                            modifier = Modifier.width(SmolTheme.modUpdateIconSize.dp)
                                .height(SmolTheme.modUpdateIconSize.dp)
                                .padding(end = 8.dp)
                                .align(Alignment.CenterVertically)
                                .pointerHoverIcon(
                                    PointerIcon(
                                        Cursor.getPredefinedCursor(
                                            Cursor.HAND_CURSOR
                                        )
                                    )
                                )
                        )
                    }
                } else {
                    // No direct download URL
                    val modThreadId =
                        mod.findHighestVersion?.versionCheckerInfo?.modThreadId
                    val hasModThread = modThreadId?.isNotBlank() == true
                    SmolTooltipArea(tooltip = {
                        SmolTooltipText(
                            text = buildAnnotatedString {
                                append("Newer version available: ${onlineVersionInfo?.modVersion}.")
                                append("\nCurrent version: $highestLocalVersion.")
                                append("\n\n<i>Update information is provided by the mod author, not SMOL, and cannot be guaranteed.</i>".parseHtml())
                                if (ddUrl == null) append("\n<i>This mod does not support direct download and should be downloaded manually.</i>".parseHtml())
                                if (hasModThread) {
                                    append("\n\nClick to open <code>${modThreadId?.getModThreadUrl()}</code>.".parseHtml())
                                } else {
                                    append("\n\n<b>No mod thread provided. Click to search on Google.</b>".parseHtml())
                                }
                            }
                        )
                    }, modifier = Modifier.onClick {
                        if (hasModThread) {
                            if (Constants.isModBrowserEnabled()) {
                                router.replaceCurrent(Screen.ModBrowser(modThreadId?.getModThreadUrl()))
                            } else {
                                kotlin.runCatching {
                                    modThreadId?.getModThreadUrl()
                                        ?.openAsUriInBrowser()
                                }
                                    .onFailure { Timber.w(it) }
                            }
                        } else {
                            createGoogleSearchFor("starsector ${mod.findHighestVersion?.modInfo?.name}")
                                .openAsUriInBrowser()
                        }
                    }
                        .align(Alignment.CenterVertically)) {
                        Image(
                            painter = painterResource(
                                if (hasModThread) "icon-new-update.svg"
                                else "icon-new-update-search.svg"
                            ),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(
                                color =
                                MaterialTheme.colors.secondary
                            ),
                            modifier = Modifier.width(SmolTheme.modUpdateIconSize.dp)
                                .height(SmolTheme.modUpdateIconSize.dp)
                                .padding(end = 8.dp)
                                .align(Alignment.CenterVertically)
                                .pointerHoverIcon(
                                    PointerIcon(
                                        Cursor.getPredefinedCursor(
                                            Cursor.HAND_CURSOR
                                        )
                                    )
                                )
                        )
                    }
                }
            } else if (changelogUrl == null) {
                // If no update was found and there's no changelog, add a spacer to make version numbers align better.
                SmolTooltipArea(
                    tooltip = { SmolTooltipText("Up to date!") }
                ) {
                    Spacer(
                        modifier = Modifier.width(SmolTheme.modUpdateIconSize.dp).height(SmolTheme.modUpdateIconSize.dp)
                            .padding(end = 8.dp)
                            .align(Alignment.CenterVertically)
                    )
                }
            }
        } else if (highestLocalVersion == null) {
            // If Version Checker isn't supported
            SmolTooltipArea(
                tooltip = { SmolTooltipText("This mod does not support Version Checker.\nPlease visit the mod page to manually find updates.") }
            ) {
                Image(
                    painter = painterResource("icon-yawn.svg"),
                    contentDescription = null,
                    colorFilter = ColorFilter
                        .colorMatrix(ColorMatrix().apply {
                            setToSaturation(0F)
                        }),
                    alpha = alpha,
                    modifier = Modifier.width(SmolTheme.modUpdateIconSize.dp).height(SmolTheme.modUpdateIconSize.dp)
                        .padding(end = 8.dp)
                        .align(Alignment.CenterVertically)
                )
            }
        } else {
            // If Version Checker is supported but checking for the online version failed.
            SmolTooltipArea(
                tooltip = { SmolTooltipText("This mod supports Version Checker but there was a problem checking the online version.\nPlease visit the mod page to manually find updates.") }
            ) {
                Icon(
                    painter = painterResource("icon-exclamation.svg"),
                    contentDescription = null,
                    tint = LocalContentColor.current.copy(alpha = alpha),
                    modifier = Modifier.width(SmolTheme.modUpdateIconSize.dp).height(SmolTheme.modUpdateIconSize.dp)
                        .padding(end = 8.dp)
                        .align(Alignment.CenterVertically)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppScope.ChangelogIcon(
    onlineVersionInfo: VersionCheckerInfo?,
    mod: Mod,
    hasUpdate: Boolean,
    alpha: Float,
    changelogUrl: String,
    modifier: Modifier = Modifier
) {
    var changelog by remember { mutableStateOf("Loading...") }

    // Download text from changelogUrl.
    LaunchedEffect(changelogUrl) {
        runCatching {
            SL.httpClientBuilder.invoke().use { httpClient ->
                // Get the changelog and make each line starting with "version" a header.
                changelog = httpClient.get(changelogUrl).bodyAsText()
            }
        }.onFailure {
            // CancellationException is expected when the view goes off-screen.
            if (it !is CancellationException) {
                Timber.w(it)
                changelog = "Failed to load changelog.\n${it.message}}"
            }
        }
    }

    val changelogView = @Composable {
//        if (changelogUrl.endsWith(".md"))
//            Markdown(content = changelog)
//        else
        Text(
            text = buildAnnotatedString {
                changelog.lines().forEach { line ->
                    if (line.trimStart().startsWith("version", ignoreCase = true)) {
                        appendLine(
                            AnnotatedString(
                                line,
                                SpanStyle(color = MaterialTheme.colors.secondary, fontWeight = FontWeight.Bold)
                            )
                        )
                    } else {
                        appendLine(AnnotatedString(line))
                    }
                }
            },
            fontSize = 16.sp
        )
    }

    SmolTooltipArea(tooltip = {
        SmolTooltipBackground(
            modifier = modifier.width(800.dp).height(500.dp),
        ) {
            Column {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().height(SmolTheme.modUpdateIconSize.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource("icon-bullhorn-variant.svg"),
                        contentDescription = null,
                        tint = SmolTheme.dimmedIconColor(),
                        modifier = Modifier.size(SmolTheme.modUpdateIconSize.dp)
                            .padding(end = 8.dp)
                    )
                    Text(
                        "Click to see full changelog.\n",
                        style = TextStyle(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Text(
                    modifier = Modifier.padding(bottom = 16.dp),
                    text = buildAnnotatedString {
                        appendLine(
                            AnnotatedString(
                                "Changelog",
                                SpanStyle(
                                    fontStyle = SmolTheme.alertDialogTitle().fontStyle,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        )
                        appendLine("----------------")
                    }
                )
                changelogView()
            }
        }
    }, modifier = modifier) {
        Icon(
            painter = painterResource("icon-bullhorn-variant.svg"),
            contentDescription = null,
//            colorFilter = ColorFilter.tint(color = if (hasUpdate) MaterialTheme.colors.secondary else MaterialTheme.colors.primary),
            tint =
            if (hasUpdate) MaterialTheme.colors.secondary
            else LocalContentColor.current.copy(alpha = alpha),
            modifier = modifier
                .width(SmolTheme.modUpdateIconSize.dp)
                .height(SmolTheme.modUpdateIconSize.dp)
                .padding(end = 8.dp)
                .pointerHoverIcon(
                    PointerIcon(
                        Cursor.getPredefinedCursor(
                            Cursor.HAND_CURSOR
                        )
                    )
                )
                .onClick {
                    alertDialogSetter {
                        SmolScrollableDialog(
                            onDismissRequest = { dismissAlertDialog() },
                            title = {
                                Text(
                                    text = "${mod.findFirstEnabledOrHighestVersion?.modInfo?.name} Changelog",
                                    style = SmolTheme.alertDialogTitle()
                                )
                            }) {
                            SelectionContainer {
                                changelogView()
                            }
                        }
                    }
                }
        )
    }
}