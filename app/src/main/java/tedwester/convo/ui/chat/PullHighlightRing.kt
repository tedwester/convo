package tedwester.convo.ui.chat

import android.graphics.PathMeasure as AndroidPathMeasure
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

private const val PullRingRevolveMs = 2400

private const val PullRingSegments = 96

/** The shape of the ring that highlights a pull target. */
enum class PullRingOutline {
    Circle,
    Capsule,
}

/**
 * A ring that fills up while a glow revolves around it.
 *
 * The stroke is drawn along the outline from the start, so the ring fills as the gesture
 * progresses, and a slow glow travels around the whole shape while it fills.
 */
@Composable
fun Modifier.pullHighlightRing(
    progress: Float,
    outline: PullRingOutline = PullRingOutline.Circle,
    width: Dp = 1.5.dp,
): Modifier {
    val filled = progress.coerceIn(0f, 1f)
    if (filled <= 0.001f) return this

    val primary = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "pull-ring")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PullRingRevolveMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pull-ring-phase",
    )

    val baseColor = primary.copy(alpha = 0.14f)
    val peakColor = primary.copy(alpha = 0.92f)

    return drawBehind {
        val strokeWidth = max(width.toPx(), 1f)
        val inset = strokeWidth / 2f
        val w = size.width - strokeWidth
        val h = size.height - strokeWidth
        if (w <= 0f || h <= 0f) return@drawBehind

        val path = Path()
        val center = Offset(size.width / 2f, size.height / 2f)
        when (outline) {
            PullRingOutline.Circle -> {
                val diameter = min(w, h)
                val left = center.x - diameter / 2f
                val top = center.y - diameter / 2f
                path.addOval(Rect(left, top, left + diameter, top + diameter))
            }
            PullRingOutline.Capsule -> {
                path.addRoundRect(
                    RoundRect(
                        left = inset,
                        top = inset,
                        right = inset + w,
                        bottom = inset + h,
                        cornerRadius = CornerRadius(h / 2f, h / 2f),
                    ),
                )
            }
        }

        val measure = AndroidPathMeasure(path.asAndroidPath(), false)
        val length = measure.length
        if (length <= 0f) return@drawBehind

        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)

        // The whole outline stays faintly visible so it is clear what is about to fill.
        drawPath(path, color = baseColor, style = stroke)

        val peakAngle = -90f + phase * 360f
        val fillLength = length * filled
        val segmentLength = length / PullRingSegments
        val position = FloatArray(2)
        val tangent = FloatArray(2)

        for (index in 0 until PullRingSegments) {
            val start = index * segmentLength
            if (start >= fillLength) break
            val end = minOf(start + segmentLength, fillLength)
            val middle = (start + end) / 2f
            if (!measure.getPosTan(middle, position, tangent)) continue

            val angle = Math.toDegrees(
                kotlin.math.atan2(
                    (position[1] - center.y).toDouble(),
                    (position[0] - center.x).toDouble(),
                ),
            ).toFloat()

            val falloff = glowFalloff(angle, peakAngle) ?: 0f
            val from = Offset(position[0], position[1])
            if (!measure.getPosTan(end, position, tangent)) continue
            drawLine(
                color = lerpColor(baseColor, peakColor, falloff),
                start = from,
                end = Offset(position[0], position[1]),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
