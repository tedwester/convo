package tedwester.convo.ui.chat.conversation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tedwester.convo.ui.icons.ConvoIcons

/**
 * Indicator shown in the gap that opens when pulling past the newest message.
 *
 * A single grey disc holding the new-chat glyph grows from small to full with the pull and
 * shrinks back with it; at full pull it performs one soft bounce. The "New chat" label
 * appears during the last stretch of the pull.
 *
 * Placement is derived, never guessed. The column is anchored above the composer by
 * [bottomInset] plus a clearance that centers it inside [bandHeight] — the room between
 * the composer top and the newest message's ink. The column's full height (disc + spacer
 * + label, whose space is reserved even while the label is invisible) always fits the
 * band with margin, so neither the disc nor the label can overlap the composer below or
 * the response above, at any point of the gesture or release. Alpha rides the same pull
 * signal the list scroll uses, so fades stay in step with the scroll settling.
 *
 * @param progress pull progress, 0..1 (1 = fully pulled / armed).
 * @param armed true once a release would start a new chat.
 * @param bottomInset distance from the screen bottom to the composer top (chrome + ime).
 * @param bandHeight vertical room between the composer top and the newest message ink,
 *   including the shift the pull has already opened.
 */
@Composable
fun NewChatPullIndicator(
    progress: Float,
    armed: Boolean,
    bottomInset: Dp,
    bandHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val p = progress.coerceIn(0f, 1f)
    val grey = MaterialTheme.colorScheme.onSurfaceVariant

    // ---- size -----------------------------------------------------------------
    // Disc diameter. Capped at full size; below the cap it tracks the open band, so small
    // pulls show a small disc. The 34dp reserve is label (16) + spacer (6) + 6dp clearance
    // on each side, which keeps the whole column inside the band with margin.
    val maxDisc = 56.dp
    val disc = (bandHeight - 34.dp).coerceIn(0.dp, maxDisc)

    // The disc is drawn at a fixed base size and scales to [disc]; the glyph scales with
    // it. One soft bounce each time the pull reaches full.
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
    val discScale = disc.value / maxDisc.value * bounce.value

    // ---- fade -----------------------------------------------------------------
    val overallAlpha = (p / 0.30f).coerceIn(0f, 1f)
    val labelAlpha = ((p - 0.60f) / 0.30f).coerceIn(0f, 1f)

    // ---- placement ------------------------------------------------------------
    // Center the whole column in the band. The label's space is always part of the column
    // height, so the disc stays put when the label fades in, and the disc's top and the
    // label's bottom both keep at least 6dp of clearance from the response and composer.
    val columnHeight = disc.value + 22f
    val bottomClearance = ((bandHeight.value - columnHeight) / 2f).coerceAtLeast(6f).dp

    Column(
        modifier = modifier
            .offset(y = -(bottomInset + bottomClearance))
            .alpha(overallAlpha),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Wrapper sized to the disc so the column's layout height matches the visual disc;
        // the inner fixed-size box only scales its drawing, glyph included.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(disc),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(maxDisc)
                    .scale(discScale)
                    .clip(CircleShape)
                    .background(grey.copy(alpha = 0.10f + 0.08f * p)),
            ) {
                Icon(
                    painter = ConvoIcons.SquarePen(),
                    contentDescription = null,
                    tint = grey.copy(alpha = 0.55f + 0.45f * p),
                    modifier = Modifier.size(22.dp),
                )
            }
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
