package tedwester.convo.ui.chat.message

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlin.math.abs

internal const val VisualTopThresholdPx = 120

internal const val EndSettleThresholdPx = 2

internal const val EndVisibleThresholdPx = 24

private const val MaxScrollByDeltaPx = 2_000_000f
private const val JumpToEndMaxPasses = 12
private const val AnimateToEndMaxPasses = 8

internal fun LazyListState.isAtEnd(thresholdPx: Int = EndSettleThresholdPx): Boolean {
    val info = layoutInfo
    if (info.totalItemsCount == 0) return true
    if (!canScrollForward) return true
    return abs(distanceToEndPx()) <= thresholdPx
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

internal fun LazyListState.distanceToEndPx(): Float {
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

internal fun endSpacerPx(
    viewportHeightPx: Int,
    topPaddingPx: Float,
    bottomPaddingPx: Float,
    lastUserHeightPx: Int,
    lastAssistantHeightPx: Int,
): Float {
    val visible = viewportHeightPx - topPaddingPx - bottomPaddingPx
    if (visible <= 0f) return 0f
    val turnHeight = lastUserHeightPx + lastAssistantHeightPx
    return (visible - turnHeight).coerceAtLeast(0f)
}

internal suspend fun LazyListState.awaitListLaidOut() {
    if (layoutInfo.totalItemsCount > 0 && layoutInfo.visibleItemsInfo.isNotEmpty()) return
    snapshotFlow {
        layoutInfo.totalItemsCount > 0 && layoutInfo.visibleItemsInfo.isNotEmpty()
    }.first { it }
}

private suspend fun LazyListState.awaitLayoutTick() {
    val token = layoutInfo.visibleItemsInfo.sumOf { it.size + it.index }
    snapshotFlow { layoutInfo.visibleItemsInfo.sumOf { it.size + it.index } }
        .first { it != token }
}

internal suspend fun LazyListState.maintainEnd() {
    val distance = distanceToEndPx()
    if (abs(distance) > 0.5f && abs(distance) < MaxScrollByDeltaPx) {
        scrollBy(distance)
    }
}

internal suspend fun LazyListState.jumpToEnd(maxPasses: Int = JumpToEndMaxPasses) {
    awaitListLaidOut()
    repeat(maxPasses) {
        if (isAtEnd()) return
        val distance = distanceToEndPx()
        if (abs(distance) <= EndSettleThresholdPx) return
        if (abs(distance) < MaxScrollByDeltaPx) {
            scrollBy(distance)
        }
        if (isAtEnd()) return
        awaitLayoutTick()
    }
}

internal suspend fun LazyListState.animateToEnd(
    maxPasses: Int = AnimateToEndMaxPasses,
    shouldAbort: () -> Boolean = { false },
) {
    awaitListLaidOut()
    var animated = false
    repeat(maxPasses) {
        if (shouldAbort() || isAtEnd()) return
        val distance = distanceToEndPx()
        if (abs(distance) <= EndSettleThresholdPx) return
        val clamped = distance.coerceIn(-MaxScrollByDeltaPx, MaxScrollByDeltaPx)
        if (!animated) {
            animateScrollBy(clamped)
            animated = true
        } else {
            scrollBy(clamped)
            awaitLayoutTick()
        }
    }
}
