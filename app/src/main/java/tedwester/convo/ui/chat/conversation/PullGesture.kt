package tedwester.convo.ui.chat.conversation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import tedwester.convo.ui.chat.message.PullRevealDistance

/**
 * A pull gesture that reports how far it has been pulled.
 *
 * The gesture only fires [onTrigger] once it has been pulled all the way. While it is in
 * progress [onProgress] reports the progress, which drives the highlight around whatever
 * the gesture is aiming at. A light haptic marks the halfway point and a firmer one marks
 * the moment the gesture is armed.
 */
@Composable
fun PullRevealGesture(
    onTrigger: () -> Unit,
    onProgress: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    distance: Dp = PullRevealDistance,
) {
    val density = LocalDensity.current
    val distancePx = with(density) { distance.toPx() }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var pullPx by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    var halfway by remember { mutableStateOf(false) }

    fun publish(value: Float) {
        pullPx = value
        onProgress(if (distancePx <= 0f) 0f else (value / distancePx).coerceIn(0f, 1f))
    }

    fun reset() {
        val from = pullPx
        armed = false
        halfway = false
        scope.launch {
            animate(
                initialValue = from,
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) { value, _ ->
                publish(value)
            }
        }
    }

    Box(
        modifier = modifier.pointerInput(enabled, distancePx) {
            if (!enabled) return@pointerInput
            detectVerticalDragGestures(
                onDragStart = {
                    armed = false
                    halfway = false
                },
                onDragEnd = {
                    if (armed) onTrigger()
                    reset()
                },
                onDragCancel = { reset() },
                onVerticalDrag = { _, dragAmount ->
                    val next = (pullPx + dragAmount).coerceIn(0f, distancePx)
                    publish(next)
                    val progress = if (distancePx <= 0f) 0f else next / distancePx
                    if (!halfway && progress >= 0.5f) {
                        halfway = true
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    if (!armed && progress >= 1f) {
                        armed = true
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    }
                },
            )
        },
    )
}
