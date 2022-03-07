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

@file:OptIn(ExperimentalFoundationApi::class)

package smol_app.toolbar

import AppScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.replaceCurrent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.tinylog.Logger
import smol_access.Constants
import smol_access.SL
import smol_app.composables.*
import smol_app.navigation.Screen
import smol_app.themes.SmolTheme
import smol_app.util.*
import timber.ktx.Timber
import utilities.runCommandInTerminal
import utilities.weightedRandom
import java.awt.FileDialog
import kotlin.io.path.absolutePathString
import kotlin.io.path.isReadable
import kotlin.io.path.readText

@Composable
fun tabButton(
    modifier: Modifier = Modifier, forceDisabled: Boolean, isSelected: Boolean, onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Column {
        val color = MaterialTheme.colors.secondary
        TextButton(
            onClick = { if (!forceDisabled && !isSelected) onClick.invoke() },
            colors = ButtonDefaults.buttonColors(
                backgroundColor = MaterialTheme.colors.surface,
                disabledBackgroundColor = MaterialTheme.colors.surface
            ),
            enabled = !forceDisabled,
            modifier = modifier
                .padding(start = 16.dp)
                .run {
                    if (isSelected) this.drawWithContent {
                        drawContent()
                        val y = size.height
                        val x = size.width
                        val strokeWidth = 2f
                        drawLine(
                            strokeWidth = strokeWidth,
                            color = color,
                            cap = StrokeCap.Square,
                            start = Offset(x = 0f, y = y),
                            end = Offset(x = x, y = y)
                        )
                    } else this
                },
        ) {
            Column {
                content.invoke()
            }
        }
    }
}

@Composable
fun AppScope.toolbar(currentScreen: Screen) {
    launchButton()
    installModsButton()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Divider(
            modifier = Modifier
                .padding(start = 24.dp, end = 8.dp)
                .size(height = 24.dp, width = 1.dp)
                .align(Alignment.CenterVertically)
                .background(color = MaterialTheme.colors.onSurface.copy(alpha = .3f))
        )
        homeButton(isSelected = currentScreen is Screen.Home)
        modBrowserButton(isSelected = currentScreen is Screen.ModBrowser)
        profilesButton(isSelected = currentScreen is Screen.Profiles)
        settingsButton(isSelected = currentScreen is Screen.Settings)
        quickLinksDropdown()
    }
}

val launchQuotes = listOf(
    "Engage!" to 10f,
    "Make it so." to 10f,
    "Onward!" to 10f,
    "Execute." to 10f,
    "Burn bright." to 8f,
    "To infinity, and beyond!" to 8f,
    "Take us out." to 8f,
    "Punch it, Chewie." to 5f,
    "Let's fly!" to 7f,
    "One smol step for humankind." to 5f,
    "I am a leaf on the wind. Watch how I soar." to 4f
)

@Composable
fun AppScope.launchButton(modifier: Modifier = Modifier) {
    val launchText = remember { launchQuotes.weightedRandom() }
    SmolTooltipArea(
        tooltip = {
            SmolTooltipText(
                when {
                    !Constants.doesGamePathExist() -> "Set a valid game path."
                    else -> launchText
                }
            )
        },
        delayMillis = SmolTooltipArea.shortDelay
    ) {
        SmolButton(
            onClick = {
                val directLaunch = true
                if (directLaunch) {
                    val workingDir = SL.gamePathManager.path.value?.resolve("starsector-core")
                    val gameLauncher = SL.gamePathManager.path.value?.resolve("jre/bin/java.exe")
                    val vmparams = (SL.gamePathManager.path.value?.resolve("vmparams")?.readText()
                        ?.removePrefix("java.exe")
                        ?.split(' ') ?: emptyList())
                        .filter { it.isNotBlank() }
                    CoroutineScope(Job()).launch {
                        runCommandInTerminal(
                            command = (gameLauncher?.absolutePathString() ?: "missing game path"),
                            args = listOf(
                                "-DlaunchDirect=true",
                                "-DstartFS=false",
                                "-DstartSound=true",
                                "-DstartRes=1920x1080",
//                                "-Dorg.lwjgl.util.Debug=true", // debugging for lwjgl
                                "-Djava.library.path=${workingDir?.absolutePathString()}\\native\\windows"
                            ) + vmparams.filter { !it.startsWith("-Djava.library.path") },
                            workingDirectory = workingDir?.toFile()
                        )
                    }
                } else {
                    val workingDir = SL.gamePathManager.path.value?.resolve("starsector-core")
                    val gameLauncher = SL.gamePathManager.path.value?.resolve("starsector-core/starsector.bat")
                    CoroutineScope(Job()).launch {
                        runCommandInTerminal(
                            command = (gameLauncher?.absolutePathString() ?: "missing game path"),
                            workingDirectory = workingDir?.toFile()
                        )
                    }
                }
                // Putting router here is a dumb hack that triggers a recompose when the app UI is invalidated,
                // which we want so that the button enabled state gets reevaluated.
                router
            },
            enabled = Constants.doesGamePathExist(),
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
            modifier = modifier
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
        tooltip = {
            SmolTooltipText(
                text = when {
                    !Constants.doesGamePathExist() -> "Set a valid game path."
                    else -> "Select one or more mod archives or mod_info.json files."
                }
            )
        },
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
            enabled = Constants.doesGamePathExist(),
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

@Composable
fun AppScope.homeButton(modifier: Modifier = Modifier, isSelected: Boolean) {
    SmolTooltipArea(
        tooltip = { SmolTooltipText("View and change mods.") },
        delayMillis = SmolTooltipArea.shortDelay
    ) {
        tabButton(
            isSelected = isSelected,
            forceDisabled = false,
            onClick = { router.replaceCurrent(Screen.Home) }
        ) {
            Text("Home")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppScope.modBrowserButton(modifier: Modifier = Modifier, isSelected: Boolean) {
    SmolTooltipArea(
        tooltip = {
            SmolTooltipText(
                when {
                    !Constants.isJCEFEnabled() -> "JCEF not found; add to /libs to enable the Mod Browser."
                    !Constants.doesGamePathExist() -> "Set a valid game path."
                    else -> "View and install mods from the internet."
                }
            )
        },
        delayMillis = SmolTooltipArea.shortDelay
    ) {
        tabButton(
            forceDisabled = !Constants.isModBrowserEnabled(),
            isSelected = isSelected,
            onClick = { router.replaceCurrent(Screen.ModBrowser()) }
        ) {
            Text("Mod Browser")
        }
    }
}

@Composable
fun AppScope.profilesButton(modifier: Modifier = Modifier, isSelected: Boolean) {
    SmolTooltipArea(
        tooltip = {
            SmolTooltipText(
                text = when {
                    !Constants.doesGamePathExist() -> "Set a valid game path."
                    else -> "Create and swap between enabled mods."
                }
            )
        },
        delayMillis = SmolTooltipArea.shortDelay
    ) {
        tabButton(
            onClick = { router.replaceCurrent(Screen.Profiles) },
            forceDisabled = !Constants.isModProfilesEnabled(),
            isSelected = isSelected
        ) {
            Text("Profiles")
        }
    }
}

@Composable
fun AppScope.settingsButton(modifier: Modifier = Modifier, isSelected: Boolean) {
    tabButton(
        onClick = { router.replaceCurrent(Screen.Settings) },
        forceDisabled = false,
        isSelected = isSelected
    ) {
        Text("Settings")
    }
}

@Composable
fun AppScope.quickLinksDropdown(modifier: Modifier = Modifier) {
    val gamePath = SL.gamePathManager.path.collectAsState().value
    val savesPath = gamePath?.resolve(Constants.SAVES_FOLDER_NAME)
    val modsPath = gamePath?.resolve(Constants.MODS_FOLDER_NAME)
    val logPath = gamePath?.let {
        kotlin.runCatching { Constants.getGameLogPath(gamePath) }.onFailure { Timber.w(it) }.getOrNull()
    }
    SmolDropdownWithButton(
        shouldShowSelectedItemInMenu = false,
        canSelectItems = false,
        modifier = Modifier
            .padding(start = 16.dp),
        customButtonContent = { _: SmolDropdownMenuItem, isExpanded: Boolean, _: (Boolean) -> Unit ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
            ) {
                Icon(painter = painterResource("icon-folder.svg"), contentDescription = null)
                SmolDropdownArrow(
                    modifier = Modifier
                        .align(Alignment.CenterVertically),
                    expanded = isExpanded
                )
            }
        },
        items = listOf(
            SmolDropdownMenuItemTemplate(
                text = "Starsector" + if (gamePath?.isReadable() != true) " (not found)" else "",
                iconPath = "icon-folder-game.svg",
                isEnabled = gamePath?.isReadable() == true,
                onClick = {
                    gamePath?.openInDesktop()
                }
            ),
            SmolDropdownMenuItemTemplate(
                text = "Mods" + if (modsPath?.isReadable() != true) " (not found)" else "",
                iconPath = "icon-folder-mods.svg",
                isEnabled = modsPath?.isReadable() == true,
                onClick = {
                    modsPath?.openInDesktop()
                }
            ),
            SmolDropdownMenuItemTemplate(
                text = "Saves" + if (savesPath?.isReadable() != true) " (not found)" else "",
                iconPath = "icon-folder-saves.svg",
                isEnabled = savesPath?.isReadable() == true,
                onClick = {
                    savesPath?.openInDesktop()
                }
            ),
            SmolDropdownMenuItemTemplate(
                text = "Log" + if (logPath?.isReadable() != true) " (not found)" else "",
                iconPath = "icon-file-debug.svg",
                isEnabled = logPath?.isReadable() == true,
                onClick = {
                    logPath?.openInDesktop()
                }
            )
        )
    )
}