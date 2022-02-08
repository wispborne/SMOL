@file:OptIn(ExperimentalFoundationApi::class)

package smol_app.toolbar

import AppScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import smol_app.util.isJCEFEnabled
import smol_app.util.isModBrowserEnabled
import smol_app.util.isModProfilesEnabled
import utilities.exists
import utilities.runCommandInTerminal
import utilities.weightedRandom
import java.awt.FileDialog
import kotlin.io.path.absolutePathString


@Composable
fun AppScope.settingsButton() {
    SmolButton(
        onClick = { router.push(Screen.Settings) },
        modifier = Modifier.padding(start = 16.dp)
    ) {
        Text("Settings")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppScope.modBrowserButton() {
    val isEnabled = Constants.isModBrowserEnabled()
    SmolTooltipArea(
        tooltip = {
            SmolTooltipText(
                when {
                    !Constants.isJCEFEnabled() -> "JCEF not found; add to /libs to enable the Mod Browser."
                    !SL.gamePathManager.path.value.exists() -> "Set a valid game path."
                    else -> "View and install mods from the internet."
                }
            )
        },
        delayMillis = SmolTooltipArea.shortDelay
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
fun AppScope.profilesButton() {
    SmolTooltipArea(
        tooltip = {
            SmolTooltipText(
                text = when {
                    !SL.gamePathManager.path.value.exists() -> "Set a valid game path."
                    else -> "Create and swap between enabled mods."
                }
            )
        },
        delayMillis = SmolTooltipArea.shortDelay
    ) {
        SmolButton(
            onClick = { router.push(Screen.Profiles) },
            enabled = Constants.isModProfilesEnabled(),
            modifier = Modifier.padding(start = 16.dp)
        ) {
            Text("Profiles")
        }
    }
}

@Composable
fun AppScope.homeButton(modifier: Modifier = Modifier) {
    SmolTooltipArea(
        tooltip = { SmolTooltipText("View and change mods.") },
        delayMillis = SmolTooltipArea.shortDelay
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
fun AppScope.launchButton() {
    val launchText = remember { sayings.weightedRandom() }
    SmolTooltipArea(
        tooltip = {
            SmolTooltipText(
                when {
                    !SL.gamePathManager.path.value.exists() -> "Set a valid game path."
                    else -> launchText
                }
            )
        },
        delayMillis = SmolTooltipArea.shortDelay
    ) {
        SmolButton(
            onClick = {
                val gameLauncher = SL.gamePathManager.path.value?.resolve("starsector.exe")
                Logger.info { "Launching ${gameLauncher?.absolutePathString()} with working dir ${SL.gamePathManager.path.value}." }
                runCommandInTerminal(
                    command = ("\"${gameLauncher?.absolutePathString() ?: "missing game path"}\""),
                    workingDirectory = SL.gamePathManager.path.value?.toFile()
                )
            },
            enabled = SL.gamePathManager.path.value.exists(),
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
            modifier = Modifier
                .padding(start = 16.dp),
            border = BorderStroke(
                width = 4.dp,
                color = MaterialTheme.colors.primaryVariant
            ),
            shape = SmolTheme.smolFullyClippedButtonShape(),
            elevation = ButtonDefaults.elevation(
                defaultElevation = 4.dp,
                hoveredElevation = 8.dp,
                pressedElevation = 16.dp
            )
        ) {
            Text(
                text = "Launch",
                modifier = Modifier.padding(4.dp),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun AppScope.installModsButton(modifier: Modifier = Modifier) {
    SmolTooltipArea(
        tooltip = { SmolTooltipText(text = "Select one or more mod archives or mod_info.json files.") },
        delayMillis = SmolTooltipArea.shortDelay
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
                                val destinationFolder = SL.gamePathManager.getModsPath()
                                if (destinationFolder != null) {
                                    SL.access.installFromUnknownSource(
                                        inputFile = it,
                                        destinationFolder = destinationFolder
                                    )
                                    SL.access.reload()
                                }
                            }
                        }
                }
            },
            modifier = modifier.padding(start = 16.dp)
        ) {
            Text("Install Mods")
//            Icon(
//                painter = painterResource("icon-plus.svg"),
//                contentDescription = null,
//                tint = SmolTheme.dimmedIconColor()
//            )
        }
    }
}
