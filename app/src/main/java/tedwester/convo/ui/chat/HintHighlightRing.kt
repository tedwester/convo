package tedwester.convo.ui.chat

import android.graphics.PathMeasure as AndroidPathMeasure
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max

private const val HintRingRevolveMs = 2600

private const val GlowHalfWidthDegrees = 70f

private const val SegmentCount = 64

enum class HintRingOutline {
    Circle,
    Capsule,
}

@Composable
fun Modifier.hintHighlightRing(
    highlighted: Boolean,
    outline: HintRingOutline = HintRingOutline.Circle,
    width: Dp = Dp.Hairline,
): Modifier {
    val dark = isSystemInDarkTheme()
    val primary = MaterialTheme.colorScheme.primary
    val ringAlpha = remember { Animatable(0f) }
    val transition = rememberInfiniteTransition(label = "hint-ring")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = HintRingRevolveMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "hint-ring-phase",
    )

    LaunchedEffect(highlighted) {
        if (highlighted) {
            ringAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = HintPopupFadeTokens.FadeInMs,
                    easing = FastOutSlowInEasing,
                ),
            )
        } else {
            ringAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = HintPopupFadeTokens.FadeOutMs,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    if (ringAlpha.value <= 0.001f && !highlighted) return this

    val baseColor = if (dark) {
        Color.White.copy(alpha = 0.085f)
    } else {
        primary.copy(alpha = 0.12f)
    }
    val peakColor = Color.White.copy(alpha = if (dark) 0.72f else 0.85f)
    val visibility = ringAlpha.value

    return drawBehind {
        when (outline) {
            HintRingOutline.Circle -> drawCircleHintRing(
                peakAngle = -90f + phase * 360f,
                baseColor = baseColor,
                peakColor = peakColor,
                strokeWidth = max(width.toPx(), 1f),
                visibility = visibility,
            )
            HintRingOutline.Capsule -> drawCapsuleHintRing(
                peakAngle = -90f + phase * 360f,
                baseColor = baseColor,
                peakColor = peakColor,
                strokeWidth = max(width.toPx(), 1f),
                visibility = visibility,
            )
        }
    }
}

@Composable
fun Modifier.composerHintHighlightRing(
    highlighted: Boolean,
    width: Dp = Dp.Hairline,
): Modifier = hintHighlightRing(
    highlighted = highlighted,
    outline = HintRingOutline.Circle,
    width = width,
)

private fun Color.scaledBy(fraction: Float): Color = copy(alpha = alpha * fraction)

private fun DrawScope.drawCircleHintRing(
    peakAngle: Float,
    baseColor: Color,
    peakColor: Color,
    strokeWidth: Float,
    visibility: Float,
) {
    val diameter = size.minDimension - strokeWidth
    val radius = diameter / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(diameter, diameter)
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    val base = baseColor.scaledBy(visibility)
    val peak = peakColor.scaledBy(visibility)

    drawCircle(
        color = base,
        radius = radius,
        center = center,
        style = stroke,
    )

    val segmentSweep = 360f / SegmentCount
    for (i in 0 until SegmentCount) {
        val segCenter = -90f + i * segmentSweep + segmentSweep / 2f
        val falloff = glowFalloff(segCenter, peakAngle) ?: continue
        drawArc(
            color = lerpColor(base, peak, falloff),
            startAngle = segCenter - segmentSweep / 2f,
            sweepAngle = segmentSweep + 0.5f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = stroke,
        )
    }
}

private fun DrawScope.drawCapsuleHintRing(
    peakAngle: Float,
    baseColor: Color,
    peakColor: Color,
    strokeWidth: Float,
    visibility: Float,
) {
    val inset = strokeWidth / 2f
    val w = size.width - strokeWidth
    val h = size.height - strokeWidth
    if (w <= 0f || h <= 0f) return

    val corner = h / 2f
    val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
    val base = baseColor.scaledBy(visibility)
    val peak = peakColor.scaledBy(visibility)
    val roundRect = RoundRect(
        left = inset,
        top = inset,
        right = inset + w,
        bottom = inset + h,
        cornerRadius = CornerRadius(corner, corner),
    )

    drawRoundRect(
        color = base,
        topLeft = Offset(inset, inset),
        size = Size(w, h),
        cornerRadius = CornerRadius(corner, corner),
        style = stroke,
    )

    val path = Path().apply { addRoundRect(roundRect) }
    val measure = AndroidPathMeasure(path.asAndroidPath(), false)
    val length = measure.length
    if (length <= 0f) return

    val center = Offset(size.width / 2f, size.height / 2f)
    val segments = 80
    val segmentLen = length / segments
    val pos = FloatArray(2)
    val tan = FloatArray(2)

    for (i in 0 until segments) {
        val start = i * segmentLen
        val mid = start + segmentLen / 2f
        if (!measure.getPosTan(mid, pos, tan)) continue

        val angle = Math.toDegrees(
            atan2(
                (pos[1] - center.y).toDouble(),
                (pos[0] - center.x).toDouble(),
            ),
        ).toFloat()
        val falloff = glowFalloff(angle, peakAngle) ?: continue

        val x1 = pos[0]
        val y1 = pos[1]
        val end = (start + segmentLen).coerceAtMost(length)
        measure.getPosTan(end, pos, tan)
        drawLine(
            color = lerpColor(base, peak, falloff),
            start = Offset(x1, y1),
            end = Offset(pos[0], pos[1]),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

private fun glowFalloff(angle: Float, peakAngle: Float): Float? {
    val delta = angularDelta(angle, peakAngle)
    val t = (delta / GlowHalfWidthDegrees).coerceIn(-1f, 1f)
    if (abs(t) >= 1f) return null
    return ((1 + cos(t * PI)) / 2f).toFloat()
}

private fun angularDelta(a: Float, b: Float): Float {
    var d = (a - b) % 360f
    if (d > 180f) d -= 360f
    if (d < -180f) d += 360f
    return d
}

private fun lerpColor(from: Color, to: Color, fraction: Float): Color {
    return Color(
        red = from.red + (to.red - from.red) * fraction,
        green = from.green + (to.green - from.green) * fraction,
        blue = from.blue + (to.blue - from.blue) * fraction,
        alpha = from.alpha + (to.alpha - from.alpha) * fraction,
    )
}
