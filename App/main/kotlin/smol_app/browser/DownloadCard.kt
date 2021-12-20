package smol_app.browser

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.mouseClickable
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import smol_app.composables.SmolTooltipArea
import smol_app.composables.SmolTooltipText
import smol_app.util.bytesAsReadableMiB
import smol_app.util.openInDesktop
import timber.ktx.Timber
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun downloadCard(modifier: Modifier = Modifier, download: DownloadItem) {
    val status = download.status.collectAsState().value
    val progress = download.progress.collectAsState().value
    val total = download.totalBytes

    val progressPercent: Float = if (total.value != null)
        (progress.toFloat() / total.value!!.toFloat())
    else 0f

    SmolTooltipArea(
        modifier = modifier,
        tooltip = {
            SmolTooltipText(text = buildString {
                appendLine(download.path.value?.absolutePathString())
                if (status is DownloadItem.Status.Failed) appendLine(status.error.message)
            })
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
                ) {
                    val progressMiB = progress.bytesAsReadableMiB
                    val totalMiB = total.value?.bytesAsReadableMiB
                    Text(
                        modifier = Modifier,
                        text = download.path.value?.name ?: "",
                        fontSize = 12.sp
                    )
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = when (status) {
                            is DownloadItem.Status.NotStarted -> "Starting"
                            is DownloadItem.Status.Downloading -> {
                                "$progressMiB${if (totalMiB != null) " / $totalMiB" else ""}}"
                            }
                            is DownloadItem.Status.Completed -> "Completed $progressMiB"
                            is DownloadItem.Status.Failed -> "Failed: ${status.error}"
                            DownloadItem.Status.Cancelled -> "Cancelled"
                        },
                        fontSize = 12.sp
                    )
                }
                if (status == DownloadItem.Status.Completed) {
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
    }
}