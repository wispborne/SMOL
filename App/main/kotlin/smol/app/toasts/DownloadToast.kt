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

package smol.app.toasts

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
import smol.access.SL
import smol.app.UI
import smol.app.browser.DownloadItem
import smol.app.composables.SmolTooltipArea
import smol.app.composables.SmolTooltipText
import smol.app.util.openInDesktop
import smol.app.util.smolPreview
import smol.timber.ktx.Timber
import smol.utilities.bitsToMB
import smol.utilities.bytesAsShortReadableMB
import smol.utilities.equalsAny
import java.awt.Cursor
import kotlin.io.path.absolutePathString
import kotlin.io.path.name

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun DownloadToast(
    modifier: Modifier = Modifier,
    download: DownloadItem,
    requestToastDismissal: (delayMs: Long) -> Unit
) {
    val status = download.status.collectAsState().value
    val progressBytes = download.progressBytes.collectAsState().value
    val bitsPerSecond = download.bitsPerSecond.collectAsState().value
    val totalBytes = download.totalBytes

    if (status::class.equalsAny(
            DownloadItem.Status.Failed::class,
            DownloadItem.Status.Cancelled::class,
            DownloadItem.Status.Completed::class
        )
    ) {
        requestToastDismissal(ToasterState.defaultTimeoutMillis)
    }

    val progressPercent: Float =
        if (download.fractionDone.value != null)
            download.fractionDone.value ?: 0f
        else if (totalBytes.value != null && progressBytes != null) {
            (progressBytes.toFloat() / totalBytes.value!!.toFloat())
        } else {
            0f
        }
    val progressMB = progressBytes?.bytesAsShortReadableMB
    val totalMB = totalBytes.value?.bytesAsShortReadableMB

    val statusText = when (status) {
        is DownloadItem.Status.NotStarted -> "Starting"
        is DownloadItem.Status.Downloading -> {
            "${if (bitsPerSecond != null) "${"%.1f MBps".format(bitsPerSecond.bitsToMB)}, " else ""}$progressMB${if (totalMB != null) " / $totalMB" else ""}"
        }

        is DownloadItem.Status.Completed -> "Completed, $progressMB"
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

                else -> throw NotImplementedError()
            }

            IconButton(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .align(Alignment.CenterVertically)
                    .size(16.dp),
                onClick = {
                    SL.UI.downloadManager.activeDownloads[download.id]?.cancel()
                    requestToastDismissal(0)
                }
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = null)
            }
        }
    }
}

@Preview
@Composable
fun downloadCardPreview() =
    smolPreview {
        DownloadToast(
            download = DownloadItem.MOCK,
            requestToastDismissal = {}
        )
    }