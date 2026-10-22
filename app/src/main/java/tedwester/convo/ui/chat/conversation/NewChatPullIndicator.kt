package tedwester.convo.ui.chat.conversation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import tedwester.convo.ui.icons.ConvoIcons

/**
 * Indicator shown in the gap that opens when pulling past the newest message.
 *
 * A grey ring of constant thickness sits around the disc. The disc grows with the pull and,
 * at full pull, performs one soft bounce that fills in where the ring was, so the grey
 * disappears. The whole indicator fades in and out with the pull so releasing without
 * triggering cleans up smoothly.
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

    val labelAlpha by animateFloatAsState(
        targetValue = if (armed) 1f else 0f,
        label = "newChatLabelAlpha",
    )

    Column(
        modifier = modifier.alpha(clamped),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(72.dp),
        ) {
            // Static grey ring of constant thickness. The growing disc covers it at full pull.
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .border(
                        width = 6.dp,
                        color = grey.copy(alpha = 0.30f),
                        shape = CircleShape,
                    ),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .scale(discScale)
                    .clip(CircleShape)
                    .background(grey.copy(alpha = 0.10f + 0.10f * clamped)),
            ) {
                Icon(
                    painter = ConvoIcons.SquarePen(),
                    contentDescription = null,
                    tint = grey.copy(alpha = 0.55f + 0.45f * clamped),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "New chat",
            style = MaterialTheme.typography.labelMedium,
            color = grey,
            modifier = Modifier.alpha(labelAlpha),
        )
    }
}
