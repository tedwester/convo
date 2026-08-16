package tedwester.convo.ui.chat.message

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlin.math.abs

internal const val VisualBottomThresholdPx = 120

internal const val VisualTopThresholdPx = 120

internal const val PinSettleThresholdPx = 2
private const val MaxScrollByDeltaPx = 2_000_000f

internal fun LazyListState.isAtVisualBottom(thresholdPx: Int = VisualBottomThresholdPx): Boolean {
    val info = layoutInfo
    val total = info.totalItemsCount
    if (total == 0) return true
    val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return false
    if (lastVisible.index < total - 1) return false
    val visualBottom = info.viewportEndOffset - info.afterContentPadding
    val itemBottom = lastVisible.offset + lastVisible.size
    if (!canScrollForward && lastVisible.offset >= 0) return true
    return abs(itemBottom - visualBottom) <= thresholdPx
}

internal fun LazyListState.isAtVisualTop(thresholdPx: Int = VisualTopThresholdPx): Boolean {
    val info = layoutInfo
    val total = info.totalItemsCount
    if (total == 0) return true
    val firstVisible = info.visibleItemsInfo.firstOrNull() ?: return false
    if (firstVisible.index > 0) return false
    val visualTop = info.viewportStartOffset + info.beforeContentPadding
    val itemTop = firstVisible.offset
    if (!canScrollBackward && firstVisible.index == 0) return true
    return abs(itemTop - visualTop) <= thresholdPx
}

internal fun LazyListState.distanceToVisualBottomPx(): Float {
    val info = layoutInfo
    val total = info.totalItemsCount
    if (total == 0) return 0f
    if (!canScrollForward) return 0f

    val lastIndex = total - 1
    val visualBottom = info.viewportEndOffset - info.afterContentPadding
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return Float.MAX_VALUE / 4f

    val lastVisible = visible.last()
    if (lastVisible.index == lastIndex) {
        val itemBottom = lastVisible.offset + lastVisible.size
        return (itemBottom - visualBottom).toFloat()
    }

    val typicalSize = visible.maxOf { it.size }.toFloat().coerceAtLeast(1f) +
        info.mainAxisItemSpacing
    val itemsRemaining = lastIndex - lastVisible.index
    val tailBelowViewport = (lastVisible.offset + lastVisible.size - visualBottom).coerceAtLeast(0)
    return tailBelowViewport + itemsRemaining * typicalSize
}

internal suspend fun LazyListState.awaitListLaidOut() {
    if (layoutInfo.totalItemsCount > 0 && layoutInfo.visibleItemsInfo.isNotEmpty()) return
    snapshotFlow {
        layoutInfo.totalItemsCount > 0 && layoutInfo.visibleItemsInfo.isNotEmpty()
    }.first { it }
}

internal suspend fun LazyListState.awaitLastItemVisible() {
    snapshotFlow {
        val last = layoutInfo.totalItemsCount - 1
        last >= 0 && layoutInfo.visibleItemsInfo.any { it.index == last }
    }.first { it }
}

private suspend fun LazyListState.awaitLayoutTick() {
    val token = layoutInfo.visibleItemsInfo.sumOf { it.size + it.index }
    snapshotFlow { layoutInfo.visibleItemsInfo.sumOf { it.size + it.index } }
        .first { it != token }
}

internal suspend fun LazyListState.pinToVisualBottom() {
    awaitListLaidOut()
    val lastIndex = layoutInfo.totalItemsCount - 1
    if (lastIndex < 0) return
    pinItemBottomIntoView(lastIndex)
}

internal suspend fun LazyListState.maintainVisualBottom() {
    val distance = distanceToVisualBottomPx()
    if (abs(distance) > 0.5f) {
        scrollBy(distance)
    }
}

internal suspend fun LazyListState.pinItemBottomIntoView(index: Int) {
    if (layoutInfo.totalItemsCount <= 0) return
    val lastIndex = layoutInfo.totalItemsCount - 1
    val clamped = index.coerceIn(0, lastIndex)

    awaitListLaidOut()
    if (isAtVisualBottom(thresholdPx = PinSettleThresholdPx)) return

    scrollToItem(clamped)
    refineItemBottomIntoView(clamped)

    if (isAtVisualBottom(thresholdPx = PinSettleThresholdPx)) return

    val distance = distanceToVisualBottomPx()
    if (abs(distance) > PinSettleThresholdPx && abs(distance) < MaxScrollByDeltaPx) {
        scrollBy(distance)
    }
}

private suspend fun LazyListState.refineItemBottomIntoView(index: Int) {
    val item = layoutInfo.visibleItemsInfo.find { it.index == index } ?: return
    val visualBottom = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding
    val overflow = (item.offset + item.size) - visualBottom
    if (abs(overflow) > PinSettleThresholdPx) {
        scrollBy(overflow.toFloat())
    }
}

internal suspend fun LazyListState.pinToVisualBottomUntilSettled(maxPasses: Int = 12) {
    awaitListLaidOut()
    repeat(maxPasses) {
        pinToVisualBottom()
        if (isAtVisualBottom(thresholdPx = PinSettleThresholdPx)) return
        awaitLayoutTick()
    }
}
