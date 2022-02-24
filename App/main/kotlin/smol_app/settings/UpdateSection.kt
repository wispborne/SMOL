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