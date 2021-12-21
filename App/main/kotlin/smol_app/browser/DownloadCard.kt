package smol_app.browser

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.mouseClickable
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import smol_access.SL
import smol_app.UI
import smol_app.composables.SmolTooltipArea
import smol_app.composables.SmolTooltipText
import smol_app.util.bitsToMiB
import smol_app.util.bytesAsShortReadableMiB
import smol_app.util.openInDesktop
import smol_app.util.previewTheme
import timber.ktx.Timber
import java.awt.Cursor
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun downloadCard(
    modifier: Modifier = Modifier,
    download: DownloadItem,
    requestToastDismissal: () -> Unit
) {
    val status = download.status.collectAsState().value
    val progress = download.progress.collectAsState().value
    val bitsPerSecond = download.bitsPerSecond.collectAsState().value
    val total = download.totalBytes

    val progressPercent: Float = if (total.value != null)
        (progress.toFloat() / total.value!!.toFloat())
    else 0f
    val progressMiB = progress.bytesAsShortReadableMiB
    val totalMiB = total.value?.bytesAsShortReadableMiB

    val statusText = when (status) {
        is DownloadItem.Status.NotStarted -> "Starting"
        is DownloadItem.Status.Downloading -> {
            "${if (bitsPerSecond != null) "${"%.1f Mbps".format(bitsPerSecond.bitsToMiB)}, " else ""}$progressMiB${if (totalMiB != null) " / $totalMiB" else ""}"
        }
        is DownloadItem.Status.Completed -> "Completed, $progressMiB"
        is DownloadItem.Status.Failed -> "Failed: ${status.error}"
        DownloadItem.Status.Cancelled -> "Cancelled"
    }

    SmolTooltipArea(
        modifier = modifier,
        delayMillis = 300,
        tooltip = {
            SmolTooltipText(
                text = listOfNotNull(
                    download.path.value?.name.toString(),
                    "Path: ${download.path.value?.absolutePathString()}",
                    if (status is DownloadItem.Status.Failed) status.error.message.toString() else null,
                    statusText
                ).joinToString(separator = "\n")
            )
        }
    ) {
        Card(
            modifier = Modifier,
            backgroundColor = MaterialTheme.colors.background
        ) {
            Row(modifier = Modifier.padding(start = 8.dp, end = 8.dp)) {
                Column(
                    modifier = Modifier
                        .padding(start = 4.dp, top = 4.dp, bottom = 4.dp, end = 8.dp)
                        .mouseClickable {
                            kotlin.runCatching { download.path.value?.parent?.openInDesktop() }
                                .onFailure { Timber.e(it) }
                        }
                        .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)))
                ) {
                    Text(
                        modifier = Modifier,
                        text = download.path.value?.name ?: "",
                        fontSize = 12.sp
                    )
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = statusText,
                        fontSize = 12.sp
                    )
                }

                when (download.status.value) {
                    DownloadItem.Status.Downloading -> {
                        if (download.totalBytes.value != null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp).align(Alignment.CenterVertically),
                                progress = progressPercent,
                                color = MaterialTheme.colors.onSurface
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp).align(Alignment.CenterVertically),
                                color = MaterialTheme.colors.onSurface
                            )
                        }
                    }
                }

                IconButton(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .align(Alignment.CenterVertically)
                        .size(16.dp),
                    onClick = {
                        SL.UI.downloadManager.activeDownloads[download.id]?.cancel()
                        requestToastDismissal()
                    }
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = null)
                }
            }
        }
    }
}

@Preview
@Composable
fun downloadCardPreview() =
    previewTheme {
        downloadCard(
            download = DownloadItem(id = "")
                .apply {
                    this.path.value = Path.of("C:/temp/perseanchronicles.7z")
                    this.totalBytes.value = 1000
                    this.progress.value = 750
                    this.bitsPerSecond.value = 512000
                    this.status.value = DownloadItem.Status.Downloading
                },
            requestToastDismissal = {}
        )
    }