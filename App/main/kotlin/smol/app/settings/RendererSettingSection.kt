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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import smol.access.SL
import smol.access.config.AppConfig
import smol.app.composables.*
import smol.app.themes.SmolTheme
import smol.utilities.Platform
import smol.utilities.currentPlatform

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun AppScope.rendererSettingSection(scope: CoroutineScope, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(text = "Renderer", style = SettingsView.settingLabelStyle())
        Text(
            text = "(restart required to change)",
            style = SettingsView.settingLabelStyle(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal
        )

        val rendererSetting by remember { mutableStateOf(SL.appConfig.renderer) }

        var showMacOsWarning by remember { mutableStateOf(false) }

        if (showMacOsWarning) {
            SmolAlertDialog(
                text = { SmolText("DirectX is a Windows renderer and you are on MacOS.\nAre you sure you want to switch?") },
                confirmButton = { SmolButton(onClick = { showMacOsWarning = false }) { Text("Nope") } },
                dismissButton = { SmolSecondaryButton(onClick = { showMacOsWarning = false }) { Text("No") } },
                onDismissRequest = { showMacOsWarning = false }
            )
        }

        var showWindowsWarning by remember { mutableStateOf(false) }

        if (showWindowsWarning) {
            SmolAlertDialog(
                text = { SmolText("Metal is a MacOS renderer and you are on Windows.\nAre you sure you want to switch?") },
                confirmButton = { SmolButton(onClick = { showWindowsWarning = false }) { Text("Nope") } },
                dismissButton = { SmolSecondaryButton(onClick = { showWindowsWarning = false }) { Text("No") } },
                onDismissRequest = { showWindowsWarning = false }
            )
        }

        @Composable
        fun rendererSettingsMenuItem(renderer: AppConfig.Renderer, tooltip: String) = SmolDropdownMenuItemCustom(
            onClick = {
                if (currentPlatform == Platform.Windows && renderer == AppConfig.Renderer.Metal) {
                    showWindowsWarning = true
                    false
                } else if (currentPlatform == Platform.MacOS && renderer == AppConfig.Renderer.DirectX) {
                    showMacOsWarning = true
                    false
                } else {
                    SL.appConfig.renderer = renderer.name
                    true
                }
            }
        ) {
            SmolTooltipArea(
                tooltip = {
                    SmolTooltipText(
                        tooltip,
                        fontFamily = SmolTheme.normalFont,
                        color = MaterialTheme.colors.onBackground
                    )
                }
            ) {
                SmolText(
                    text = renderer.name
                )
            }
        }

        SmolDropdownWithButton(
            modifier = Modifier.padding(top = 4.dp),
            initiallySelectedIndex = when (rendererSetting) {
                AppConfig.Renderer.OpenGL.name -> 1
                AppConfig.Renderer.DirectX.name -> 2
                AppConfig.Renderer.Metal.name -> 3
                else -> 0
            },
            items = listOf(
                rendererSettingsMenuItem(
                    AppConfig.Renderer.Default,
                    "Let Skia pick automatically based on your OS."
                ),
                rendererSettingsMenuItem(
                    AppConfig.Renderer.OpenGL,
                    "Recommended for Windows. Works well on MacOS.\nWindows: OpenGL doesn't have as many issues with VRR (G-Sync, FreeSync) that DirectX seems to (flickering, not running at high refresh rate)."
                ),
                rendererSettingsMenuItem(
                    AppConfig.Renderer.DirectX,
                    "Windows's renderer. SMOL uses OpenGL by default for Windows to avoid some flickering issues on displays with G-Sync/FreeSync, but DirectX is an option if OpenGL causes problems."
                ),
                rendererSettingsMenuItem(
                    AppConfig.Renderer.Metal,
                    "Recommended for MacOS.\nMacOS's default renderer."
                ),
            ),
            shouldShowSelectedItemInMenu = true
        )
    }
}