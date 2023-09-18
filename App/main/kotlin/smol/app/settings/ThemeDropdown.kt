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

package smol.app.settings

import AppScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import smol.access.SL
import smol.app.composables.SmolClickableText
import smol.app.composables.SmolDropdownMenuItem
import smol.app.composables.SmolDropdownMenuItemCustom
import smol.app.composables.SmolDropdownWithButton
import smol.app.themes.SmolTheme
import smol.app.themes.SmolTheme.hyperlink
import smol.app.themes.SmolTheme.toColors
import smol.app.util.openInDesktop

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppScope.themeDropdown(modifier: Modifier = Modifier): String {
    var themeName by remember { mutableStateOf(SL.themeManager.activeTheme.value.first) }
    val themes = SL.themeManager.getThemes()
        .entries
        .sortedBy { it.key.lowercase() }
    val recomposeScope = currentRecomposeScope

    Column(modifier) {
        Text(text = "Theme", modifier = Modifier.padding(bottom = 8.dp), style = SettingsView.settingLabelStyle())
        Row {
            SmolDropdownWithButton(
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                items = themes
                    .map { entry ->
                        val colors = entry.value.toColors()
                        val isActive = SL.themeManager.activeTheme.value.first == entry.key

                        SmolDropdownMenuItemCustom(
                            backgroundColor = colors.surface,
                            border = if (isActive)
                                SmolDropdownMenuItem.Border(
                                    borderStroke = BorderStroke(width = 2.dp, color = colors.onSurface),
                                    shape = SmolTheme.smolNormalButtonShape()
                                )
                            else null,
                            onClick = {
                                themeName = entry.key
                                SL.themeManager.setActiveTheme(entry.key)
                                true
                            },
                            customItemContent = { isMenuButton ->
                                val height = 24.dp
                                Text(
                                    text = entry.key,
                                    modifier = Modifier
                                        .padding(vertical = 8.dp)
                                        .run { if (!isMenuButton) this.weight(1f) else this }
                                        .align(Alignment.CenterVertically),
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = SmolTheme.orbitronSpaceFont,
                                    color = colors.onSurface
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(start = 16.dp)
                                        .width(height * 3)
                                        .height(height)
                                        .align(Alignment.CenterVertically)
                                        .background(color = colors.primary)
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .width(height)
                                        .height(height)
                                        .align(Alignment.CenterVertically)
                                        .background(color = colors.secondary)
                                )
                            }
                        )
                    },
                initiallySelectedIndex = themes.map { it.key }.indexOf(themeName).coerceAtLeast(0),
                canSelectItems = true
            )
            SmolClickableText(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .align(Alignment.CenterVertically),
                text = "Edit",
                color = MaterialTheme.colors.hyperlink,
                textDecoration = TextDecoration.Underline,
                onClick = { SL.themeManager.editTheme(SL.themeManager.activeTheme.value.first).openInDesktop() }
            )
            SmolClickableText(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .align(Alignment.CenterVertically),
                text = "Refresh",
                color = MaterialTheme.colors.hyperlink,
                textDecoration = TextDecoration.Underline,
                onClick = {
                    SL.themeManager.reloadThemes()
                    recomposeScope.invalidate()
                }
            )
        }
    }

    return themeName
}