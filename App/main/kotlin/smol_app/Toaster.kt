package smol_app

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun toaster(
    modifier: Modifier = Modifier,
    toasterState: ToasterState
) {
    val scope = rememberCoroutineScope()
    val recomposeScope = currentRecomposeScope
    LaunchedEffect(Unit) {
        scope.launch {
            while (true) {
                toasterState.items.forEach {
                    if (it.timeoutMillis != null)
                        it.timeoutMillis = it.timeoutMillis!! - 100
                }
                toasterState.items.removeIf { (it.timeoutMillis ?: 1) <= 0 }

                if (toasterState.items.any()) {
                    recomposeScope.invalidate()
                }

                delay(100)
            }
        }
    }

    LazyColumn(modifier) {
        items(toasterState.items) {
            if ((it.timeoutMillis ?: 1) > 0) {
                it.content()
            }
        }
    }
}

class ToasterState(
) {
    val items: MutableList<Toast> = mutableListOf()

    fun addItem(toast: Toast) {
        items.add(toast)
    }
}

data class Toast(
    var timeoutMillis: Long? = 5000,
    val content: @Composable () -> Unit
)