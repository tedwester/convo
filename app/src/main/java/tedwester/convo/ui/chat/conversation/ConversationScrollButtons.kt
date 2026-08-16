package tedwester.convo.ui.chat.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import tedwester.convo.ui.chat.message.VisualBottomThresholdPx
import tedwester.convo.ui.chat.message.VisualTopThresholdPx
import tedwester.convo.ui.chat.message.isAtVisualBottom
import tedwester.convo.ui.chat.message.isAtVisualTop
import tedwester.convo.ui.components.ConvoIconButton
import tedwester.convo.ui.icons.ConvoIcons

private const val ShowHideMs = 220
internal val GapAboveComposer = 12.dp
private val EndPadding = 18.dp
internal val ButtonSize = 40.dp
private val IconSize = 20.dp
private val GapBetweenScrollButtons = 8.dp

private const val HideBottomThresholdPx = 80
private const val HideTopThresholdPx = 80

/**
 * Bottom-right scroll FABs. The top button sits above the bottom one when it is
 * visible; otherwise it animates down to the bottom slot.
 */
@Composable
fun ConversationScrollButtons(
    listState: LazyListState,
    bottomInset: Dp,
    showTop: Boolean,
    showBottom: Boolean,
    hasMessages: Boolean,
    applyImePadding: Boolean = true,
    onResumeFollow: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var topScrolledAway by remember(listState) { mutableStateOf(false) }
    var bottomScrolledAway by remember(listState) { mutableStateOf(false) }

    LaunchedEffect(listState) {
        snapshotFlow {
            val away = !listState.isAtVisualTop(VisualTopThresholdPx)
            val near = listState.isAtVisualTop(HideTopThresholdPx)
            away to near
        }
            .distinctUntilChanged()
            .collect { (away, near) ->
                topScrolledAway = when {
                    away -> true
                    near -> false
                    else -> topScrolledAway
                }
            }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val away = !listState.isAtVisualBottom(VisualBottomThresholdPx)
            val near = listState.isAtVisualBottom(HideBottomThresholdPx)
            away to near
        }
            .distinctUntilChanged()
            .collect { (away, near) ->
                bottomScrolledAway = when {
                    away -> true
                    near -> false
                    else -> bottomScrolledAway
                }
            }
    }

    val bottomFabVisible = showBottom && hasMessages && bottomScrolledAway
    val topFabVisible = showTop && hasMessages && topScrolledAway

    val topBottomPadding by animateDpAsState(
        targetValue = if (bottomFabVisible) {
            bottomInset + GapAboveComposer + ButtonSize + GapBetweenScrollButtons
        } else {
            bottomInset + GapAboveComposer
        },
        animationSpec = tween(ShowHideMs, easing = FastOutSlowInEasing),
        label = "scrollTopBottomPadding",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .then(if (applyImePadding) Modifier.imePadding() else Modifier),
    ) {
        AnimatedVisibility(
            visible = topFabVisible,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = EndPadding, bottom = topBottomPadding),
            enter = fadeIn(tween(ShowHideMs, easing = FastOutSlowInEasing)) +
                slideInVertically(tween(ShowHideMs, easing = FastOutSlowInEasing)) { it / 2 },
            exit = fadeOut(tween(ShowHideMs - 40, easing = FastOutSlowInEasing)) +
                slideOutVertically(tween(ShowHideMs - 40, easing = FastOutSlowInEasing)) { it / 2 },
        ) {
            ConvoIconButton(
                painter = ConvoIcons.ChevronUp(),
                contentDescription = "Scroll to start of chat",
                onClick = {
                    scope.launch { listState.scrollToItem(0) }
                    topScrolledAway = false
                },
                size = ButtonSize,
                iconSize = IconSize,
            )
        }

        AnimatedVisibility(
            visible = bottomFabVisible,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = EndPadding, bottom = bottomInset + GapAboveComposer),
            enter = fadeIn(tween(ShowHideMs, easing = FastOutSlowInEasing)) +
                slideInVertically(tween(ShowHideMs, easing = FastOutSlowInEasing)) { it / 2 },
            exit = fadeOut(tween(ShowHideMs - 40, easing = FastOutSlowInEasing)) +
                slideOutVertically(tween(ShowHideMs - 40, easing = FastOutSlowInEasing)) { it / 2 },
        ) {
            ConvoIconButton(
                painter = ConvoIcons.ChevronDown(),
                contentDescription = "Scroll to latest messages",
                onClick = {
                    onResumeFollow()
                    bottomScrolledAway = false
                },
                size = ButtonSize,
                iconSize = IconSize,
            )
        }
    }
}
