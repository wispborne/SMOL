package smol_app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import smol_access.SL
import smol_app.browser.DownloadManager
import smol_app.browser.downloadCard

class ToasterState(
    private val downloadManager: DownloadManager
) {
    companion object {
        const val defaultTimeoutMillis = 10000L
    }

    val items: MutableStateFlow<List<Toast>> = MutableStateFlow(emptyList())
    val timersByToastId = mutableMapOf<String, Long>()
    private val scope = CoroutineScope(Job())

    init {
        scope.launch {
            downloadManager.downloads.collect { downloads ->
                downloads
                    .filter { it.id !in items.value.map { it.id } }
                    .map {
                        Toast(id = it.id, timeoutMillis = null) {
                            downloadCard(download = it,
                                requestToastDismissal = {
                                    if (!SL.UI.toaster.timersByToastId.containsKey(it.id)) {
                                        SL.UI.toaster.timersByToastId[it.id] = 0
                                    }
                                })
                        }
                    }
                    .also {
                        items.value += it
                    }
            }
        }
    }
}

@Composable
fun toaster(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical? = Arrangement.Top,
    horizontalArrangement: Arrangement.Horizontal? = null
) {
    val scope = rememberCoroutineScope()
    val recomposeScope = currentRecomposeScope
    val toasterState = SL.UI.toaster
    val items = toasterState.items.collectAsState()
    toasterState.items.value = items.value
        .filter { (toasterState.timersByToastId[it.id] ?: 1) > 0 }
        .toMutableList()
    items.value.forEach {
        if (it.timeoutMillis != null && !toasterState.timersByToastId.containsKey(it.id)) {
            toasterState.timersByToastId[it.id] = it.timeoutMillis
        }
    }

    LaunchedEffect(Unit) {
        scope.launch {
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

                delay(100)
            }
        }
    }

    if (horizontalArrangement != null) {
        LazyRow(modifier, horizontalArrangement = horizontalArrangement) {
            items(items.value) {
                if ((it.timeoutMillis ?: 1) > 0) {
                    it.content()
                }
            }
        }
    } else if (verticalArrangement != null) {
        LazyColumn(modifier, verticalArrangement = verticalArrangement) {
            items(items.value) {
                if ((it.timeoutMillis ?: 1) > 0) {
                    it.content()
                }
            }
        }
    }
}

data class Toast(
    val id: String,
    val timeoutMillis: Long? = ToasterState.defaultTimeoutMillis,
    val content: @Composable () -> Unit
)