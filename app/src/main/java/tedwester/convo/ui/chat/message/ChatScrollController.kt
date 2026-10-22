package tedwester.convo.ui.chat.message

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Slack, in pixels, that still counts as "flush with the newest content".
 *
 * Deliberately tiny: anything larger would let a one-pixel scroll up re-arm following and
 * snap the list back to the bottom, which is exactly the "stuck to the bottom" feel.
 */
private const val EndSlackPx = 1f

/** Distance from the newest content before the scroll-to-bottom button appears. */
internal const val ScrollToBottomThresholdPx = 120f

/** Distance from the oldest content before the scroll-to-top button appears. */
internal const val ScrollToTopThresholdPx = 120f

// ---------------------------------------------------------------------------------------------
// Exact geometry
//
// Everything below is derived from LazyListLayoutInfo, never from guesses about item sizes.
// ---------------------------------------------------------------------------------------------

/** Bottom edge of the scrollable content, in viewport coordinates. */
internal val LazyListState.contentBottom: Float
    get() = layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding.toFloat()

/** Top edge of the scrollable content, in viewport coordinates. */
internal val LazyListState.contentTop: Float
    get() = layoutInfo.viewportStartOffset + layoutInfo.beforeContentPadding.toFloat()

/**
 * Exact distance between the bottom of the newest content and the bottom of the viewport.
 *
 * Positive when the newest content hangs below the viewport, zero when it is flush with the
 * end. Only meaningful while the newest item is on screen.
 */
internal fun LazyListState.distanceToEnd(): Float {
    val info = layoutInfo
    if (info.totalItemsCount == 0) return 0f
    if (!canScrollForward) return 0f
    val last = info.visibleItemsInfo.lastOrNull() ?: return 0f
    if (last.index != info.totalItemsCount - 1) return 0f
    return (last.offset + last.size) - contentBottom
}

/** True when the newest content sits flush with the end of the viewport. */
internal fun LazyListState.isAtEnd(slackPx: Float = EndSlackPx): Boolean {
    val info = layoutInfo
    if (info.totalItemsCount == 0) return true
    if (!canScrollForward) return true
    val last = info.visibleItemsInfo.lastOrNull() ?: return false
    if (last.index != info.totalItemsCount - 1) return false
    return (last.offset + last.size) <= contentBottom + slackPx
}

/** True when the oldest content sits flush with the top of the viewport. */
internal fun LazyListState.isAtStart(slackPx: Float = EndSlackPx): Boolean {
    val info = layoutInfo
    if (info.totalItemsCount == 0) return true
    if (!canScrollBackward) return true
    val first = info.visibleItemsInfo.firstOrNull() ?: return false
    if (first.index != 0) return false
    return first.offset >= contentTop - slackPx
}

// ---------------------------------------------------------------------------------------------
// Movement
// ---------------------------------------------------------------------------------------------

/** Suspends until the list has content and a settled layout. */
internal suspend fun LazyListState.awaitContent() {
    withTimeoutOrNull(1000) {
        snapshotFlow {
            layoutInfo.totalItemsCount > 0 && layoutInfo.visibleItemsInfo.isNotEmpty()
        }.first { it }
    }
}

/**
 * Scrolls by the exact remaining distance so the newest content ends flush with the viewport.
 */
private suspend fun LazyListState.settleAtEnd(animated: Boolean) {
    val info = layoutInfo
    val lastIndex = info.totalItemsCount - 1
    if (lastIndex < 0) return
    val last = info.visibleItemsInfo.lastOrNull() ?: return
    if (last.index != lastIndex) return
    val remainder = (last.offset + last.size) - contentBottom
    if (remainder <= 0.5f) return
    if (animated) animateScrollBy(remainder) else scrollBy(remainder)
}

/**
 * Keeps the viewport pinned to the newest content.
 *
 * If the newest item is on screen we scroll by the exact overflow. If it is not, we bring it
 * into view first and settle on the exact end. Both paths converge on the same position, so
 * the list never drifts while a response streams in.
 */
internal suspend fun LazyListState.pinToEnd() {
    val info = layoutInfo
    val lastIndex = info.totalItemsCount - 1
    if (lastIndex < 0) return
    if (!canScrollForward) return
    val last = info.visibleItemsInfo.lastOrNull()
    if (last != null && last.index == lastIndex) {
        val overflow = (last.offset + last.size) - contentBottom
        if (overflow > 0.5f) scrollBy(overflow)
        return
    }
    // The newest item is not on screen, so something was appended while we were following.
    // Bring it in and settle on the exact end.
    animateScrollToItem(lastIndex)
    settleAtEnd(animated = true)
}

/** Jumps straight to the newest content, without animating. */
internal suspend fun LazyListState.jumpToEnd() {
    awaitContent()
    val info = layoutInfo
    val lastIndex = info.totalItemsCount - 1
    if (lastIndex < 0) return
    val last = info.visibleItemsInfo.lastOrNull()
    if (last != null && last.index == lastIndex) {
        val overflow = (last.offset + last.size) - contentBottom
        if (overflow > 0.5f) scrollBy(overflow)
        return
    }
    scrollToItem(lastIndex)
    settleAtEnd(animated = false)
}

/**
 * Animates to the newest content.
 *
 * When the newest item is on screen the remaining distance is exact, so the list glides
 * straight to the end. Otherwise the item is scrolled into view first and the short
 * remainder to its very end is animated afterwards.
 */
internal suspend fun LazyListState.animateToEnd() {
    awaitContent()
    val info = layoutInfo
    val lastIndex = info.totalItemsCount - 1
    if (lastIndex < 0) return
    val last = info.visibleItemsInfo.lastOrNull()
    if (last != null && last.index == lastIndex) {
        val overflow = (last.offset + last.size) - contentBottom
        if (overflow > 0.5f) animateScrollBy(overflow)
        return
    }
    animateScrollToItem(lastIndex)
    settleAtEnd(animated = true)
}

/** Animates to the oldest content. */
internal suspend fun LazyListState.animateToStart() {
    awaitContent()
    if (layoutInfo.totalItemsCount <= 0) return
    animateScrollToItem(0)
}

/** Jumps straight to the oldest content. */
internal suspend fun LazyListState.jumpToStart() {
    awaitContent()
    if (layoutInfo.totalItemsCount <= 0) return
    scrollToItem(0)
}

// ---------------------------------------------------------------------------------------------
// Controller
// ---------------------------------------------------------------------------------------------

/**
 * Owns every scroll decision for the conversation list.
 *
 * The list state stays the single source of truth. This controller only decides *whether* the
 * list should follow new content, and it never moves the list unless the user asked for it or
 * the list was already following the newest content.
 */
@Stable
class ChatScrollController internal constructor(
    val listState: LazyListState,
    private val scope: CoroutineScope,
) {
    /** True while the list should stay pinned to the newest content. */
    var isFollowing by mutableStateOf(true)
        private set

    /** True when the newest content is within [ScrollToBottomThresholdPx] of the viewport end. */
    var isNearEnd by mutableStateOf(true)
        private set

    /** True when the oldest content is within [ScrollToTopThresholdPx] of the viewport start. */
    var isNearStart by mutableStateOf(true)
        private set

    /** The user moved the list themselves; stop following until they come back. */
    internal fun onUserScrolled() {
        isFollowing = false
    }

    /** Re-arms following, for example when a new conversation is opened. */
    internal fun resumeFollowing() {
        isFollowing = true
    }

    /** Something else took over the viewport (for example an expanded thinking block). */
    internal fun stopFollowing() {
        isFollowing = false
    }

    internal fun onProximityChanged(atEnd: Boolean, nearEnd: Boolean, nearStart: Boolean) {
        isNearEnd = nearEnd
        isNearStart = nearStart
        if (atEnd) {
            // Back at the newest content: following resumes.
            isFollowing = true
        }
    }

    /** Scrolls to the newest content and keeps following it. */
    fun scrollToEnd(animated: Boolean = true) {
        isFollowing = true
        scope.launch {
            if (animated) listState.animateToEnd() else listState.jumpToEnd()
        }
    }

    /** Scrolls to the oldest content. */
    fun scrollToStart(animated: Boolean = true) {
        scope.launch {
            if (animated) listState.animateToStart() else listState.jumpToStart()
        }
    }
}

/**
 * Creates the scroll controller for a conversation list and wires it to the list state.
 *
 * @param conversationKey changes whenever a different conversation is shown, which resets the
 *   position to the newest content.
 * @param autoScrollEnabled when false the controller stays out of the way and the list is only
 *   moved by the user.
 */
@Composable
fun rememberChatScrollController(
    listState: LazyListState,
    conversationKey: Any? = null,
    autoScrollEnabled: Boolean = true,
    scope: CoroutineScope = rememberCoroutineScope(),
): ChatScrollController {
    val controller = remember(listState) { ChatScrollController(listState, scope) }

    LaunchedEffect(listState, conversationKey, autoScrollEnabled) {
        if (!autoScrollEnabled) return@LaunchedEffect
        controller.resumeFollowing()
        controller.listState.jumpToEnd()
    }

    LaunchedEffect(listState, autoScrollEnabled) {
        if (!autoScrollEnabled) return@LaunchedEffect
        snapshotFlow {
            Triple(
                listState.isAtEnd(),
                listState.isAtEnd(ScrollToBottomThresholdPx),
                listState.isAtStart(ScrollToTopThresholdPx),
            )
        }
            .distinctUntilChanged()
            .collect { (atEnd, nearEnd, nearStart) ->
                controller.onProximityChanged(atEnd, nearEnd, nearStart)
            }
    }

    LaunchedEffect(listState, autoScrollEnabled) {
        if (!autoScrollEnabled) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo }
            .collect {
                if (!controller.isFollowing) return@collect
                try {
                    listState.pinToEnd()
                } catch (error: CancellationException) {
                    // A user gesture interrupted the pin. Keep the loop alive so the list can
                    // follow again later, unless this whole effect is being cancelled.
                    if (!currentCoroutineContext().isActive) throw error
                }
            }
    }

    return controller
}

/** Remembers the list state for a conversation. */
@Composable
fun rememberConversationListState(initialFirstVisibleItemIndex: Int): LazyListState {
    return rememberLazyListState(
        initialFirstVisibleItemIndex = initialFirstVisibleItemIndex.coerceAtLeast(0),
    )
}
