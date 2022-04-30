package smol.app.composables

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Source: https://gist.github.com/Tlaster/3e31da8e123b8153993b3aa2cfd5ea00
 */

@Stable
class ReorderableColumnState(
    private val onReorder: (oldIndex: Int, newIndex: Int) -> Unit,
) {
    internal var reordering by mutableStateOf(false)
    internal var draggingItemIndex: Int = -1
    internal var newTargetIndex by mutableStateOf(-1)
    internal var offsetY by mutableStateOf(0f)
    internal var childSizes = arrayListOf<IntSize>()

    fun start(index: Int) {
        draggingItemIndex = index
        reordering = true
    }

    fun drag(y: Float) {
        offsetY += y
        if (offsetY.roundToInt() == 0) {
            return
        }

        val newOffset =
            (childSizes.subList(0, draggingItemIndex).sumOf { it.height } + offsetY).roundToInt()

        newTargetIndex = ArrayList(childSizes)
            .apply {
                removeAt(draggingItemIndex)
            }
            .map { it.height }
            .runningReduce { acc, i -> acc + i }
            .let { it + newOffset }
            .sortedBy { it }
            .indexOf(newOffset)
            .let {
                if (offsetY < 0) {
                    it + 1
                } else {
                    it
                }
            }
    }

    fun cancel() {
        reordering = false
        draggingItemIndex = -1
        newTargetIndex = -1
        offsetY = 0f
    }

    fun drop() {
        if (offsetY.roundToInt() == 0) {
            return
        }
        val newOffset =
            (childSizes.subList(0, draggingItemIndex).sumOf { it.height } + offsetY).roundToInt()

        val newIndex = ArrayList(childSizes)
            .apply {
                removeAt(draggingItemIndex)
            }
            .map { it.height }
            .runningReduce { acc, i -> acc + i }
            .let { it + newOffset }
            .sortedBy { it }
            .indexOf(newOffset)
            .let {
                if (offsetY < 0) {
                    it + 1
                } else {
                    it
                }
            }

        onReorder.invoke(draggingItemIndex, newIndex)

        reordering = false
        draggingItemIndex = -1
        newTargetIndex = -1
        offsetY = 0f
    }
}

@Composable
fun <T> ReorderableColumn(
    modifier: Modifier = Modifier,
    data: List<T>,
    state: ReorderableColumnState,
    draggingContent: @Composable ((T) -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    Layout(
        modifier = modifier
            .verticalScroll(rememberScrollState()),
        content = {
            data.forEachIndexed { index, item ->
                Box(
                    modifier = Modifier
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragCancel = {
                                    state.cancel()
                                },
                                onDragEnd = {
                                    state.drop()
                                },
                                onDragStart = {
                                    state.start(index)
                                },
                                onDrag = { _, dragAmount ->
                                    state.drag(dragAmount.y)
                                }
                            )
                        }
                        .let {
                            if (state.reordering && state.draggingItemIndex == index) {
                                it.zIndex(0.1f)
                            } else {
                                it.zIndex(0f)
                            }
                        }
                ) {
                    if (state.reordering && state.draggingItemIndex == index && draggingContent != null) {
                        draggingContent.invoke(item)
                    } else {
                        itemContent.invoke(item)
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.map {
            it.measure(constraints)
        }
        state.childSizes.clear()
        state.childSizes.addAll(placeables.map {
            IntSize(
                width = it.measuredWidth,
                height = it.measuredHeight
            )
        })

        layout(
            width = placeables.maxOf { it.measuredWidth },
            height = placeables.sumOf { it.measuredHeight }
        ) {
            var height = 0
            placeables.forEachIndexed { index, placeable ->
                if (state.reordering && index == state.newTargetIndex && index != state.draggingItemIndex && state.offsetY < 0) {
                    height += placeables[state.draggingItemIndex].height
                }
                if (state.reordering && index == state.draggingItemIndex) {
                    placeable.place(0, (height + state.offsetY.roundToInt()).let {
                        if (state.newTargetIndex != -1 && index != state.newTargetIndex && state.offsetY < 0) {
                            it - placeables[state.newTargetIndex].height
                        } else {
                            it
                        }
                    })
                } else {
                    placeable.place(0, height)
                }
                if (state.reordering && index == state.newTargetIndex && index != state.draggingItemIndex && state.offsetY > 0) {
                    height += placeables[state.draggingItemIndex].height
                }
                if (!state.reordering || index != state.draggingItemIndex || state.newTargetIndex == -1 || index == state.newTargetIndex) {
                    height += placeable.measuredHeight
                }
            }
        }
    }
}