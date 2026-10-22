package tedwester.convo.ui.chat.conversation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
 * All geometry is derived from [bandHeight] — the room currently available between the top
 * of the composer and the newest message's ink. The assembly therefore always fits inside
 * that gap with a clearance on both sides, at every instant of the gesture, and can never
 * overlap the composer below or the response above.
 *
 * Visual structure, bottom to top of the drawn ink:
 *
 *  - one disc holding the new-chat glyph, growing from small to full with the pull;
 *  - exactly one grey ring hugging the disc's edge (the border is drawn over the disc, so
 *    they are always attached, never separated);
 *  - the "New chat" label, which appears during the last stretch of the pull.
 *
 * The whole assembly grows with the pull and shrinks back with it. In the final stretch
 * the ring thins away and the disc grows into its place, so at full pull the grey ring is
 * gone, replaced by the circle. Alpha rides the same pull signal the list scroll uses, so
 * fades stay in step with the scroll settling and nothing lingers over the content.
 *
 * @param progress pull progress, 0..1 (1 = fully pulled / armed).
 * @param armed true once a release would start a new chat.
 * @param bandHeight vertical room between the composer top and the newest message ink,
 *   including the shift the pull has already opened.
 */
@Composable
fun NewChatPullIndicator(
    progress: Float,
    armed: Boolean,
    bandHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val p = progress.coerceIn(0f, 1f)
    val grey = MaterialTheme.colorScheme.onSurfaceVariant

    // ---- size -----------------------------------------------------------------
    // Total outer diameter of disc + ring. Capped at the full size; below the cap it
    // tracks the open band so small pulls show a small assembly. The reserved space is
    // label (16) + spacer (6) + 5dp clearance on each side, so the ink fits with margin.
    val maxOuter = 56.dp
    val outer = (bandHeight - 32.dp).coerceIn(0.dp, maxOuter)

    // Ring thickness: a constant fraction of the assembly (same look at every size),
    // thinning away only in the last stretch of the pull, where the disc overtakes it.
    val absorb = smoothStep(((p - 0.72f) / 0.28f).coerceIn(0f, 1f))
    val ringWidth = outer.value * 0.10f * (1f - absorb)
    val discDiameter = outer.value - 2f * ringWidth

    // The disc is drawn at a fixed base size and scales to [discDiameter]; the glyph
    // scales with it. One soft bounce each time the pull reaches full.
    val discBase = maxOuter.value
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
    val discScale = discDiameter / discBase * bounce.value

    // ---- fade -----------------------------------------------------------------
    // Ramps in quickly as the pull starts, and both position and alpha collapse with
    // the same animated pull signal on release, so the indicator disappears in step
    // with the scroll returning.
    val overallAlpha = (p / 0.30f).coerceIn(0f, 1f)
    val labelAlpha = ((p - 0.60f) / 0.30f).coerceIn(0f, 1f)

    // ---- placement ------------------------------------------------------------
    // Bottom clearance centers the currently visible ink inside the band. While the
    // label is hidden only the disc counts, so the disc floats centered in the gap and
    // glides down gently as the label appears below it.
    val visibleInk = outer.value + 22f * labelAlpha
    val bottomClearance = ((bandHeight.value - visibleInk) / 2f).coerceAtLeast(6f).dp

    Column(
        modifier = modifier
            .offset(y = -bottomClearance)
            .alpha(overallAlpha),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(outer),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(maxOuter)
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
            // One ring, painted over the disc's edge so the two are always attached.
            Box(
                modifier = Modifier
                    .size(outer)
                    .border(
                        width = ringWidth.dp,
                        color = grey.copy(alpha = 0.30f),
                        shape = CircleShape,
                    ),
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

/** Hermite interpolation between 0 and 1 at [t] (0..1), for easing the ring absorption. */
private fun smoothStep(t: Float): Float = t * t * (3f - 2f * t)
