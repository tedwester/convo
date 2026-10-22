package tedwester.convo.ui.chat.conversation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import tedwester.convo.ui.icons.ConvoIcons

/** The total height the affordance reserves so the label never shifts the circle. */
internal val NewChatPullAffordanceHeight = 88.dp

private val CircleSize = 44.dp
private val GlyphSize = 22.dp

/**
 * The "new chat" affordance shown while pulling past the newest content.
 *
 * A circle grows out of a grey aura as the pull progresses, with the pencil glyph inside.
 * Once the pull is complete the affordance bounces and the aura firms up into the solid
 * circle, and the "New chat" label appears underneath.
 */
@Composable
fun NewChatPullAffordance(
    progress: Float,
    armed: Boolean,
    modifier: Modifier = Modifier,
) {
    val eased = FastOutSlowInEasing.transform(progress.coerceIn(0f, 1f))
    val grey = MaterialTheme.colorScheme.onSurfaceVariant

    // The whole affordance grows from small as the pull progresses.
    val grow = lerp(0.4f, 1f, eased)

    // A single overshoot bounce once the pull is complete.
    val bounce = remember { Animatable(1f) }
    LaunchedEffect(armed) {
        if (armed) {
            bounce.snapTo(1.18f)
            bounce.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 320f))
        }
    }

    val labelAlpha by animateFloatAsState(
        targetValue = if (armed) 1f else 0f,
        animationSpec = tween(140),
        label = "newChatLabelAlpha",
    )

    Column(
        modifier = modifier.height(NewChatPullAffordanceHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(CircleSize)
                .graphicsLayer {
                    val scale = grow * bounce.value
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center,
        ) {
            // The grey aura around the circle.
            Canvas(Modifier.matchParentSize()) {
                val glowRadius = size.minDimension / 2f * (1.9f - 0.9f * eased)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            grey.copy(alpha = 0.28f * eased),
                            grey.copy(alpha = 0f),
                        ),
                        center = center,
                        radius = glowRadius,
                    ),
                    radius = glowRadius,
                    center = center,
                )
            }

            // The circle the aura firms up into.
            Box(
                modifier = Modifier
                    .size(CircleSize)
                    .clip(CircleShape)
                    .background(grey.copy(alpha = 0.10f + 0.10f * eased))
                    .border(1.5.dp, grey.copy(alpha = 0.45f * eased), CircleShape),
            )

            Icon(
                painter = ConvoIcons.SquarePen(),
                contentDescription = null,
                tint = grey.copy(alpha = 0.5f + 0.5f * eased),
                modifier = Modifier.size(GlyphSize),
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
