package smol_app.settings

import AppState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.mouseClickable
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import smol_access.SL
import smol_app.composables.SmolDropdownMenuItem
import smol_app.composables.SmolDropdownMenuItemCustom
import smol_app.composables.SmolDropdownWithButton
import smol_app.composables.SmolLinkText
import smol_app.themes.SmolTheme
import smol_app.themes.SmolTheme.toColors
import smol_app.util.openInDesktop

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppState.themeDropdown(modifier: Modifier = Modifier): String {
    var themeName by remember { mutableStateOf(SL.themeManager.activeTheme.value.first) }
    val themes = SL.themeManager.getThemes()
        .entries
        .sortedBy { it.key.lowercase() }
    val recomposeScope = currentRecomposeScope

    Column(modifier) {
        Text(text = "Theme", style = SettingsView.settingLabelStyle())
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
            SmolLinkText(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .align(Alignment.CenterVertically)
                    .mouseClickable { SL.themeManager.editTheme(SL.themeManager.activeTheme.value.first).openInDesktop() }, text = "Edit"
            )
            SmolLinkText(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .align(Alignment.CenterVertically)
                    .mouseClickable {
                        SL.themeManager.reloadThemes()
                        recomposeScope.invalidate()
                    },
                text = "Refresh"
            )
        }
    }

    return themeName
}