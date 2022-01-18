package smol_app.updater

import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.collectAsState
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
import smol_app.util.ellipsizeAfter

class UpdateSmolToast() {
    private var job = CoroutineScope(Job())
    private var isUpdating = false

    fun createIfNeeded(
        updateConfig: Configuration,
        toasterState: ToasterState,
        updater: Updater
    ) {
        if (updateConfig.requiresUpdate()) {
            toasterState.addItem(
                toast = Toast(
                    id = "smol-update",
                    timeoutMillis = null,
                    useStandardToastFrame = true,
                    content = {
                        val version = updateConfig.resolvedProperties[Updater.PROP_VERSION]
                        Row(modifier = Modifier
                            .let {
                                if (isUpdating)
                                    it.width(400.dp)
                                else it
                            }) {
                            val fileProgress = updater.currentFileDownload.collectAsState()

                            Column {
                                Text(
                                    text = if (!isUpdating) {
                                        "${version ?: "A new version"} of SMOL is available."
                                    } else {
                                        "Downloading ${version?.let { "$it: " } ?: ""}" +
                                                "${fileProgress.value?.name?.ellipsizeAfter(30)}"
                                    }
                                )

                                Row {
                                    SmolButton(
                                        modifier = Modifier.padding(top = 4.dp).align(Alignment.CenterVertically),
                                        onClick = {
                                            if (isUpdating) {
                                                job.cancel()
                                                isUpdating = false
                                            } else {
                                                job = CoroutineScope(Job())
                                                job.launch {
                                                    try {
                                                        isUpdating = true
                                                        updater.update(remoteConfig = updateConfig)
                                                    } finally {
                                                        isUpdating = false
                                                    }
                                                }
                                            }
                                            // TODO install archive, too.
                                        }
                                    ) {
                                        Text(text = if (isUpdating) "Cancel" else "Download")
                                    }

                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(start = 16.dp).size(20.dp).align(Alignment.CenterVertically),
                                        progress = updater.totalDownloadFraction.collectAsState().value ?: 0f
                                    )
                                    CircularProgressIndicator(
                                        modifier = Modifier.padding(start = 8.dp).size(20.dp).align(Alignment.CenterVertically),
                                        progress = fileProgress.value?.progress ?: 0f
                                    )
                                }
                            }

//                            job.launch {
//                                val updater = SL.UI.updater
//                                val updateToastId = "download-update"
//
//                                updater.updateDownloadFraction.collectLatest { downloadFraction ->
//                                    if (downloadFraction != null) {
//                                        var downloadItem =
//                                            SL.UI.downloadManager.downloads.value.firstOrNull { it.id == updateToastId }
//
//                                        if (downloadItem == null) {
//                                            downloadItem = DownloadItem(
//                                                id = updateToastId,
//                                                path = MutableStateFlow(Path.of("Downloading update")),
//                                                totalBytes = MutableStateFlow(1L)
//                                            )
//                                            SL.UI.downloadManager.addDownload(downloadItem)
//                                        }
//
//                                        downloadItem.status.value = DownloadItem.Status.Downloading
//                                        downloadItem.fractionDone.value = downloadFraction
//                                    }
//                                }
//                            }
                        }
                    }
                )
            )
        }
    }
}