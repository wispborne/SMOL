package smol_app.views

import AppState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import smol_access.SL
import smol_access.business.JreEntry
import smol_app.composables.SmolButton
import smol_app.composables.SmolTooltipArea
import smol_app.composables.SmolTooltipText
import smol_app.util.parseHtml
import kotlin.io.path.relativeTo

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppState.jreSwitcher(modifier: Modifier = Modifier, javasFound: SnapshotStateList<JreEntry>) {
    val recomposer = currentRecomposeScope

    SmolTooltipArea(
        tooltip = { SmolTooltipText("Switch between JRE versions") },
        delayMillis = SmolTooltipArea.delay
    ) {
        Column(modifier = modifier.padding(start = 16.dp)) {
            if (javasFound.size > 1) {
                Text(
                    text = "Select a Java Runtime (JRE)",
                    modifier = Modifier.padding(bottom = 8.dp),
                    style = MaterialTheme.typography.subtitle2
                )
            }

            javasFound.forEach { jreEntry ->
                Row {
                    RadioButton(
                        onClick = {
                            if (!jreEntry.isUsedByGame) {
                                GlobalScope.launch {
                                    SL.jreManager.changeJre(jreEntry)

                                    withContext(Dispatchers.Main) {
                                        recomposer.invalidate()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterVertically),
                        selected = jreEntry.isUsedByGame
                    )
                    Text(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        text = "<b>${jreEntry.versionString}</b> (in folder <code>${
                            jreEntry.path.relativeTo(
                                SL.gamePath.get()!!
                            )
                        }</code>)".parseHtml()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppState.jre8DownloadButton(modifier: Modifier = Modifier) {
    SmolTooltipArea(
        tooltip = { SmolTooltipText("Download JRE 8") },
        delayMillis = SmolTooltipArea.delay
    ) {
        var progress_ by remember { mutableStateOf(0f) }
        val progress by animateFloatAsState(progress_)

        Row(modifier = modifier.padding(start = 16.dp)) {
            SmolButton(
                onClick = {
                    GlobalScope.launch {
                        SL.jreManager.downloadJre8 {
                            progress_ = it
                        }
                    }
                },
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(text = "Download JRE 8")
            }
        }
        CircularProgressIndicator(
            progress = progress
        )
    }
}