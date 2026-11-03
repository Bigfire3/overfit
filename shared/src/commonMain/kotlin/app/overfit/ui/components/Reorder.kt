package app.overfit.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

/**
 * Drag & drop reordering for a [androidx.compose.foundation.lazy.LazyColumn] with keyed items,
 * built on Compose Foundation only.
 *
 * Usage: `val reorder = rememberReorderState(listState, canMove = { key -> key is String }) { from, to -> … }`,
 * then on each movable item `Modifier.reorderableItem(reorder, key)` and on its handle
 * `Modifier.dragHandle(reorder, key)` (long press, then drag). [onMove] receives the keys of the
 * dragged item and of the item whose place it takes; the caller moves its data accordingly.
 *
 * Swapping happens when the dragged item's edge passes the neighbour's centre, which keeps items
 * of different heights from flipping back and forth.
 */
@Stable
class ReorderState internal constructor(
    val listState: LazyListState,
    private val canMove: (Any) -> Boolean,
    private val onMove: (from: Any, to: Any) -> Unit,
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set

    internal fun start(key: Any) {
        draggingKey = key
        dragOffset = 0f
    }

    internal fun end() {
        draggingKey = null
        dragOffset = 0f
    }

    internal fun drag(dy: Float) {
        val key = draggingKey ?: return
        dragOffset += dy
        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == key } ?: return
        val top = current.offset + dragOffset
        val bottom = top + current.size

        val below = items.firstOrNull { it.index == current.index + 1 }?.takeIf { canMove(it.key) }
        val above = items.firstOrNull { it.index == current.index - 1 }?.takeIf { canMove(it.key) }
        when {
            below != null && bottom > below.center -> move(current, below, top, down = true)
            above != null && top < above.center -> move(current, above, top, down = false)
        }
        autoScroll(top, bottom)
    }

    private fun move(current: LazyListItemInfo, target: LazyListItemInfo, visualTop: Float, down: Boolean) {
        // Where the dragged item will sit after the move; the finger position must not jump.
        val newOffset = if (down) target.offset + target.size - current.size else target.offset
        dragOffset = visualTop - newOffset
        onMove(current.key, target.key)
    }

    private fun autoScroll(top: Float, bottom: Float) {
        val info = listState.layoutInfo
        val edge = (info.viewportEndOffset - info.viewportStartOffset) * 0.12f
        val delta = when {
            bottom > info.viewportEndOffset - edge -> SCROLL_STEP
            top < info.viewportStartOffset + edge -> -SCROLL_STEP
            else -> 0f
        }
        if (delta != 0f) {
            val consumed = listState.dispatchRawDelta(delta)
            // The item scrolls with the content; keep it under the finger.
            dragOffset += consumed
        }
    }

    private val LazyListItemInfo.center: Float get() = offset + size / 2f

    private companion object {
        const val SCROLL_STEP = 12f
    }
}

@Composable
fun rememberReorderState(
    listState: LazyListState,
    canMove: (Any) -> Boolean = { true },
    onMove: (from: Any, to: Any) -> Unit,
): ReorderState {
    val currentCanMove by rememberUpdatedState(canMove)
    val currentOnMove by rememberUpdatedState(onMove)
    return remember(listState) { ReorderState(listState, { currentCanMove(it) }, { a, b -> currentOnMove(a, b) }) }
}

/** Lifts the dragged item above the others and lets the rest animate into place. */
@Composable
fun LazyItemScope.reorderableItem(state: ReorderState, key: Any): Modifier =
    if (state.draggingKey == key) {
        Modifier.zIndex(1f).graphicsLayer { translationY = state.dragOffset; alpha = 0.9f }
    } else {
        Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
    }

/** Long press on this element starts dragging the item with [key]. */
fun Modifier.dragHandle(state: ReorderState, key: Any, onStart: () -> Unit = {}): Modifier =
    pointerInput(state, key) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.start(key); onStart() },
            onDragEnd = { state.end() },
            onDragCancel = { state.end() },
            onDrag = { change, amount ->
                change.consume()
                state.drag(amount.y)
            },
        )
    }
