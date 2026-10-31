package tedwester.convo.ui.chat.conversation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

const val ConversationTransitionMs = 320

private const val ConversationEnterMs = 320
private const val ConversationExitMs = 220
private const val ConversationRiseFraction = 0.045f
private const val ConversationSinkFraction = 0.02f
private const val ConversationExitScale = 0.985f

private val ConversationTransitionEasing = FastOutSlowInEasing

internal fun AnimatedContentTransitionScope<Int>.conversationTransitionSpec(): ContentTransform {
    val enter = fadeIn(
        animationSpec = tween(ConversationEnterMs, easing = ConversationTransitionEasing),
    ) + slideInVertically(
        animationSpec = tween(ConversationEnterMs, easing = ConversationTransitionEasing),
    ) { fullHeight -> (fullHeight * ConversationRiseFraction).toInt() }

    val exit = fadeOut(
        animationSpec = tween(ConversationExitMs, easing = ConversationTransitionEasing),
    ) + slideOutVertically(
        animationSpec = tween(ConversationExitMs, easing = ConversationTransitionEasing),
    ) { fullHeight -> (-fullHeight * ConversationSinkFraction).toInt() } +
        scaleOut(
            animationSpec = tween(ConversationExitMs, easing = ConversationTransitionEasing),
            targetScale = ConversationExitScale,
        )

    return enter togetherWith exit using SizeTransform(clip = false)
}

@Composable
internal fun ConversationSessionAnimatedContent(
    sessionId: Int,
    onSessionSettled: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit,
) {
    LaunchedEffect(sessionId) {
        if (sessionId > 0) {
            kotlinx.coroutines.delay(ConversationTransitionMs.toLong() + 32L)
            onSessionSettled()
        }
    }

    AnimatedContent(
        targetState = sessionId,
        transitionSpec = { conversationTransitionSpec() },
        modifier = modifier,
        label = "conversationSession",
        contentKey = { it },
    ) { activeSession ->
        content(activeSession)
    }
}
