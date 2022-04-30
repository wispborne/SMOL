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

package smol.app.composables

import AppScope
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.splitpane.ExperimentalSplitPaneApi
import org.jetbrains.compose.splitpane.HorizontalSplitPane
import org.jetbrains.compose.splitpane.rememberSplitPaneState
import smol.access.SL
import smol.app.Logging
import smol.app.UI
import smol.app.themes.SmolTheme
import smol.app.util.openInDesktop
import smol.app.util.replaceTabsWithSpaces
import smol.timber.LogLevel

@OptIn(ExperimentalSplitPaneApi::class)
@Composable
fun AppScope.logPanel(
    modifier: Modifier = Modifier,
    onHideModPanel: () -> Unit
) {
    val horzScrollState = rememberScrollState()
    val splitterState = rememberSplitPaneState(
        initialPositionPercentage = SL.UI.uiConfig.logPanelWidthPercentage
    )
    HorizontalSplitPane(splitPaneState = splitterState) {
        first {
            LaunchedEffect(splitterState.positionPercentage) {
                // Update config file on recompose
                SL.UI.uiConfig.logPanelWidthPercentage = splitterState.positionPercentage
            }

            Card(
                modifier = modifier
//                    .width((window.width / 2).dp)
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
                                onClick = { Logging.logPath.openInDesktop() }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null
                                )
                            }
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
                                            text = it.message.replaceTabsWithSpaces(),
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

        splitter {
            visiblePart {
                Box(
                    modifier
                        .width(1.dp)
                        .offset(x = (-8).dp)
                        .fillMaxHeight()
                        .background(color = Color.Transparent)
                )
            }
            handle {
                Box(
                    Modifier
                        .width(5.dp)
                        .offset(x = (-8).dp)
                        .fillMaxHeight()
                        .markAsHandle()
                        .cursorForHorizontalResize()
                ) {
                }
            }
        }
        second { Spacer(Modifier) }
    }
}