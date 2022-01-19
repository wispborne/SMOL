package smol_app.updater

import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.update4j.Configuration
import smol_app.composables.SmolButton
import smol_app.toasts.Toast
import smol_app.toasts.ToasterState
import smol_app.util.ellipsizeAfter
import timber.ktx.Timber

class UpdateSmolToast {
    private var job = CoroutineScope(Job())
    private var updateStage = MutableStateFlow(UpdateStage.Idle)

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
                        val version = updateConfig.resolvedProperties[Updater.PROP_VERSION]

                        Row(modifier = Modifier
                            .let {
                                if (updateStage.value != UpdateStage.Idle)
                                    it.width(400.dp)
                                else it
                            }) {
                            val fileProgress = updater.currentFileDownload.collectAsState()

                            Column {
                                Text(
                                    text = when (updateStage.value) {
                                        UpdateStage.Downloading ->
                                            "Downloading ${version?.let { "$it: " } ?: ""}" +
                                                    "${fileProgress.value?.name?.ellipsizeAfter(30)}"
                                        UpdateStage.DownloadFailed -> "Download failed."
                                        UpdateStage.ReadyToInstall -> "Update downloaded."
                                        UpdateStage.Installing -> "Installing update."
                                        else -> "${version ?: "A new version"} of SMOL is available."
                                    }
                                )

                                Row {
                                    SmolButton(
                                        modifier = Modifier.padding(top = 4.dp).align(Alignment.CenterVertically),
                                        enabled = updateStage.value != UpdateStage.Done,
                                        onClick = {
                                            when (updateStage.value) {
                                                UpdateStage.Idle -> {
                                                    job = CoroutineScope(Job())
                                                    job.launch {
                                                        try {
                                                            updateStage.value = UpdateStage.Downloading
                                                            updater.update(remoteConfig = updateConfig)
                                                            updateStage.value = UpdateStage.ReadyToInstall
                                                        } catch (e: Exception) {
                                                            Timber.w(e)
                                                            updateStage.value = UpdateStage.DownloadFailed
                                                        }
                                                    }
                                                }
                                                UpdateStage.Downloading -> {
                                                    job.cancel()
                                                    updateStage.value = UpdateStage.Idle
                                                }
                                                UpdateStage.ReadyToInstall -> {
                                                    job = CoroutineScope(Job())
                                                    job.launch {
                                                        try {
                                                            updateStage.value = UpdateStage.Installing
                                                            updater.installUpdate()
                                                            updateStage.value = UpdateStage.Done
                                                        } catch (e: Exception) {
                                                            Timber.w(e)
                                                            updateStage.value = UpdateStage.InstallFailed
                                                        }
                                                    }
                                                }
                                                UpdateStage.Installing -> {
                                                    job.cancel()
                                                    updateStage.value = UpdateStage.ReadyToInstall
                                                }
                                                else -> {
                                                }
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = when (updateStage.value) {
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