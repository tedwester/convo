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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tedwester.convo.ui.chat.message.ChatScrollController
import tedwester.convo.ui.components.ConvoIconButton
import tedwester.convo.ui.icons.ConvoIcons

private const val ShowHideMs = 220
internal val GapAboveComposer = 12.dp
private val EndPadding = 18.dp
internal val ButtonSize = 40.dp
private val IconSize = 20.dp
private val GapBetweenScrollButtons = 8.dp

@Composable
fun ConversationScrollButtons(
    controller: ChatScrollController,
    bottomInset: Dp,
    showTop: Boolean,
    showBottom: Boolean,
    hasMessages: Boolean,
    applyImePadding: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val bottomFabVisible = showBottom && hasMessages && !controller.isNearEnd
    // The top button only shows once the user has moved away from the newest content, so it
    // never sits at the bottom of a chat where it would be in the way.
    val topFabVisible = showTop && hasMessages && !controller.isNearStart && !controller.isNearEnd

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
                onClick = { controller.scrollToStart() },
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
                onClick = { controller.scrollToEnd() },
                size = ButtonSize,
                iconSize = IconSize,
            )
        }
    }
}
