package tedwester.convo.ui.chat.conversation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import tedwester.convo.ui.icons.ConvoIcons

/**
 * Indicator shown in the gap that opens when pulling past the newest message.
 *
 * A grey disc with the new-chat glyph grows with the pull. At full pull it performs one
 * soft bounce, the aura around it settles into the disc edge, and the label appears.
 * Releasing while armed starts the new chat.
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
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(armed) {
        if (armed) {
            bounce.snapTo(1.14f)
            bounce.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = 380f),
            )
        }
    }

    // The disc grows from half size as the pull progresses.
    val discScale = (0.5f + 0.5f * clamped) * bounce.value
    // The aura starts wide and faint, then tightens onto the disc edge at full pull.
    val auraScale = 1.9f - 0.9f * clamped
    val auraAlpha = 0.20f * clamped

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
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .scale(auraScale * bounce.value)
                    .alpha(auraAlpha)
                    .clip(CircleShape)
                    .background(grey.copy(alpha = 0.16f)),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .scale(discScale)
                    .clip(CircleShape)
                    .background(grey.copy(alpha = 0.10f + 0.08f * clamped)),
            ) {
                Icon(
                    painter = ConvoIcons.SquarePen(),
                    contentDescription = null,
                    tint = grey.copy(alpha = 0.55f + 0.45f * clamped),
                    modifier = Modifier.size(22.dp),
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
