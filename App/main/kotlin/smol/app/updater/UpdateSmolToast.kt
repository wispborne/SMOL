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

package smol.app.updater

import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.update4j.Configuration
import smol.app.composables.SmolButton
import smol.app.toasts.ToastContainer
import smol.app.toasts.ToasterState
import smol.timber.ktx.Timber
import smol.update_installer.BaseAppUpdater
import smol.update_installer.SmolUpdater
import smol.updatestager.UpdaterUpdater
import smol.utilities.bytesAsShortReadableMB
import smol.utilities.bytesToMB
import smol.utilities.ellipsizeAfter

class UpdateSmolToast {
    private var job = CoroutineScope(Job())

    companion object {
        const val UPDATE_TOAST_ID = "smol-update"
    }

    enum class UpdateStage {
        Idle,
        Downloading,
        DownloadFailed,
        ReadyToInstall,
        Installing,
        InstallFailed,
        Done,
    }

    fun updateUpdateToast(
        updateConfig: Configuration?,
        toasterState: ToasterState,
        smolUpdater: BaseAppUpdater,
        onUpdateInstalled: () -> Unit
    ) {
        val name = when (smolUpdater) {
            is SmolUpdater -> "SMOL"
            is UpdaterUpdater -> "Updater"
            else -> throw RuntimeException(smolUpdater::class.qualifiedName)
        }

        if (updateConfig?.requiresUpdate() == true) {
            Timber.i { "Adding update toast for config '${smolUpdater.updateZipFile}'." }
            toasterState.addItem(
                toastContainer = ToastContainer(
                    id = UPDATE_TOAST_ID,
                    timeoutMillis = null,
                    useStandardToastFrame = true,
                    content = {
                        kotlin.runCatching {
                            val version = updateConfig.resolvedProperties[smolUpdater.versionPropertyKey]
                            var updateStage by remember {
                                mutableStateOf(
                                    if (smolUpdater.isUpdatedDownloaded())
                                        UpdateStage.ReadyToInstall else UpdateStage.Idle
                                )
                            }

                            Row(modifier = Modifier
                                .let {
                                    if (updateStage == UpdateStage.Downloading)
                                        it.width(400.dp)
                                    else it
                                }) {
                                val fileProgress = smolUpdater.currentFileDownload.collectAsState()

                                Column {
                                    Text(
                                        text = when (updateStage) {
                                            UpdateStage.Downloading ->
                                                "Downloading ${version?.let { version -> "$version: " } ?: ""}" +
                                                        (fileProgress.value?.name?.ellipsizeAfter(30) ?: "...")
                                            UpdateStage.DownloadFailed -> "$name update download failed."
                                            UpdateStage.ReadyToInstall -> "$name update downloaded."
                                            UpdateStage.Installing -> "Installing $name update."
                                            else -> "${version?.ifBlank { null } ?: "A new version"} of $name is available."
                                        }
                                    )

                                    Row {
                                        SmolButton(
                                            modifier = Modifier.padding(top = 4.dp).align(Alignment.CenterVertically),
                                            enabled = updateStage != UpdateStage.Done,
                                            onClick = {
                                                when (updateStage) {
                                                    UpdateStage.Idle -> {
                                                        job = CoroutineScope(Job())
                                                        job.launch {
                                                            try {
                                                                updateStage = UpdateStage.Downloading
                                                                smolUpdater.downloadUpdateZip(remoteConfig = updateConfig)
                                                                updateStage = UpdateStage.ReadyToInstall
                                                            } catch (e: Exception) {
                                                                Timber.w(e)
                                                                updateStage = UpdateStage.DownloadFailed
                                                            }
                                                        }
                                                    }
                                                    UpdateStage.Downloading -> {
                                                        job.cancel()
                                                        updateStage = UpdateStage.Idle
                                                    }
                                                    UpdateStage.ReadyToInstall -> {
                                                        job = CoroutineScope(Job())
                                                        job.launch {
                                                            try {
                                                                updateStage = UpdateStage.Installing
                                                                smolUpdater.installUpdate()
                                                                updateStage = UpdateStage.Done
                                                                toasterState.remove(UPDATE_TOAST_ID)
                                                                onUpdateInstalled.invoke()
                                                            } catch (e: Exception) {
                                                                Timber.w(e)
                                                                updateStage = UpdateStage.InstallFailed
                                                            }
                                                        }
                                                    }
                                                    UpdateStage.Installing -> {
                                                        job.cancel()
                                                        updateStage = UpdateStage.ReadyToInstall
                                                    }
                                                    else -> {
                                                    }
                                                }
                                            }
                                        ) {
                                            Text(
                                                text = when (updateStage) {
                                                    UpdateStage.Idle,
                                                    UpdateStage.DownloadFailed -> "Download"
                                                    UpdateStage.Downloading -> "Cancel"
                                                    UpdateStage.ReadyToInstall,
                                                    UpdateStage.Installing,
                                                    UpdateStage.InstallFailed -> "Install"
                                                    UpdateStage.Done -> "Done"
                                                }
                                            )
                                        }

                                        CircularProgressIndicator(
                                            modifier = Modifier.padding(start = 16.dp).size(20.dp)
                                                .align(Alignment.CenterVertically),
                                            progress = smolUpdater.totalDownloadFraction.collectAsState().value ?: 0f
                                        )
                                        CircularProgressIndicator(
                                            modifier = Modifier.padding(start = 8.dp).size(20.dp)
                                                .align(Alignment.CenterVertically),
                                            progress = fileProgress.value?.progress ?: 0f
                                        )
                                    }

                                    val downloadTotal = smolUpdater.totalDownloadBytes.collectAsState().value
                                    val downloadedTotal =
                                        if (updateStage > UpdateStage.Downloading) downloadTotal
                                        else smolUpdater.totalDownloadedBytes.collectAsState().value
                                    val downloadedAmountText =
                                        "${downloadedTotal?.let { "%.2f".format(it.bytesToMB) } ?: "unknown"} / "
                                    Text(
                                        text = "${if (updateStage >= UpdateStage.Downloading) downloadedAmountText else ""}${downloadTotal?.bytesAsShortReadableMB ?: "unknown"}",
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }


                                Spacer(Modifier.weight(1f))

                                IconButton(
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .align(Alignment.CenterVertically)
                                        .size(16.dp),
                                    onClick = {
                                        toasterState.remove(UPDATE_TOAST_ID)
                                    }
                                ) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = null)
                                }
                            }
                        }
                            .onFailure { Timber.w(it) }
                    }
                )
            )
        } else {
            Timber.i {
                "Removing update toast for config '${smolUpdater.updateZipFile}'."
            }
            toasterState.remove(toastId = UPDATE_TOAST_ID)
        }
    }
}