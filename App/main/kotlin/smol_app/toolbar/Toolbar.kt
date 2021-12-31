@file:OptIn(ExperimentalFoundationApi::class)

package smol_app.toolbar

import AppState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.push
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.tinylog.Logger
import smol_access.Constants
import smol_access.SL
import smol_app.composables.SmolButton
import smol_app.composables.SmolTooltipArea
import smol_app.composables.SmolTooltipText
import smol_app.navigation.Screen
import smol_app.themes.SmolTheme
import smol_app.themes.SmolTheme.withAdjustedBrightness
import smol_app.util.isJCEFEnabled
import smol_app.util.openProgramInTerminal
import utilities.toFileOrNull
import utilities.toPathOrNull
import utilities.weightedRandom
import java.awt.FileDialog
import kotlin.io.path.absolutePathString


@Composable
fun AppState.settingsButton() {
    SmolButton(
        onClick = { router.push(Screen.Settings) },
        modifier = Modifier.padding(start = 16.dp)
    ) {
        Text("Settings")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppState.modBrowserButton() {
    val isEnabled = Constants.isJCEFEnabled()
    SmolTooltipArea(
        tooltip = {
            SmolTooltipText(
                if (isEnabled) "View and install mods from the internet."
                else "JCEF not found; add to /libs to enable the Mod Browser."
            )
        },
        delayMillis = SmolTooltipArea.delay
    ) {
        SmolButton(
            enabled = isEnabled,
            onClick = { router.push(Screen.ModBrowser()) },
            modifier = Modifier.padding(start = 16.dp)
        ) {
            Text("Mod Browser")
        }
    }
}

@Composable
fun AppState.profilesButton() {
    SmolTooltipArea(
        tooltip = { SmolTooltipText("Create and swap between enabled mods.") },
        delayMillis = SmolTooltipArea.delay
    ) {
        SmolButton(
            onClick = { router.push(Screen.Profiles) },
            modifier = Modifier.padding(start = 16.dp)
        ) {
            Text("Profiles")
        }
    }
}

@Composable
fun AppState.homeButton(modifier: Modifier = Modifier) {
    SmolTooltipArea(
        tooltip = { SmolTooltipText("View and change mods.") },
        delayMillis = SmolTooltipArea.delay
    ) {
        SmolButton(
            onClick = { router.push(Screen.Home) },
            modifier = modifier.padding(start = 16.dp)
        ) {
            Text("Home")
        }
    }
}

val sayings = listOf(
    "Engage!" to 10f,
    "Make it so." to 10f,
    "Onward!" to 10f,
    "To infinity, and beyond!" to 8f,
    "Execute." to 10f,
    "Take us out." to 8f,
    "Punch it, Chewie." to 5f,
    "Let's fly!" to 7f,
    "I am a leaf on the wind. Watch how I soar." to 4f
)

@Composable
fun AppState.launchButton() {
    val launchText = remember { sayings.weightedRandom() }
    SmolTooltipArea(
        tooltip = { SmolTooltipText(launchText) },
        delayMillis = SmolTooltipArea.delay
    ) {
        SmolButton(
            onClick = {
                val gameLauncher = SL.appConfig.gamePath.toPathOrNull()?.resolve("starsector.exe")
                Logger.info { "Launching ${gameLauncher?.absolutePathString()} with working dir ${SL.appConfig.gamePath}." }
                openProgramInTerminal(
                    gameLauncher?.absolutePathString() ?: "missing",
                    SL.appConfig.gamePath.toFileOrNull()
                )
            },
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
            modifier = Modifier
                .padding(start = 16.dp)
                .border(
                    8.dp,
                    MaterialTheme.colors.primary.withAdjustedBrightness(-35),
                    shape = SmolTheme.smolFullyClippedButtonShape()
                ),
            shape = SmolTheme.smolFullyClippedButtonShape(),
            elevation = ButtonDefaults.elevation(
                defaultElevation = 4.dp,
                hoveredElevation = 8.dp,
                pressedElevation = 16.dp
            )
        ) {
            Text(text = "Launch", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun AppState.installModsButton(modifier: Modifier = Modifier) {
    SmolTooltipArea(
        tooltip = { SmolTooltipText(text = "Install mod(s).") },
        delayMillis = SmolTooltipArea.delay
    ) {
        SmolButton(
            onClick = {
                with(
                    FileDialog(this.window, "Choose an archive or mod_info.json", FileDialog.LOAD)
                        .apply {
                            this.isMultipleMode = true
                            this.directory = SL.appConfig.lastFilePickerDirectory
                            this.isVisible = true
                        })
                {
                    SL.appConfig.lastFilePickerDirectory = this.directory

                    this.files
                        .map { it.toPath() }
                        .onEach { Logger.debug { "Chosen file: $it" } }
                        .forEach {
                            GlobalScope.launch {
                                SL.access.installFromUnknownSource(inputFile = it, shouldCompressModFolder = true)
                            }
                        }
                }
            },
            modifier = modifier.padding(start = 16.dp)
        ) {
            Text("Install Mods")
//            Icon(
//                painter = painterResource("plus.svg"),
//                contentDescription = null,
//                tint = SmolTheme.dimmedIconColor()
//            )
        }
    }
}
