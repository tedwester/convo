package tedwester.convo.ui.chat.message

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.exp
import tedwester.convo.ui.chat.PullHaptics

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

/** How far a pull gesture travels before it counts as fully pulled. */
internal val PullRevealDistance = 120.dp

/** How far the chat moves up while a pull gesture is in progress. */
internal val PullRevealShift = 96.dp

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
    private val pullThresholdPx: Float,
    private val pullShiftMaxPx: Float,
) {
    /** The keyboard is dismissed when the user starts scrolling the list. */
    internal var onDismissKeyboard: () -> Unit = {}

    /** Called when the list is pulled far enough to offer a new chat. */
    internal var onPullTriggered: () -> Unit = {}

    /** Fired as the pull passes each quarter of the way. */
    internal var onPullTick: () -> Unit = {}

    /** Fired the moment the pull is complete. */
    internal var onPullArmed: () -> Unit = {}

    /** Whether the pull gesture is currently allowed. */
    internal var pullEnabled: Boolean = true

    private val pullHaptics = PullHaptics()

    /**
     * Watches the list for user input: it tells the controller when the user scrolls and
     * opens the gap below the newest content when the list is pulled past the end.
     */
    val scrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput && available.y != 0f) {
                onDismissKeyboard()
            }
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (source != NestedScrollSource.UserInput) return Offset.Zero

            if (consumed.y != 0f) {
                onUserScrolled()
                // Scrolling back towards the newest content closes the gap again.
                closePull(consumed.y)
            }

            // Dragging past the end opens a gap for the new chat action.
            if (available.y < 0f && listState.isAtEnd()) {
                addPull(-available.y)
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }
    }

    /** True while the list should stay pinned to the newest content. */
    var isFollowing by mutableStateOf(true)
        private set

    /** True when the newest content is within [ScrollToBottomThresholdPx] of the viewport end. */
    var isNearEnd by mutableStateOf(true)
        private set

    /** True when the oldest content is within [ScrollToTopThresholdPx] of the viewport start. */
    var isNearStart by mutableStateOf(true)
        private set

    /** True while the user has a finger on the list. */
    var isGestureActive by mutableStateOf(false)
        private set

    /** True while an explicit scroll animation runs, so the follow logic keeps its hands off. */
    internal var isProgrammaticScroll = false

    /** How far the list has been pulled past the newest content, in pixels. */
    var pullDistance by mutableFloatStateOf(0f)
        private set

    /** How far the pull has progressed towards the new chat action. */
    val pullProgress: Float
        get() = if (pullThresholdPx <= 0f) 0f else (pullDistance / pullThresholdPx).coerceIn(0f, 1f)

    /** True once a release would start a new chat. */
    val isPullArmed: Boolean
        get() = pullProgress >= 1f

    /**
     * How far the chat itself moves up while pulling.
     *
     * The chat only shifts a little, and it eases into place instead of tracking the finger
     * exactly, so the gesture reads as "something is about to happen" rather than dragging
     * the whole conversation off screen.
     */
    val pullShift: Float
        get() {
            if (pullShiftMaxPx <= 0f || pullDistance <= 0f) return 0f
            val ratio = pullDistance / pullShiftMaxPx
            return pullShiftMaxPx * (1f - exp(-ratio))
        }

    /** The user moved the list themselves; stop following until they come back. */
    internal fun onUserScrolled() {
        isFollowing = false
    }

    /** A finger went down on the list. Nothing may move the list while it is held. */
    internal fun onGestureStart() {
        isGestureActive = true
        isFollowing = false
        pullHaptics.reset()
    }

    /** The finger lifted. A full pull starts a new chat; otherwise the list just settles. */
    internal fun onGestureEnd() {
        val fireNewChat = isPullArmed
        isGestureActive = false
        if (fireNewChat) {
            onPullTriggered()
        }
        if (!isProgrammaticScroll && listState.isAtEnd()) {
            isFollowing = true
        }
        releasePull()
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
        if (atEnd && !isProgrammaticScroll && !isGestureActive) {
            // Back at the newest content: following resumes. While a finger is down the list
            // belongs to the user, and an explicit scroll keeps its own decision.
            isFollowing = true
        }
    }

    /** Scrolls to the newest content and keeps following it. */
    fun scrollToEnd(animated: Boolean = true) {
        isFollowing = true
        isProgrammaticScroll = true
        scope.launch {
            try {
                if (animated) listState.animateToEnd() else listState.jumpToEnd()
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    /** Scrolls to the oldest content, letting go of the newest content on the way. */
    fun scrollToStart(animated: Boolean = true) {
        // Reading history means the newest content is no longer what the user is looking at.
        isFollowing = false
        isProgrammaticScroll = true
        scope.launch {
            try {
                if (animated) listState.animateToStart() else listState.jumpToStart()
            } finally {
                isProgrammaticScroll = false
            }
        }
    }

    /** Opens the gap below the newest content while the list is pulled past the end. */
    internal fun addPull(deltaPx: Float) {
        if (deltaPx <= 0f || !pullEnabled) return
        val next = (pullDistance + deltaPx).coerceAtMost(pullThresholdPx * 1.15f)
        if (next == pullDistance) return
        pullDistance = next
        pullHaptics.onProgress(
            progress = pullProgress,
            tick = onPullTick,
            confirm = onPullArmed,
        )
    }

    /** Closes the gap again, for example when the user scrolls back down. */
    internal fun closePull(amountPx: Float) {
        if (amountPx <= 0f || pullDistance <= 0f) return
        pullDistance = (pullDistance - amountPx).coerceAtLeast(0f)
    }

    /** Closes the gap and forgets it, for example when the conversation is left. */
    internal fun resetPull() {
        pullDistance = 0f
    }

    /** Animates the gap closed. */
    internal fun releasePull() {
        if (pullDistance <= 0f) return
        val from = pullDistance
        scope.launch {
            try {
                animate(
                    initialValue = from,
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) { value, _ ->
                    pullDistance = value
                }
            } catch (error: CancellationException) {
                if (!currentCoroutineContext().isActive) throw error
            }
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
 * @param onDismissKeyboard called when the user starts scrolling the list.
 */
@Composable
fun rememberChatScrollController(
    listState: LazyListState,
    conversationKey: Any? = null,
    autoScrollEnabled: Boolean = true,
    onDismissKeyboard: () -> Unit = {},
    onPullTriggered: () -> Unit = {},
    onPullTick: () -> Unit = {},
    onPullArmed: () -> Unit = {},
    pullEnabled: Boolean = true,
    pullDistance: Dp = PullRevealDistance,
    pullShift: Dp = PullRevealShift,
    scope: CoroutineScope = rememberCoroutineScope(),
): ChatScrollController {
    val density = LocalDensity.current
    val pullThresholdPx = with(density) { pullDistance.toPx() }
    val pullShiftMaxPx = with(density) { pullShift.toPx() }
    val controller = remember(listState) {
        ChatScrollController(
            listState = listState,
            scope = scope,
            pullThresholdPx = pullThresholdPx,
            pullShiftMaxPx = pullShiftMaxPx,
        )
    }
    SideEffect {
        controller.onDismissKeyboard = onDismissKeyboard
        controller.onPullTriggered = onPullTriggered
        controller.onPullTick = onPullTick
        controller.onPullArmed = onPullArmed
        controller.pullEnabled = pullEnabled
    }

    LaunchedEffect(listState, conversationKey, autoScrollEnabled) {
        if (!autoScrollEnabled) return@LaunchedEffect
        controller.resumeFollowing()
        controller.listState.jumpToEnd()
    }

    DisposableEffect(controller) {
        onDispose {
            // The scope that would animate the gap closed is gone with this controller, so
            // the gap is cleared outright instead of being left half open.
            controller.resetPull()
        }
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
                // An explicit scroll owns the viewport while it runs, and so does a finger.
                if (controller.isProgrammaticScroll || controller.isGestureActive) return@collect
                try {
                    listState.pinToEnd()
                } catch (error: CancellationException) {
                    // A user gesture interrupted the pin. Keep the loop alive so the list can
                    // follow again later, unless this whole effect is being cancelled.
                    if (!currentCoroutineContext().isActive) throw error
                }
            }
    }

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> controller.onGestureStart()
                is DragInteraction.Stop -> controller.onGestureEnd()
                else -> Unit
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
