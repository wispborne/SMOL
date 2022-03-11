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

package smol_app.home

import AppScope
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.mouseClickable
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.replaceCurrent
import smol_access.Constants
import smol_access.model.Mod
import smol_access.model.VersionCheckerInfo
import smol_app.composables.SmolTooltipArea
import smol_app.composables.SmolTooltipText
import smol_app.navigation.Screen
import smol_app.util.*
import timber.ktx.Timber
import java.awt.Cursor

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppScope.ModUpdateIcon(
    modifier: Modifier = Modifier,
    highestLocalVersion: VersionCheckerInfo.Version?,
    onlineVersion: VersionCheckerInfo.Version?,
    onlineVersionInfo: VersionCheckerInfo?,
    mod: Mod
) {
    Row(modifier) {
        if (highestLocalVersion != null && onlineVersion != null && onlineVersion > highestLocalVersion && onlineVersionInfo != null) {
            val ddUrl =
                onlineVersionInfo.directDownloadURL?.ifBlank { null }
                    ?: mod.findHighestVersion?.versionCheckerInfo?.directDownloadURL
            if (ddUrl != null) {
                SmolTooltipArea(tooltip = {
                    SmolTooltipText(
                        text = buildString {
                            append("Newer version available: ${onlineVersionInfo.modVersion}")
                            append("\nCurrent version: $highestLocalVersion.")
                            append("\n\nClick to download and update.")
                        }
                    )
                }, modifier = Modifier.mouseClickable {
                    if (this.buttons.isPrimaryPressed) {
                        alertDialogSetter {
                            DirectDownloadAlertDialog(
                                ddUrl = ddUrl,
                                mod = mod,
                                onlineVersion = onlineVersion
                            )
                        }
                    }
                }
                    .align(androidx.compose.ui.Alignment.CenterVertically)) {
                    Image(
                        painter = painterResource("icon-direct-install.svg"),
                        contentDescription = null,
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(color = androidx.compose.material.MaterialTheme.colors.secondary),
                        modifier = Modifier.width(28.dp).height(28.dp)
                            .padding(end = 8.dp)
                            .align(androidx.compose.ui.Alignment.CenterVertically)
                            .pointerHoverIcon(
                                PointerIcon(
                                    java.awt.Cursor.getPredefinedCursor(
                                        java.awt.Cursor.HAND_CURSOR
                                    )
                                )
                            )
                    )
                }
            }

            val modThreadId =
                mod.findHighestVersion?.versionCheckerInfo?.modThreadId
            val hasModThread = modThreadId?.isNotBlank() == true
            SmolTooltipArea(tooltip = {
                SmolTooltipText(
                    text = buildAnnotatedString {
                        append("Newer version available: ${onlineVersionInfo.modVersion}.")
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
            }, modifier = Modifier.mouseClickable {
                if (this.buttons.isPrimaryPressed) {
                    if (hasModThread) {
                        if (smol_access.Constants.isModBrowserEnabled()) {
                            router.replaceCurrent(smol_app.navigation.Screen.ModBrowser(modThreadId?.getModThreadUrl()))
                        } else {
                            kotlin.runCatching {
                                modThreadId?.getModThreadUrl()
                                    ?.openAsUriInBrowser()
                            }
                                .onFailure { timber.ktx.Timber.w(it) }
                        }
                    } else {
                        createGoogleSearchFor("starsector ${mod.findHighestVersion?.modInfo?.name}")
                            .openAsUriInBrowser()
                    }
                }
            }
                .align(androidx.compose.ui.Alignment.CenterVertically)) {
                Image(
                    painter = painterResource(
                        if (hasModThread) "icon-new-update.svg"
                        else "icon-new-update-search.svg"
                    ),
                    contentDescription = null,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                        color =
                        if (ddUrl == null) androidx.compose.material.MaterialTheme.colors.secondary
                        else androidx.compose.material.MaterialTheme.colors.secondary.copy(alpha = androidx.compose.material.ContentAlpha.disabled)
                    ),
                    modifier = Modifier.width(28.dp).height(28.dp)
                        .padding(end = 8.dp)
                        .align(androidx.compose.ui.Alignment.CenterVertically)
                        .pointerHoverIcon(
                            PointerIcon(
                                java.awt.Cursor.getPredefinedCursor(
                                    java.awt.Cursor.HAND_CURSOR
                                )
                            )
                        )
                )
            }
        }
    }
}