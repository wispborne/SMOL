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

package smol_app.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import smol_access.SL
import smol_app.UI
import smol_app.composables.SmolButton
import smol_app.composables.SmolDropdownMenuItemTemplate
import smol_app.composables.SmolDropdownWithButton
import smol_app.updater.UpdateSmolToast
import timber.ktx.Timber
import updatestager.Updater

@Composable
fun updateSection(scope: CoroutineScope) {
    Column(modifier = Modifier.padding(start = 16.dp, top = 24.dp)) {
        Text(text = "Updates", style = SettingsView.settingLabelStyle())

        SmolDropdownWithButton(
            modifier = Modifier.padding(top = 4.dp),
            initiallySelectedIndex = when (SL.UI.updater.getUpdateChannelSetting()) {
                Updater.UpdateChannel.Stable -> 0
                Updater.UpdateChannel.Unstable -> 1
            },
            items = listOf(
                SmolDropdownMenuItemTemplate(
                    text = "Stable",
                    iconPath = "icon-stable.svg",
                    onClick = {
                        SL.UI.updater.setUpdateChannel(Updater.UpdateChannel.Stable)
                        checkForUpdate(scope)
                    }
                ),
                SmolDropdownMenuItemTemplate(
                    text = "Unstable",
                    iconPath = "icon-experimental.svg",
                    onClick = {
                        SL.UI.updater.setUpdateChannel(Updater.UpdateChannel.Unstable)
                        checkForUpdate(scope)
                    }
                )
            ),
            shouldShowSelectedItemInMenu = true)

        SmolButton(
            onClick = {
                checkForUpdate(scope)
            }
        ) {
            Text("Check for Update")
        }
        Text(
            text = "If an update is found, a notification will be displayed.",
            style = MaterialTheme.typography.caption
        )
    }
}

private fun checkForUpdate(scope: CoroutineScope) {
    scope.launch {
        val remoteConfig = SL.UI.updater.getRemoteConfig()

        if (remoteConfig == null) {
            Timber.w { "Unable to fetch remote config, aborting update check." }
        } else {
            UpdateSmolToast().createIfNeeded(
                updateConfig = remoteConfig,
                toasterState = SL.UI.toaster,
                updater = SL.UI.updater
            )
        }
    }
}