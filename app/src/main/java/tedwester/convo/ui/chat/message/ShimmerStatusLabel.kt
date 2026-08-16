package tedwester.convo.ui.chat.message

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tedwester.convo.ui.theme.AssistantSerifFamily

internal val StatusLabelGrey = Color(0xFF9A9A9A)

@Composable
internal fun ShimmerStatusLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "status-shimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmer-phase",
    )

    val base = StatusLabelGrey
    val highlight = Color(0xFFFFFFFF)
    val travel = 420f
    val x = -120f + phase * travel
    val brush = Brush.linearGradient(
        colorStops = arrayOf(
            0.0f to base,
            0.35f to base,
            0.5f to highlight,
            0.65f to base,
            1.0f to base,
        ),
        start = Offset(x, 0f),
        end = Offset(x + 160f, 0f),
    )

    Text(
        text = text,
        style = TextStyle(
            fontFamily = AssistantSerifFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 15.5.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.1.sp,
            brush = brush,
        ),
        modifier = modifier.padding(vertical = 2.dp),
    )
}

@Composable
internal fun StaticStatusLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = TextStyle(
            fontFamily = AssistantSerifFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 15.5.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.1.sp,
            color = StatusLabelGrey,
        ),
        modifier = modifier.padding(vertical = 2.dp),
    )
}
