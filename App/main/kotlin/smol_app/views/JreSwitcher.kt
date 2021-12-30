package smol_app.views

import AppState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import smol_access.SL
import smol_access.business.JreEntry
import smol_access.business.JreManager
import smol_app.composables.SmolButton
import smol_app.composables.SmolTooltipArea
import smol_app.composables.SmolTooltipText
import smol_app.util.openAsUriInBrowser
import smol_app.util.parseHtml
import kotlin.io.path.relativeTo

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppState.jreSwitcher(
    modifier: Modifier = Modifier,
    recomposer: RecomposeScope,
    jresFound: SnapshotStateList<JreEntry>
) {
    Column(modifier = modifier.padding(start = 16.dp)) {
        if (jresFound.size > 1) {
            Text(
                text = "Select a Java Runtime (JRE)",
                modifier = Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.subtitle1
            )
        }

        jresFound.forEach { jreEntry ->
            val onClick = {
                if (!jreEntry.isUsedByGame) {
                    GlobalScope.launch {
                        SL.jreManager.changeJre(jreEntry)

                        withContext(Dispatchers.Main) {
                            recomposer.invalidate()
                        }
                    }
                }
            }

            SmolTooltipArea(
                tooltip = { SmolTooltipText("Set ${jreEntry.versionString} as the active JRE.") },
                delayMillis = SmolTooltipArea.delay
            ) {
                Row {
                    RadioButton(
                        onClick = onClick,
                        modifier = Modifier.align(Alignment.CenterVertically),
                        selected = jreEntry.isUsedByGame
                    )
                    Text(
                        modifier = Modifier.align(Alignment.CenterVertically)
                            .clickable { onClick.invoke() },
                        text = "<b>Java ${jreEntry.version}</b> (${jreEntry.versionString}) in folder <code>${
                            jreEntry.path.relativeTo(
                                SL.gamePath.get()!!
                            )
                        }</code>".parseHtml()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppState.jre8DownloadButton(
    modifier: Modifier = Modifier,
    jresFound: SnapshotStateList<JreEntry>,
    recomposer: RecomposeScope
) {
    val jre8DownloadProgress by SL.jreManager.jre8DownloadProgress.collectAsState()

    Row(modifier = modifier.padding(start = 16.dp)) {
        SmolTooltipArea(
            tooltip = {
                SmolTooltipText(
                    "Download JRE 8 to '<code>${
                        SL.gamePath.get()?.resolve(JreManager.gameJreFolderName)
                    }</code>'.".parseHtml()
                )
            },
            delayMillis = SmolTooltipArea.delay
        ) {
            SmolButton(
                enabled = jre8DownloadProgress == null,
                onClick = {
                    GlobalScope.launch {
                        SL.jreManager.downloadJre8()
                        recomposer.invalidate()
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterVertically)
            ) {
                Text(
                    text = if (jresFound.any { it.versionString.contains("1.8") })
                        "Redownload JRE 8"
                    else "Download JRE 8"
                )
            }
        }

        SmolTooltipArea(
            tooltip = {
                SmolTooltipText("Download in a browser.")
            },
            delayMillis = SmolTooltipArea.delay
        ) {
            IconButton(
                onClick = { SL.appConfig.jre8Url.openAsUriInBrowser() },
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Icon(
                    painter = painterResource("web.svg"),
                    contentDescription = null
                )
            }
        }

        if (jre8DownloadProgress != null) {
            Text(
                text = when (val progress = jre8DownloadProgress) {
                    JreManager.Jre8Progress.Done -> "Done"
                    is JreManager.Jre8Progress.Downloading -> {
                        if (progress.progress == null || progress.progress!! <= 0f) "Connecting..."
                        else "Downloading..."
                    }
                    JreManager.Jre8Progress.Extracting -> "Extracting..."
                    null -> ""
                },
                modifier = Modifier.align(Alignment.CenterVertically).padding(start = 16.dp)
            )
        }

        if (jre8DownloadProgress is JreManager.Jre8Progress.Downloading) {
            val progress by animateFloatAsState(
                (jre8DownloadProgress as? JreManager.Jre8Progress.Downloading)?.progress ?: 0f
            )

            val progressModifier = Modifier
                .padding(start = 16.dp)
                .align(Alignment.CenterVertically)
                .size(32.dp)
            if (progress > 0f) {
                CircularProgressIndicator(
                    modifier = progressModifier,
                    progress = progress
                )
            } else {
                CircularProgressIndicator(
                    modifier = progressModifier,
                )
            }
        }
    }
}