package tedwester.convo.ui.chat

import android.graphics.PathMeasure as AndroidPathMeasure
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/** The shape of a pull highlight ring. */
enum class PullRingOutline {
    Circle,
    Capsule,
}

/**
 * A smooth ring that fills around a target as a pull gesture progresses.
 *
 * The ring traces the target's outline at [inset] from its edge and is drawn progressively
 * along the outline, so it fills in the space around the target rather than pulsing. The
 * unfilled remainder stays faintly visible so it is clear what is about to fill.
 */
@Composable
fun Modifier.pullHighlightRing(
    progress: Float,
    outline: PullRingOutline = PullRingOutline.Circle,
    width: Dp = 2.dp,
    inset: Dp = 2.dp,
    baseAlpha: Float = 0.16f,
    peakAlpha: Float = 0.9f,
): Modifier {
    val filled = progress.coerceIn(0f, 1f)
    if (filled <= 0.001f) return this

    // White in dark mode, the theme colour in light mode — matching the hint rings.
    val dark = isSystemInDarkTheme()
    val accent = if (dark) Color.White else MaterialTheme.colorScheme.primary
    val base = accent.copy(alpha = baseAlpha)
    val peak = accent.copy(alpha = peakAlpha)

    return drawBehind {
        val strokeWidth = max(width.toPx(), 1f)
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)

        val path = Path()
        val center = Offset(size.width / 2f, size.height / 2f)
        val insetPx = inset.toPx()
        when (outline) {
            PullRingOutline.Circle -> {
                val diameter = min(size.width, size.height) - insetPx * 2f
                if (diameter <= 0f) return@drawBehind
                val left = center.x - diameter / 2f
                val top = center.y - diameter / 2f
                path.addOval(Rect(left, top, left + diameter, top + diameter))
            }
            PullRingOutline.Capsule -> {
                val left = insetPx
                val top = insetPx
                val right = size.width - insetPx
                val bottom = size.height - insetPx
                if (right <= left || bottom <= top) return@drawBehind
                val radius = (bottom - top) / 2f
                path.addRoundRect(
                    RoundRect(left, top, right, bottom, CornerRadius(radius, radius)),
                )
            }
        }

        // The outline that is left to fill stays faintly visible.
        drawPath(path, color = base, style = stroke)

        val measure = AndroidPathMeasure(path.asAndroidPath(), false)
        val length = measure.length
        if (length <= 0f) return@drawBehind

        val segment = Path()
        measure.getSegment(0f, length * filled, segment, true)
        drawPath(segment, color = peak, style = stroke)
    }
}
