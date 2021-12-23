@file:OptIn(ExperimentalFoundationApi::class)

package smol_app.composables

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconToggleButton
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import smol_app.Logging
import smol_app.util.previewTheme

@Composable
@Preview
private fun logButtonAndErrorDisplayPreview() = previewTheme {
    logButtonAndErrorDisplay(mutableStateOf(true))
}

@Composable
fun logButtonAndErrorDisplay(showLogPanel: MutableState<Boolean>) {
    Row {
        SmolTooltipArea(tooltip = { SmolTooltipText(text = "Show/Hide Logs") }) {
            IconToggleButton(
                checked = showLogPanel.value,
                modifier = Modifier.padding(start = 8.dp),
                onCheckedChange = { showLogPanel.value = it }
            ) {
                Icon(
                    painter = painterResource("icon-log.svg"),
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
            modifier = Modifier.align(Alignment.CenterVertically).padding(start = 8.dp)
        )
    }
}