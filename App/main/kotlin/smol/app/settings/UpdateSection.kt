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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.update4j.Configuration
import smol.access.Constants
import smol.access.SL
import smol.app.UI
import smol.app.composables.*
import smol.app.updater.UpdateSmolToast
import smol.timber.ktx.Timber
import smol.update_installer.UpdateChannel
import smol.updatestager.UpdateChannelManager
import smol.utilities.asList
import java.io.FileNotFoundException
import java.net.UnknownHostException
import kotlin.system.exitProcess

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun updateSection(scope: CoroutineScope) {
    Column(modifier = Modifier.padding(start = 16.dp, top = 24.dp)) {
        Text(text = "Updates", style = SettingsView.settingLabelStyle())
        var updateStatus by remember { mutableStateOf("") }

        fun doUpdateCheck() {
            scope.launch {
                kotlin.runCatching {
                    updateStatus =
                        if (!checkForUpdate().requiresUpdate()) {
                            "No update found."
                        } else {
                            "Update found! Check the notification to download."
                        }

                }
                    .onFailure {
                        updateStatus =
                            when (it) {
                                is UnknownHostException -> "Unable to connect to ${it.message}."
                                is FileNotFoundException ->
                                    if (UpdateChannelManager.getUpdateChannelSetting(appConfig = SL.appConfig) == UpdateChannel.Stable)
                                        "There's no stable version...yet." else
                                        "File not found: ${it.message}."
                                else -> it.toString()
                            }

                    }

                kotlin.runCatching { SL.modRepo.refreshFromInternet(SL.appConfig.updateChannel) }
                    .onFailure { Timber.w(it) }
            }
        }

        LaunchedEffect(Unit) { doUpdateCheck() }

        SmolDropdownWithButton(
            modifier = Modifier.padding(top = 4.dp),
            initiallySelectedIndex = when (UpdateChannelManager.getUpdateChannelSetting(appConfig = SL.appConfig)) {
                UpdateChannel.Stable -> 0
                UpdateChannel.Unstable -> 1
                UpdateChannel.Test -> 2
            },
            items = listOf(
                SmolDropdownMenuItemTemplate(
                    text = "Stable",
                    iconPath = "icon-stable.svg",
                    onClick = {
                        SL.UI.updateChannelManager.setUpdateChannel(UpdateChannel.Stable, SL.appConfig)
                        doUpdateCheck()
                    }
                ),
                SmolDropdownMenuItemTemplate(
                    text = "Unstable",
                    iconPath = "icon-experimental.svg",
                    onClick = {
                        SL.UI.updateChannelManager.setUpdateChannel(UpdateChannel.Unstable, SL.appConfig)
                        doUpdateCheck()
                    }
                ),
                SmolDropdownMenuItemTemplate(
                    text = "Test (don't use this)",
                    iconPath = "icon-test-radioactive.svg",
                    onClick = {
                        SL.UI.updateChannelManager.setUpdateChannel(UpdateChannel.Test, SL.appConfig)
                        doUpdateCheck()
                    }
                )
            ),
            shouldShowSelectedItemInMenu = true)

        SmolButton(
            onClick = { doUpdateCheck() }
        ) {
            Text("Check for Update")
        }
        Text(
            text = updateStatus,
            style = MaterialTheme.typography.caption
        )
        SmolLinkText(
            linkTextData = LinkTextData(text = "View All Releases", url = Constants.SMOL_RELEASES_URL).asList(),
            modifier = Modifier
                .padding(top = 8.dp),
            style = TextStyle.Default.copy(fontSize = 13.sp),
        )
    }
}

private suspend fun checkForUpdate(): Configuration {
    val updaterConfig = runCatching {
        SL.UI.updateChannelManager.fetchRemoteConfig(SL.UI.updaterUpdater, SL.appConfig)

    }
        .onFailure { Timber.w(it) }
        .getOrNull()

    return if (updaterConfig?.requiresUpdate() == true) {
        Timber.i { "Found update for the SMOL updater." }
        UpdateSmolToast().updateUpdateToast(
            updateConfig = updaterConfig,
            toasterState = SL.UI.toaster,
            smolUpdater = SL.UI.updaterUpdater
        ) {
            GlobalScope.launch { checkForUpdate() }
        }

        updaterConfig

//            Timber.i { "Found update for the SMOL updater, updating it in the background." }
//            runCatching {
//                SL.UI.updaterUpdater.downloadUpdateZip(updaterConfig)
//                SL.UI.updaterUpdater.installUpdate()
//            }
    } else {
        val exitAfterDelay = {
            GlobalScope.launch {
                delay(400) // Time to log an error if there was one
                exitProcess(status = 0)
            }
        }
        val remoteConfig =
            runCatching { SL.UI.updateChannelManager.fetchRemoteConfig(SL.UI.smolUpdater, SL.appConfig) }
                .onFailure {
                    UpdateSmolToast().updateUpdateToast(
                        updateConfig = null,
                        toasterState = SL.UI.toaster,
                        smolUpdater = SL.UI.smolUpdater,
                        onUpdateInstalled = { exitAfterDelay.invoke() }
                    )
                }
                .onSuccess { remoteConfig ->
                    UpdateSmolToast().updateUpdateToast(
                        updateConfig = remoteConfig,
                        toasterState = SL.UI.toaster,
                        smolUpdater = SL.UI.smolUpdater,
                        onUpdateInstalled = { exitAfterDelay.invoke() }
                    )
                }

        remoteConfig
            .getOrThrow()
    }
}