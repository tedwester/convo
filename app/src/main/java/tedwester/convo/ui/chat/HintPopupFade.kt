package tedwester.convo.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object HintPopupFadeTokens {
    const val FadeInMs = 220
    const val FadeOutMs = 180
}

@Composable
fun rememberHintPopupFadeController(
    onDismissComplete: () -> Unit,
): HintPopupFadeController {
    val alpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val onDismissState = rememberUpdatedState(onDismissComplete)
    return remember(alpha, scope) {
        HintPopupFadeController(alpha, scope) {
            onDismissState.value()
        }
    }
}

class HintPopupFadeController internal constructor(
    private val alpha: Animatable<Float, *>,
    private val scope: CoroutineScope,
    private val onDismissComplete: () -> Unit,
) {
    val value: Float get() = alpha.value

    fun fadeIn() {
        scope.launch {
            alpha.snapTo(0f)
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = HintPopupFadeTokens.FadeInMs,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    fun dismiss() {
        scope.launch {
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = HintPopupFadeTokens.FadeOutMs,
                    easing = FastOutSlowInEasing,
                ),
            )
            onDismissComplete()
        }
    }
}

fun Modifier.hintPopupFadeLayer(alpha: Float): Modifier = graphicsLayer {
    this.alpha = alpha
    val scale = 0.96f + 0.04f * alpha
    scaleX = scale
    scaleY = scale
}
