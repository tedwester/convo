package tedwester.convo.ui.chat.conversation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.launch
import tedwester.convo.ui.chat.message.PullRevealDistance

/**
 * An outline that fills out around its edges as the gesture progresses.
 *
 * The stroke is drawn progressively around the perimeter, so the shape grows the way a line
 * is drawn rather than by fading in.
 */
@Composable
fun PullRevealOutline(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outline,
    strokeWidth: Dp = 2.dp,
) {
    val clamped = progress.coerceIn(0f, 1f)
    Canvas(modifier) {
        val minSide = minOf(size.width, size.height)
        if (minSide <= 0f) return@Canvas

        val shapeSize = lerp(minSide * 0.24f, minSide * 0.94f, clamped)
        val rect = Rect(
            left = (size.width - shapeSize) / 2f,
            top = (size.height - shapeSize) / 2f,
            right = (size.width + shapeSize) / 2f,
            bottom = (size.height + shapeSize) / 2f,
        )
        val path = Path().apply {
            addRoundRect(RoundRect(rect, CornerRadius(shapeSize * 0.3f)))
        }

        val measure = PathMeasure()
        measure.setPath(path, false)
        val segment = Path()
        measure.getSegment(0f, measure.length * clamped, segment, true)

        drawPath(
            path = segment,
            color = color,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round),
        )
    }
}

/**
 * A pull gesture that fills out an outline and fires an action once it is fully pulled.
 *
 * The gesture is purely visual until it is complete: the outline grows as the finger travels,
 * a light haptic marks the halfway point and a firmer one marks the trigger.
 */
@Composable
fun PullRevealGesture(
    onTrigger: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    distance: Dp = PullRevealDistance,
    content: @Composable (progress: Float) -> Unit = {},
) {
    val density = LocalDensity.current
    val distancePx = with(density) { distance.toPx() }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var pullPx by remember { mutableFloatStateOf(0f) }
    var armed by remember { mutableStateOf(false) }
    var halfway by remember { mutableStateOf(false) }

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
                pullPx = value
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
                    pullPx = next
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
    ) {
        content(if (distancePx <= 0f) 0f else (pullPx / distancePx).coerceIn(0f, 1f))
    }
}
