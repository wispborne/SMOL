@file:OptIn(ExperimentalFoundationApi::class)

package smol_app.composables

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Icon
import androidx.compose.material.IconToggleButton
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import smol_app.Logging
import smol_app.themes.SmolTheme
import smol_app.util.smolPreview

@Composable
@Preview
private fun logButtonAndErrorDisplayPreview() = smolPreview {
    logButtonAndErrorDisplay(showLogPanel = mutableStateOf(true))
}

@Composable
fun logButtonAndErrorDisplay(modifier: Modifier = Modifier, showLogPanel: MutableState<Boolean>) {
    Row(modifier) {
        SmolTooltipArea(
            tooltip = { SmolTooltipText(text = "Show/Hide Logs") },
            delayMillis = SmolTooltipArea.shortDelay
        ) {
            IconToggleButton(
                checked = showLogPanel.value,
                modifier = Modifier.padding(start = 8.dp).run {
                    if (showLogPanel.value) this.background(color = Color.White.copy(alpha = 0.20f), shape = CircleShape)
                    else this
                },
                onCheckedChange = { showLogPanel.value = it }
            ) {
                Icon(
                    painter = painterResource("icon-log.svg"),
                    tint = if (showLogPanel.value) Color.White else SmolTheme.dimmedIconColor(),
                    contentDescription = null
                )
            }
        }

        var newestLogLine by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            Logging.logFlow.collectLatest {
                if (it.trim().startsWith("E/", ignoreCase = true)) {
                    newestLogLine = it
                }
            }
        }
        Text(
            text = newestLogLine,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp).align(Alignment.CenterVertically)
        )
    }
}