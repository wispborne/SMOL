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

package smol.app.toasts

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import smol.access.SL
import smol.app.UI
import smol.timber.ktx.Timber
import smol.utilities.asList

class ToasterState {
    companion object {
        const val defaultTimeoutMillis = 10000L
    }

    val items: MutableStateFlow<List<ToastContainer>> = MutableStateFlow(emptyList())
    private val timersByToastId = mutableMapOf<String, Long>()
    private val scope = CoroutineScope(Job())

    init {
        scope.launch {
            items.collect {
                items.value = items.value
                    .filter { (timersByToastId[it.id] ?: 1) > 0 }
                    .distinctBy { it.id }
                    .toMutableList()

                // If there's a toast in the new list that doesn't have a timer and should, add the timer.
                items.value.forEach { toast ->
                    if (toast.timeoutMillis != null && !timersByToastId.containsKey(toast.id)) {
                        Timber.i { "Added toast timer, as it didn't have one, for toast $toast." }
                        timersByToastId[toast.id] = toast.timeoutMillis
                    }
                }

                // Prune any timers that don't have a Toast anymore.
                timersByToastId.keys.toList().forEach { toastId ->
                    if (toastId !in items.value.map { it.id }) {
                        Timber.i { "Pruned toast timer $toastId with no corresponding toast." }
                        timersByToastId.remove(toastId)
                    }
                }
            }
        }
    }

    fun addItems(toastContainers: List<ToastContainer>) {
        toastContainers
            .filter { toast -> toast.id !in items.value.map { it.id } }
            .run {
                items.update { it + this }
                Timber.i { "Added new toasts ${toastContainers.joinToString(separator = "\n")}." }
            }
    }

    fun addItem(toastContainer: ToastContainer) = addItems(toastContainer.asList())

    /**
     * Removes the toast.
     */
    fun burn(toastId: String, delay: Long = 0L) {
        if (!timersByToastId.containsKey(toastId)) {
            timersByToastId[toastId] = delay
        }
    }

    fun burnAll(toastIds: List<String>, delay: Long = 0L) {
        toastIds.forEach { burn(it, delay) }
    }

    fun setTimeout(toastId: String, timeoutMillis: Long) {
        Timber.d { "Set toast timer id $toastId to ${timeoutMillis}ms." }
        timersByToastId[toastId] = timeoutMillis
    }

    fun getMillisRemaining(toastId: String): Long? = timersByToastId[toastId]
}

@Composable
fun toaster(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical? = null,
    horizontalArrangement: Arrangement.Horizontal? = null
) {
    val recomposeScope = currentRecomposeScope
    val toasterState = SL.UI.toaster
    val items = toasterState.items.collectAsState()

    // Constantly loop to update Toast timers.
    LaunchedEffect(Unit) {
        while (true) {
            val loopDelay = 500L
            items.value.toList().forEach {
                val millisRemaining = toasterState.getMillisRemaining(it.id)

                if (millisRemaining != null) {
                    toasterState.setTimeout(it.id, millisRemaining - loopDelay)
                }
            }

            val preFilterSize = items.value.size
            toasterState.items.value =
                items.value.filter { toast ->
                    val hasTimeLeftOrIndefinite = (toasterState.getMillisRemaining(toast.id) ?: 1) > 0
                    if (!hasTimeLeftOrIndefinite) Timber.i { "Removing expired toast $toast." }
                    hasTimeLeftOrIndefinite
                }

            if (preFilterSize != items.value.size) {
                recomposeScope.invalidate()
            }

            delay(loopDelay)
        }
    }

    if (horizontalArrangement != null) {
        LazyRow(modifier, horizontalArrangement = horizontalArrangement, verticalAlignment = Alignment.Bottom) {
            items(items.value.sortedByDescending { it.priority }) {
                renderToast(it)
            }
        }
    } else if (verticalArrangement != null) {
//        Row(verticalAlignment = Alignment.Bottom) {
        LazyColumn(
            modifier.fillMaxWidth(),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = Alignment.End,
            reverseLayout = true
        ) {
            items(items.value.sortedByDescending { it.priority }) {
                renderToast(it)
            }
        }
//        }
    }
}

@Composable
private fun renderToast(toastContainer: ToastContainer) {
    if ((toastContainer.timeoutMillis ?: 1) > 0) {
        Row {
            Box(Modifier.align(Alignment.Bottom)) {
                if (toastContainer.useStandardToastFrame) {
                    Card(
                        modifier = Modifier
                            .border(
                                1.dp,
                                MaterialTheme.colors.secondaryVariant,
                                shape = MaterialTheme.shapes.medium
                            ),
                        backgroundColor = MaterialTheme.colors.background,
                        elevation = 4.dp
                    ) {
                        Box(Modifier.padding(16.dp)) {
                            toastContainer.content()
                        }
                    }
                } else {
                    toastContainer.content()
                }
            }
        }
    }
}

/**
 * Toast container.
 */
data class ToastContainer(
    val id: String,
    /**
     * Higher priority toasts are shown first.
     * 000 is standard priority.
     * 999 is pinned priority.
     */
    val priority: Int = 0,
    val timeoutMillis: Long? = ToasterState.defaultTimeoutMillis,
    val useStandardToastFrame: Boolean = true,
    val content: @Composable () -> Unit
) {
}