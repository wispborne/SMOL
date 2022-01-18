package smol_app.updater

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.update4j.Configuration
import smol_app.composables.SmolButton
import smol_app.toasts.Toast
import smol_app.toasts.ToasterState

class UpdateSmolToast() {
    private val job = CoroutineScope(Job())

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
                        val version = updateConfig.resolvedProperties[Updater.PROP_VERSION] ?: "A new version"
                        Row {
                            Column {
                                Text(text = "$version of SMOL is now available.")
                                SmolButton(
                                    modifier = Modifier.padding(top = 4.dp),
                                    onClick = {
                                        updater.update(remoteConfig = updateConfig)
                                        // TODO install archive, too.
                                    }
                                ) {
                                    Text(text = "Download")
                                }
                            }

                            val progress = updater.updateDownloadFraction.collectAsState()

                            if (progress.value != null) {
                                CircularProgressIndicator(
                                    progress = progress.value ?: 0f
                                )
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