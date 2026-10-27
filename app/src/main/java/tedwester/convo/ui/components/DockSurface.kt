package tedwester.convo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Corner radius every floating dock shares, so the pills read as one family. */
internal val DockCornerRadius = 26.dp

/** Fill and hairline ring for a floating dock, resolved for the current theme. */
internal data class ConvoDockColors(
    val fill: Color,
    val ring: Color,
)

@Composable
internal fun convoDockColors(): ConvoDockColors {
    return if (isSystemInDarkTheme()) {
        ConvoDockColors(
            fill = Color(0xFF1E1D1B).copy(alpha = 0.96f),
            ring = Color.White.copy(alpha = 0.08f),
        )
    } else {
        ConvoDockColors(
            fill = Color.White.copy(alpha = 0.96f),
            ring = Color.Black.copy(alpha = 0.08f),
        )
    }
}

/**
 * The floating pill that sits over the bottom chrome — used by the quick settings dock and the
 * chat selection bar. Both live in the same corner, so they share one surface to stay legible
 * as the same kind of object when they swap.
 *
 * The caller supplies sizing and any inner padding: clipping, fill and ring are applied here.
 */
@Composable
internal fun DockSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = convoDockColors()
    val shape = RoundedCornerShape(DockCornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.fill)
            .border(1.dp, colors.ring, shape),
    ) {
        content()
    }
}
