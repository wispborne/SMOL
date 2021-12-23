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
import kotlinx.coroutines.flow.collect
import smol_app.Logging
import smol_app.themes.SmolTheme
import timber.LogLevel

@Composable
fun AppState.logPanel(
    modifier: Modifier = androidx.compose.ui.Modifier,
    onHideModPanel: () -> Unit
) {
    val horzScrollState = rememberScrollState()
    Card(
        modifier = modifier
            .width((window.width / 2).dp)
            .fillMaxHeight()
            .padding(bottom = smol_app.themes.SmolTheme.bottomBarHeight, top = 8.dp, start = 8.dp, end = 8.dp),
        shape = androidx.compose.ui.graphics.RectangleShape
    ) {
        val linesToShow = 200
        val log = remember { mutableStateListOf<String>() }

        LaunchedEffect(Unit) {
            smol_app.Logging.logFlow.collect {
                if (log.size >= linesToShow) log.removeFirstOrNull()
                log.add(it)
            }
        }

        Box(modifier = androidx.compose.ui.Modifier.padding(8.dp)) {
            val lazyListState = rememberLazyListState()
            Column {
                Row {
                    val selectedLogLevel = remember { smol_app.Logging.logLevel }
                    SmolDropdownWithButton(
                        modifier = androidx.compose.ui.Modifier.padding(bottom = 4.dp),
                        items = timber.LogLevel.values().map {
                            SmolDropdownMenuItemTemplate(
                                text = it.name.lowercase().replaceFirstChar { it.uppercaseChar() },
                                onClick = {
                                    smol_app.Logging.logLevel = it
                                })
                        },
                        initiallySelectedIndex = selectedLogLevel.ordinal
                    )
                    Spacer(androidx.compose.ui.Modifier.weight(1f))
                    IconButton(
                        onClick = { onHideModPanel() }) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Close,
                            contentDescription = null
                        )
                    }
                }
                Box(
                    modifier = androidx.compose.ui.Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .horizontalScroll(horzScrollState)
                ) {
                    SelectionContainer {
                        LazyColumn(
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxWidth(),
                            state = lazyListState
                        ) {
                            items(log) {
                                Text(
                                    text = it,
                                    softWrap = false,
                                    fontFamily = smol_app.themes.SmolTheme.fireCodeFont,
                                    fontSize = 14.sp,
                                    color = when (it.trim().firstOrNull()?.lowercase()) {
                                        "v" -> androidx.compose.material.MaterialTheme.colors.onSurface.copy(alpha = androidx.compose.material.ContentAlpha.disabled)
                                        "d" -> androidx.compose.material.MaterialTheme.colors.onSurface.copy(alpha = androidx.compose.material.ContentAlpha.medium)
                                        "i" -> androidx.compose.material.MaterialTheme.colors.onSurface
                                        "w" -> androidx.compose.material.MaterialTheme.colors.error.copy(alpha = androidx.compose.material.ContentAlpha.high)
                                        "e" -> androidx.compose.material.MaterialTheme.colors.error
                                        else -> androidx.compose.material.MaterialTheme.colors.onSurface
                                    }
                                )
                            }
                        }
                    }
                }
                HorizontalScrollbar(
                    modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally)
                        .height(8.dp).fillMaxWidth(),
                    adapter = ScrollbarAdapter(horzScrollState)
                )
            }
            VerticalScrollbar(
                modifier = androidx.compose.ui.Modifier.width(8.dp).align(androidx.compose.ui.Alignment.CenterEnd)
                    .fillMaxHeight(),
                adapter = ScrollbarAdapter(lazyListState)
            )
        }
    }
}