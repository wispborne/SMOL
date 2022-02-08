package smol_app.updater

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
import smol_app.composables.SmolButton
import smol_app.toasts.Toast
import smol_app.toasts.ToasterState
import smol_app.util.bytesAsShortReadableMB
import smol_app.util.bytesToMB
import smol_app.util.ellipsizeAfter
import timber.ktx.Timber
import updatestager.Updater
import kotlin.system.exitProcess

class UpdateSmolToast {
    private var job = CoroutineScope(Job())

    enum class UpdateStage {
        Idle,
        Downloading,
        DownloadFailed,
        ReadyToInstall,
        Installing,
        InstallFailed,
        Done,
    }

    fun createIfNeeded(
        updateConfig: Configuration,
        toasterState: ToasterState,
        updater: Updater
    ) {
        if (updateConfig.requiresUpdate()) {
            val updateToastId = "smol-update"
            toasterState.addItem(
                toast = Toast(
                    id = updateToastId,
                    timeoutMillis = null,
                    useStandardToastFrame = true,
                    content = {
                        val version = updateConfig.resolvedProperties[Updater.PROP_VERSION_NAME]
                        var updateStage by remember {
                            mutableStateOf(
                                if (updater.isUpdatedDownloaded())
                                    UpdateStage.ReadyToInstall else UpdateStage.Idle
                            )
                        }

                        Row(modifier = Modifier
                            .let {
                                if (updateStage == UpdateStage.Downloading)
                                    it.width(400.dp)
                                else it
                            }) {
                            val fileProgress = updater.currentFileDownload.collectAsState()

                            Column {
                                Text(
                                    text = when (updateStage) {
                                        UpdateStage.Downloading ->
                                            "Downloading ${version?.let { "$it: " } ?: ""}" +
                                                    "${fileProgress.value?.name?.ellipsizeAfter(30)}"
                                        UpdateStage.DownloadFailed -> "Download failed."
                                        UpdateStage.ReadyToInstall -> "Update downloaded."
                                        UpdateStage.Installing -> "Installing update."
                                        else -> "${version?.ifBlank { null } ?: "A new version"} of SMOL is available."
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
                                                            updater.update(remoteConfig = updateConfig)
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
                                                            // Start updater, then quit application so updater can replace files.
                                                            updater.installUpdate()
                                                            exitProcess(status = 0)
                                                            updateStage = UpdateStage.Done
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
                                        progress = updater.totalDownloadFraction.collectAsState().value ?: 0f
                                    )
                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(start = 8.dp).size(20.dp)
                                            .align(Alignment.CenterVertically),
                                        progress = fileProgress.value?.progress ?: 0f
                                    )
                                }

                                val downloadTotal = updater.totalDownloadBytes.collectAsState().value
                                val downloadedTotal =
                                    if (updateStage >= UpdateStage.Downloading) downloadTotal
                                    else updater.totalDownloadedBytes.collectAsState().value
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
                                    toasterState.remove(updateToastId)
                                }
                            ) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = null)
                            }
                        }
                    }
                )
            )
        }
    }
}