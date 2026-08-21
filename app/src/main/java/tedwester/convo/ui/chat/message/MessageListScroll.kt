package tedwester.convo.ui.chat.message

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

internal const val VisualTopThresholdPx = 160

internal const val EndSettleThresholdPx = 2

/** Scroll-up distance from latest messages before the scroll-to-bottom FAB appears. */
internal const val EndVisibleThresholdPx = 180

/** Distance from latest messages where the scroll-to-bottom FAB hides again. */
internal const val EndHideThresholdPx = 48

/** Scroll-up distance from latest messages before the scroll-to-top FAB may appear. */
internal const val TopShowFromEndThresholdPx = 120

/** Distance from latest messages where the scroll-to-top FAB hides again. */
internal const val TopHideAtEndThresholdPx = 64

private const val MaxScrollByDeltaPx = 2_000_000f
private const val JumpToEndMaxPasses = 12
private const val MinScrollMs = 320
private const val MaxScrollMs = 900

/** Fast start, then ease into the destination. */
internal val ChatScrollEasing = CubicBezierEasing(0.12f, 0.0f, 0.0f, 1.0f)

internal fun chatScrollDurationMs(distancePx: Float): Int {
    val abs = abs(distancePx)
    return (260f + abs * 0.28f).toInt().coerceIn(MinScrollMs, MaxScrollMs)
}

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

internal fun LazyListState.distanceToStartPx(): Float {
    val info = layoutInfo
    if (info.totalItemsCount == 0) return 0f
    if (!canScrollBackward) return 0f

    val visualTop = info.viewportStartOffset + info.beforeContentPadding
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return Float.MAX_VALUE / 4f

    val firstVisible = visible.first()
    val intoFirst = (visualTop - firstVisible.offset).toFloat().coerceAtLeast(0f)
    if (firstVisible.index == 0) return intoFirst

    val typicalSize = visible.maxOf { it.size }.toFloat().coerceAtLeast(1f) +
        info.mainAxisItemSpacing
    return intoFirst + firstVisible.index * typicalSize
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

private suspend fun LazyListState.awaitLayoutTickOrTimeout() {
    withTimeoutOrNull(48) { awaitLayoutTick() }
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
        awaitLayoutTickOrTimeout()
    }
}

private suspend fun LazyListState.animateAlong(
    distancePx: Float,
    shouldAbort: () -> Boolean,
) {
    val clamped = distancePx.coerceIn(-MaxScrollByDeltaPx, MaxScrollByDeltaPx)
    if (abs(clamped) <= EndSettleThresholdPx) return
    animateScrollBy(
        clamped,
        tween(
            durationMillis = chatScrollDurationMs(clamped),
            easing = ChatScrollEasing,
        ),
    )
    if (shouldAbort()) return
    awaitLayoutTickOrTimeout()
}

internal suspend fun LazyListState.animateToEnd(
    shouldAbort: () -> Boolean = { false },
) {
    awaitListLaidOut()
    val distance = distanceToEndPx()
    if (abs(distance) <= EndSettleThresholdPx) return
    val clamped = distance.coerceIn(-MaxScrollByDeltaPx, MaxScrollByDeltaPx)
    animateAlong(clamped, shouldAbort)
    if (shouldAbort()) return
    val remaining = distanceToEndPx()
    if (abs(remaining) > EndSettleThresholdPx) {
        scrollBy(remaining)
    }
}

internal suspend fun LazyListState.animateToStart(
    shouldAbort: () -> Boolean = { false },
) {
    awaitListLaidOut()
    val distance = -distanceToStartPx()
    if (abs(distance) <= EndSettleThresholdPx) return
    val clamped = distance.coerceIn(-MaxScrollByDeltaPx, MaxScrollByDeltaPx)
    animateAlong(clamped, shouldAbort)
    if (shouldAbort() || !canScrollBackward) return
    val remaining = -distanceToStartPx()
    if (abs(remaining) > EndSettleThresholdPx) {
        scrollBy(remaining)
    }
}
