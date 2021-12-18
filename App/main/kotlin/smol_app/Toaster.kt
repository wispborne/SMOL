package smol_app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import smol_app.Toaster.defaultTimeoutMillis

object Toaster {
    val defaultTimeoutMillis = 10000L
}

@Composable
fun toaster(
    modifier: Modifier = Modifier,
    toasterState: ToasterState,
    items: List<Toast>,
    verticalArrangement: Arrangement.Vertical? = Arrangement.Top,
    horizontalArrangement: Arrangement.Horizontal? = null
) {
    val scope = rememberCoroutineScope()
    val recomposeScope = currentRecomposeScope
    toasterState.items = items
        .filter { (toasterState.timersByToastId[it.id] ?: 1) > 0 }
        .toMutableList()
    toasterState.items.forEach {
        if (it.timeoutMillis != null && !toasterState.timersByToastId.containsKey(it.id)) {
            toasterState.timersByToastId[it.id] = it.timeoutMillis
        }
    }

    LaunchedEffect(Unit) {
        scope.launch {
            while (true) {
                toasterState.items.toList().forEach {
                    if (toasterState.timersByToastId.containsKey(it.id)) {
                        toasterState.timersByToastId[it.id] = toasterState.timersByToastId[it.id]!! - 100
                    }
                }

                val preFilterSize = toasterState.items.size
                toasterState.items = toasterState.items.filter { (toasterState.timersByToastId[it.id] ?: 1) > 0 }

                if (preFilterSize != toasterState.items.size) {
                    recomposeScope.invalidate()
                }

                delay(100)
            }
        }
    }

    if (horizontalArrangement != null) {
        LazyRow(modifier, horizontalArrangement = horizontalArrangement) {
            items(toasterState.items) {
                if ((it.timeoutMillis ?: 1) > 0) {
                    it.content()
                }
            }
        }
    } else if (verticalArrangement != null) {
        LazyColumn(modifier, verticalArrangement = verticalArrangement) {
            items(toasterState.items) {
                if ((it.timeoutMillis ?: 1) > 0) {
                    it.content()
                }
            }
        }
    }
}

class ToasterState {
    var items: List<Toast> = emptyList()
    val timersByToastId = mutableMapOf<String, Long>()
}

data class Toast(
    val id: String,
    val timeoutMillis: Long? = defaultTimeoutMillis,
    val content: @Composable () -> Unit
)