package smol_app.toasts

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import smol_access.SL
import smol_app.UI
import smol_app.themes.SmolTheme.lighten
import utilities.asList

class ToasterState {
    companion object {
        const val defaultTimeoutMillis = 10000L
    }

    val items: MutableStateFlow<List<Toast>> = MutableStateFlow(emptyList())
    val timersByToastId = mutableMapOf<String, Long>()
    private val scope = CoroutineScope(Job())

    init {
        scope.launch {
            items.collect {
                items.value = items.value
                    .filter { (timersByToastId[it.id] ?: 1) > 0 }
                    .distinctBy { it.id }
                    .toMutableList()

                items.value.forEach {
                    if (it.timeoutMillis != null && !timersByToastId.containsKey(it.id)) {
                        timersByToastId[it.id] = it.timeoutMillis
                    }
                }

                timersByToastId.keys.forEach { toastId ->
                    if (toastId !in items.value.map { it.id }) {
                        timersByToastId.remove(toastId)
                    }
                }
            }
        }
    }

    fun addItems(toasts: List<Toast>) =
        toasts
            .filter { toast -> toast.id !in items.value.map { it.id } }
            .run {
                items.value += this
            }

    fun addItem(toast: Toast) = addItems(toast.asList())
}

@Composable
fun toaster(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical? = Arrangement.Top,
    horizontalArrangement: Arrangement.Horizontal? = null
) {
    val recomposeScope = currentRecomposeScope
    val toasterState = SL.UI.toaster
    val items = toasterState.items.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            items.value.toList().forEach {
                if (toasterState.timersByToastId.containsKey(it.id)) {
                    toasterState.timersByToastId[it.id] = toasterState.timersByToastId[it.id]!! - 100
                }
            }

            val preFilterSize = items.value.size
            toasterState.items.value =
                items.value.filter { (toasterState.timersByToastId[it.id] ?: 1) > 0 }

            if (preFilterSize != items.value.size) {
                recomposeScope.invalidate()
            }

            delay(500)
        }
    }

    if (horizontalArrangement != null) {
        LazyRow(modifier, horizontalArrangement = horizontalArrangement, verticalAlignment = Alignment.Bottom) {
            items(items.value) {
                renderToast(it)
            }
        }
    } else if (verticalArrangement != null) {
        LazyColumn(modifier, verticalArrangement = verticalArrangement) {
            items(items.value) {
                renderToast(it)
            }
        }
    }
}

@Composable
private fun renderToast(toast: Toast) {
    if ((toast.timeoutMillis ?: 1) > 0) {
        Row(Modifier.fillMaxHeight()) {
            Box(Modifier.align(Alignment.Bottom)) {
                if (toast.useStandardToastFrame) {
                    Card(
                        modifier = Modifier
                            .border(
                                1.dp,
                                MaterialTheme.colors.background.lighten(),
                                shape = MaterialTheme.shapes.medium
                            ),
                        backgroundColor = MaterialTheme.colors.background,
                        elevation = 4.dp
                    ) {
                        Box(Modifier.padding(16.dp)) {
                            toast.content()
                        }
                    }
                } else {
                    toast.content()
                }
            }
        }
    }
}

data class Toast(
    val id: String,
    val timeoutMillis: Long? = ToasterState.defaultTimeoutMillis,
    val useStandardToastFrame: Boolean = true,
    val content: @Composable () -> Unit
)