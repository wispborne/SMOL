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

package smol.app.toolbar

import AppScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.replaceCurrent
import kotlinx.coroutines.*
import org.tinylog.Logger
import smol.access.Constants
import smol.access.SL
import smol.app.Logging
import smol.app.composables.*
import smol.app.navigation.Screen
import smol.app.themes.SmolTheme
import smol.app.util.doesGamePathExist
import smol.app.util.isModBrowserEnabled
import smol.app.util.isModProfilesEnabled
import smol.app.util.openInDesktop
import smol.timber.ktx.Timber
import smol.utilities.IOLock
import smol.utilities.IOLocks
import smol.utilities.runCommandInTerminal
import smol.utilities.weightedRandom
import java.awt.FileDialog
import java.nio.file.Path
import java.util.prefs.Preferences
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
        toolsDropdown()
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
    "I am a leaf on the wind. Watch how I soar." to 4f,
    "Alfonzo put me in the screenshot" to 1f
)

data class StarsectorLaunchPrefs(
    val isFullscreen: Boolean,
    val resolution: String,
    val hasSound: Boolean
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppScope.launchButton(modifier: Modifier = Modifier) {
    val launchText = remember { launchQuotes.weightedRandom() }

    SmolTooltipArea(
        tooltipPlacement = TooltipPlacement.CursorPoint(
            offset = DpOffset(x = 8.dp, y = 8.dp),
            alignment = Alignment.BottomEnd
        ),
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
                SL.gamePathManager.path.value?.openInDesktop()

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

@OptIn(ExperimentalMaterialApi::class)
private fun AppScope.launchStarsector() {
    // Game crashes with missing OpenAL unless we use Direct Launch and provide an absolute path to OpenAL.
    // No idea why.
    val directLaunch = true
    if (directLaunch) {
        val workingDir = SL.gamePathManager.getGameCoreFolderPath()
        val starsectorCoreDir = SL.gamePathManager.getGameCoreFolderPath()
        val gameLauncher = SL.gamePathManager.path.value?.resolve("jre/bin/java.exe")?.normalize()
        val (vmparams, launchPrefs) = runCatching {
            val vmp = getCurrentVmParams()

            vmp to getStarsectorLaunchPrefs()
        }
            .onFailure { Timber.w(it) }
            .getOrElse { ex ->
                alertDialogSetter.invoke {
                    SmolAlertDialog(
                        onDismissRequest = { alertDialogSetter.invoke(null) },
                        text = { Text(text = ex.toString(), style = SmolTheme.alertDialogBody()) },
                        confirmButton = { SmolButton(onClick = { alertDialogSetter.invoke(null) }) { Text("Ok") } }
                    )
                }
                null to null
            }
        if (vmparams != null && launchPrefs != null) {
            val overrideArgs = generateVmparamOverrides(launchPrefs, starsectorCoreDir, vmparams)

            CoroutineScope(Job()).launch(Dispatchers.IO) {
                runCommandInTerminal(
                    args = listOf(gameLauncher?.absolutePathString() ?: "missing game path")
                            + overrideArgs.map { it.key + "=" + it.value } + vmparams
                        .filter { vanillaParam ->
                            // Remove any vanilla params that we're overriding.
                            overrideArgs.none { overrideArg ->
                                vanillaParam.startsWith(
                                    overrideArg.key
                                )
                            }
                        },
                    workingDirectory = workingDir?.toFile()
                )
            }
        }
    } else {
        val workingDir = SL.gamePathManager.getGameCoreFolderPath()!!
        val gameJava = SL.gamePathManager.path.value?.resolve("jre/bin/java.exe")!!
        val gameLauncher = SL.gamePathManager.path.value?.resolve("starsector-core/starsector.bat")
        CoroutineScope(Job()).launch(Dispatchers.IO) {
            runCommandInTerminal(
                args = listOf(gameJava.absolutePathString(), "-cp", *getCurrentVmParams().toTypedArray()),
                launchInNewWindow = true,
                workingDirectory = workingDir.toFile()
            )
        }
    }
}

private fun getCurrentVmParams() = IOLock.read(IOLocks.gameMainFolderLock) {
    (SL.gamePathManager.path.value?.resolve("vmparams")?.readText()
        ?.removePrefix("java.exe")
        ?.split(' ') ?: emptyList())
        .filter { it.isNotBlank() }
}

private fun generateVmparamOverrides(
    launchPrefs: StarsectorLaunchPrefs,
    starsectorCoreDir: Path?,
    vanillaVmparams: List<String>
): Map<String, String?> {
    val vmparamsKeysToAbsolutize = listOf(
        "-Djava.library.path",
        "-Dcom.fs.starfarer.settings.paths.saves",
        "-Dcom.fs.starfarer.settings.paths.screenshots",
        "-Dcom.fs.starfarer.settings.paths.mods",
        "-Dcom.fs.starfarer.settings.paths.logs",
    )

    val overrideArgs = mapOf(
        "-DlaunchDirect" to "true",
        "-DstartFS" to launchPrefs.isFullscreen.toString(),
        "-DstartSound" to launchPrefs.hasSound.toString(),
        "-DstartRes" to launchPrefs.resolution
    ) + vmparamsKeysToAbsolutize
        .mapNotNull { key ->
            // Look through vmparams for the matching key, grab the value of it, and treat it as a relative path
            // to return an absolute one.
            key to starsectorCoreDir?.resolve(
                (vanillaVmparams.firstOrNull { it.startsWith("$key=") } ?: return@mapNotNull null)
                    .split("=").getOrNull(1) ?: return@mapNotNull null
            )?.normalize()?.absolutePathString()
        }
    return overrideArgs
}

private fun getStarsectorLaunchPrefs(): StarsectorLaunchPrefs {
    Preferences.userRoot().node("com").node("fs").node("starfarer").let { prefs ->
        Timber.i {
            "Reading Starsector settings from Registry:\n${
                prefs.keys().joinToString(separator = "\n") { key ->
                    "$key: ${
                        if (key == "serial") "REDACTED" else prefs.get(
                            key,
                            "(no value found)"
                        )
                    }"
                }
            }"
        }
        return StarsectorLaunchPrefs(
            isFullscreen = prefs.getBoolean("fullscreen", false),
            resolution = prefs.get("resolution", "1920x1080"),
            hasSound = prefs.getBoolean("sound", true)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppScope.installModsButton(modifier: Modifier = Modifier) {
    SmolTooltipArea(
        tooltipPlacement = TooltipPlacement.CursorPoint(
            offset = DpOffset(x = 8.dp, y = 8.dp),
            alignment = Alignment.BottomEnd
        ),
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
                                        destinationFolder = destinationFolder,
                                        promptUserToReplaceExistingFolder = {
                                            duplicateModAlertDialogState.showDialogBooleo(
                                                it
                                            )
                                        }
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
        tooltipPlacement = TooltipPlacement.CursorPoint(
            offset = DpOffset(x = 8.dp, y = 8.dp),
            alignment = Alignment.BottomEnd
        ),
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
        tooltipPlacement = TooltipPlacement.CursorPoint(
            offset = DpOffset(x = 8.dp, y = 8.dp),
            alignment = Alignment.BottomEnd
        ),
        tooltip = {
            SmolTooltipText(
                when {
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
        tooltipPlacement = TooltipPlacement.CursorPoint(
            offset = DpOffset(x = 8.dp, y = 8.dp),
            alignment = Alignment.BottomEnd
        ),
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
            isSelected = isSelected,
            modifier = Modifier.padding(top = 13.dp)
        ) {
            Column {
                Text("Profiles", modifier = Modifier)
                Text(
                    text = SL.userManager.activeProfile.collectAsState().value.activeModProfile.name,
                    style = MaterialTheme.typography.caption,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterHorizontally).widthIn(max = 80.dp),
                )
            }
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
fun AppScope.toolsDropdown(modifier: Modifier = Modifier) {
    val gamePath = SL.gamePathManager.path.collectAsState().value

    SmolDropdownWithButton(
        shouldShowSelectedItemInMenu = false,
        canSelectItems = false,
        modifier = modifier
            .padding(start = 16.dp),
        customButtonContent = { _: SmolDropdownMenuItem, isExpanded: Boolean, _: (Boolean) -> Unit ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
            ) {
                Icon(
                    painter = painterResource("icon-toolbox.svg"),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
//                Text(text = "Tools", modifier = Modifier.padding(start = 4.dp))
                SmolDropdownArrow(
                    modifier = Modifier
                        .align(Alignment.CenterVertically),
                    expanded = isExpanded
                )
            }
        },
        items = listOf(
            SmolDropdownMenuItemTemplate(
                text = "In-game Tips",
                iconPath = "icon-tips.svg",
                isEnabled = gamePath?.isReadable() == true,
                onClick = {
                    router.replaceCurrent(Screen.Tips)
                    true
                }
            ),
        ))
}

@Composable
fun AppScope.quickLinksDropdown(modifier: Modifier = Modifier) {
    val gamePath = SL.gamePathManager.path.collectAsState().value
    val savesPath = gamePath?.resolve(Constants.SAVES_FOLDER_NAME)
    val modsPath = gamePath?.resolve(Constants.MODS_FOLDER_NAME)
    val logPath = gamePath?.let {
        runCatching { Constants.getGameLogPath(gamePath) }.onFailure { Timber.w(it) }.getOrNull()
    }
    val smolLog = Logging.logPath
    val backupsPath = SL.backupManager.folderPath

    SmolDropdownWithButton(
        shouldShowSelectedItemInMenu = false,
        canSelectItems = false,
        modifier = modifier
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
                    true
                }
            ),
            SmolDropdownMenuItemTemplate(
                text = "Mods" + if (modsPath?.isReadable() != true) " (not found)" else "",
                iconPath = "icon-folder-mods.svg",
                isEnabled = modsPath?.isReadable() == true,
                onClick = {
                    modsPath?.openInDesktop()
                    true
                }
            ),
            SmolDropdownMenuItemTemplate(
                text = "Saves" + if (savesPath?.isReadable() != true) " (not found)" else "",
                iconPath = "icon-folder-saves.svg",
                isEnabled = savesPath?.isReadable() == true,
                onClick = {
                    savesPath?.openInDesktop()
                    true
                }
            ),
            SmolDropdownMenuItemTemplate(
                text = "Starsector Log" + if (logPath?.isReadable() != true) " (not found)" else "",
                iconPath = "icon-file-debug.svg",
                isEnabled = logPath?.isReadable() == true,
                onClick = {
                    logPath?.openInDesktop()
                    true
                }
            ),
            SmolDropdownMenuItemTemplate(
                text = "SMOL Log",
                iconPath = "icon-file-debug.svg",
                isEnabled = smolLog?.isReadable() == true,
                onClick = {
                    smolLog?.openInDesktop()
                    true
                }
            ),
            SmolDropdownMenuItemTemplate(
                text = "Mod Backups",
                iconPath = "icon-file-history.svg",
                isEnabled = backupsPath?.isReadable() == true,
                onClick = {
                    backupsPath?.openInDesktop()
                    true
                }
            )
        )
    )
}