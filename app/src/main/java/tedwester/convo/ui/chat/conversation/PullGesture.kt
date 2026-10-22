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
import tedwester.convo.ui.chat.PullHaptics
import tedwester.convo.ui.chat.message.PullRevealDistance

/**
 * A vertical pull gesture that reports its progress and fires once fully pulled.
 *
 * The gesture only tracks how far it has been pulled: [onProgress] drives whatever highlight
 * the caller has attached to the pull target, and [onTrigger] fires the moment the pull is
 * complete. Haptics follow along (a light tick at each quarter, a firm one at full).
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
    var triggered by remember { mutableStateOf(false) }
    val progressHaptics = remember { PullHaptics() }

    fun report(value: Float) {
        pullPx = value
        onProgress(if (distancePx <= 0f) 0f else (value / distancePx).coerceIn(0f, 1f))
    }

    fun reset() {
        val from = pullPx
        progressHaptics.reset()
        scope.launch {
            animate(
                initialValue = from,
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) { value, _ ->
                report(value)
            }
        }
    }

    Box(
        modifier = modifier.pointerInput(enabled, distancePx) {
            if (!enabled) return@pointerInput
            detectVerticalDragGestures(
                onDragStart = {
                    triggered = false
                    progressHaptics.reset()
                },
                onDragEnd = { reset() },
                onDragCancel = { reset() },
                onVerticalDrag = { _, dragAmount ->
                    val next = (pullPx + dragAmount).coerceIn(0f, distancePx)
                    report(next)
                    val armed = progressHaptics.onProgress(
                        progress = if (distancePx <= 0f) 0f else next / distancePx,
                        tick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                        confirm = { haptics.performHapticFeedback(HapticFeedbackType.Confirm) },
                    )
                    if (armed && !triggered) {
                        triggered = true
                        onTrigger()
                    }
                },
            )
        },
    )
}
