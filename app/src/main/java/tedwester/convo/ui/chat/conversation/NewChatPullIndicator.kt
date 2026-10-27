package tedwester.convo.ui.chat.conversation

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.pow
import tedwester.convo.ui.icons.ConvoIcons

/**
 * Indicator shown in the gap that opens when pulling past the newest message.
 *
 * A single grey disc holding the new-chat glyph grows from small to full with the pull and
 * shrinks back with it. The "New chat" label appears during the last stretch of the pull.
 *
 * Size, opacity and position all follow the pull progress, so the indicator mirrors the
 * gesture exactly and can never linger over the chat while it dissolves.
 *
 * @param progress pull progress, 0..1 (1 = fully pulled / armed).
 * @param bottomInset distance from the screen bottom to the composer top (chrome + ime).
 * @param bandHeight vertical room between the composer top and the newest message ink,
 *   including the shift the pull has already opened.
 */
@Composable
fun NewChatPullIndicator(
    progress: Float,
    bottomInset: Dp,
    bandHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val p = progress.coerceIn(0f, 1f)
    val grey = MaterialTheme.colorScheme.onSurfaceVariant

    // The disc is grown from the progress so that what is on screen is exactly what the
    // gesture has spent, and it reaches full size at the arming point.
    val maxDisc = 56.dp
    val disc = (maxDisc.value * p.pow(0.8f)).dp

    // Opacity follows the disc size, so the two always move together.
    val overallAlpha = ((disc.value - 8f) / 14f).coerceIn(0f, 1f)
    val labelAlpha = ((p - 0.60f) / 0.30f).coerceIn(0f, 1f)

    // Clearance below the column keeps the label off the composer; the band keeps the disc
    // off the response above.
    val clearance = ((bandHeight.value - disc.value - 22f) / 2f).coerceAtLeast(6f).dp

    Column(
        modifier = modifier
            .offset(y = -(bottomInset + clearance))
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
                    .scale(disc.value / maxDisc.value)
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
