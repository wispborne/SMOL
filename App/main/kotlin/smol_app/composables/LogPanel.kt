package smol_app.composables

import AppState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import smol_app.Logging
import smol_app.themes.SmolTheme
import timber.LogLevel

@Composable
fun AppState.logPanel(
    modifier: Modifier = Modifier,
    onHideModPanel: () -> Unit
) {
    val horzScrollState = rememberScrollState()
    Card(
        modifier = modifier
            .width((window.width / 2).dp)
            .fillMaxHeight()
            .padding(bottom = SmolTheme.bottomBarHeight, top = 8.dp, start = 8.dp, end = 8.dp),
        shape = RectangleShape
    ) {
        val linesToShow = 200
        val log = remember { mutableStateListOf<Logging.LogMessage>() }

        LaunchedEffect(Unit) {
            Logging.logFlow.collect {
                if (log.size >= linesToShow) log.removeFirstOrNull()
                log.add(it)
            }
        }

        Box(modifier = Modifier.padding(8.dp)) {
            val lazyListState = rememberLazyListState()
            Column {
                Row {
                    val selectedLogLevel = remember { Logging.logLevel }
                    SmolDropdownWithButton(
                        modifier = Modifier.padding(bottom = 4.dp),
                        items = LogLevel.values().map {
                            SmolDropdownMenuItemTemplate(
                                text = it.name.lowercase().replaceFirstChar { it.uppercaseChar() },
                                onClick = {
                                    Logging.logLevel = it
                                })
                        },
                        initiallySelectedIndex = selectedLogLevel.ordinal
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { onHideModPanel() }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .horizontalScroll(horzScrollState)
                ) {
                    SelectionContainer {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth(),
                            state = lazyListState
                        ) {
                            items(log) {
                                SmolText(
                                    text = it.message.replace("\t", "    "),
                                    softWrap = false,
                                    fontFamily = SmolTheme.fireCodeFont,
                                    fontSize = 14.sp,
                                    color = when (it.logLevel) {
                                        LogLevel.VERBOSE -> MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
                                        LogLevel.DEBUG -> MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                                        LogLevel.INFO -> MaterialTheme.colors.onSurface
                                        LogLevel.WARN -> MaterialTheme.colors.error.copy(alpha = ContentAlpha.high)
                                        LogLevel.ERROR -> MaterialTheme.colors.error
                                        else -> MaterialTheme.colors.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
                HorizontalScrollbar(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .height(8.dp).fillMaxWidth(),
                    adapter = ScrollbarAdapter(horzScrollState)
                )
            }
            VerticalScrollbar(
                modifier = Modifier.width(8.dp).align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                adapter = rememberScrollbarAdapter(lazyListState)
            )
        }
    }
}