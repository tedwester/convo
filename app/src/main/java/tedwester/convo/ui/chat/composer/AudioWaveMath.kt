package tedwester.convo.ui.chat.composer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

internal val ActionButtonSize = 36.dp
internal val ActionIconSize = 18.dp

/** Per-bar phase offsets so motion doesn't read as one uniform wave. */
internal fun barPhaseOffset(index: Int): Float = index * 1.21f + index * index * 0.07f

/**
 * Blend of three out-of-sync sine layers — long before the pattern obviously repeats.
 */
internal fun layeredWave(slow: Float, mid: Float, fast: Float, barOffset: Float): Float {
    val wave =
        sin(slow + barOffset) * 0.5f +
            sin(mid * 1.37f + barOffset * 0.68f) * 0.32f +
            sin(fast * 0.91f - barOffset * 1.14f) * 0.18f
    return ((wave / 1f) + 1f) / 2f
}

@Composable
internal fun rememberWaveModulationPhases(label: String): Triple<Float, Float, Float> {
    val infinite = rememberInfiniteTransition(label = label)
    val slow by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "${label}Slow",
    )
    val mid by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1360, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "${label}Mid",
    )
    val fast by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(780, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "${label}Fast",
    )
    return Triple(slow, mid, fast)
}
