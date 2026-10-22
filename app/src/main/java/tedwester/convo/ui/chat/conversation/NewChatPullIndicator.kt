package tedwester.convo.ui.chat.conversation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tedwester.convo.ui.icons.ConvoIcons

private val DiscMin = 34.dp
private val DiscMax = 46.dp
private val RingWidth = 3.dp
private val RingGap = 6.dp
private val GlyphSize = 22.dp

/**
 * Indicator shown in the gap that opens when pulling past the newest message.
 *
 * A grey ring of constant thickness surrounds a disc with the new-chat glyph. The disc
 * grows with the pull; at full pull the ring fades out while the disc expands to fill
 * where the ring was, with one soft bounce. Releasing while armed starts the new chat.
 *
 * Everything is sized in Dp rather than scaled, so the ring thickness never changes.
 */
@Composable
fun NewChatPullIndicator(
    progress: Float,
    armed: Boolean,
    modifier: Modifier = Modifier,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val grey = MaterialTheme.colorScheme.onSurfaceVariant

    // One soft bounce each time the pull reaches full.
    val bounce = remember { Animatable(0f) }
    LaunchedEffect(armed) {
        if (armed) {
            bounce.snapTo(1f)
            bounce.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = 380f),
            )
        } else {
            bounce.snapTo(0f)
        }
    }
    // Overshoot in Dp so the ring thickness stays constant through the bounce.
    val overshoot: Dp = 5.dp * bounce.value

    val discSize: Dp = DiscMin + (DiscMax - DiscMin) * clamped + overshoot +
        (if (armed) RingGap + RingWidth else 0.dp)
    val ringAlpha = if (armed) 0f else 0.45f * clamped

    val labelAlpha by animateFloatAsState(
        targetValue = if (armed) 1f else 0f,
        label = "newChatLabelAlpha",
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(72.dp),
        ) {
            // Constant-thickness ring; fades out at full pull as the disc fills it.
            Box(
                modifier = Modifier
                    .size(discSize + RingGap * 2f + RingWidth * 2f)
                    .alpha(ringAlpha)
                    .border(RingWidth, grey, CircleShape),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(discSize)
                    .clip(CircleShape)
                    .background(grey.copy(alpha = 0.10f + 0.08f * clamped)),
            ) {
                Icon(
                    painter = ConvoIcons.SquarePen(),
                    contentDescription = null,
                    tint = grey.copy(alpha = 0.55f + 0.45f * clamped),
                    modifier = Modifier.size(GlyphSize),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "New chat",
            style = MaterialTheme.typography.labelMedium,
            color = grey,
            modifier = Modifier.alpha(labelAlpha),
        )
    }
}
