package tedwester.convo.ui.chat.conversation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import tedwester.convo.ui.icons.ConvoIcons

/** Grey band around the disc. Constant for the whole pull — never scaled. */
private val RingThickness = 5.dp

private val DiscStart = 16.dp
private val DiscBeforeCover = 26.dp

/**
 * New-chat affordance shown while pulling past the newest message.
 *
 * The grey around the disc is a stroke of fixed thickness. The disc grows inside it.
 * At a full pull the disc expands through that band, so the grey disappears because
 * the circle has filled it, then settles with one small bounce.
 */
@Composable
fun NewChatPullIndicator(
    progress: Float,
    armed: Boolean,
    modifier: Modifier = Modifier,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val grey = MaterialTheme.colorScheme.onSurfaceVariant

    val cover by animateFloatAsState(
        targetValue = if (armed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "newChatCover",
    )
    val bounce = remember { Animatable(0f) }
    LaunchedEffect(armed) {
        if (armed) {
            bounce.snapTo(3f)
            bounce.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 520f))
        } else {
            bounce.snapTo(0f)
        }
    }

    val disc = DiscStart + (DiscBeforeCover - DiscStart) * clamped +
        RingThickness * cover + bounce.value.dp
    val labelAlpha by animateFloatAsState(
        targetValue = if (armed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 500f),
        label = "newChatLabel",
    )

    Column(
        modifier = modifier.alpha(0.35f + 0.65f * clamped),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(88.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val base = (DiscStart + (DiscBeforeCover - DiscStart) * clamped).toPx()
                val thickness = RingThickness.toPx()
                // Drawn under the disc. Thickness never changes; the growing disc covers it.
                drawCircle(
                    color = grey.copy(alpha = 0.55f),
                    radius = base + thickness / 2f,
                    center = center,
                    style = Stroke(width = thickness, cap = StrokeCap.Butt),
                )
                drawCircle(
                    color = grey.copy(alpha = 0.16f),
                    radius = disc.toPx(),
                    center = center,
                )
            }
            Icon(
                painter = ConvoIcons.SquarePen(),
                contentDescription = null,
                tint = grey,
                modifier = Modifier
                    .size(20.dp)
                    .alpha(0.45f + 0.55f * clamped),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "New chat",
            style = MaterialTheme.typography.labelMedium,
            color = grey,
            modifier = Modifier.alpha(labelAlpha),
        )
    }
}
