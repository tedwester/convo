package tedwester.convo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object ConvoRingGapTokens {
    val Gap = 4.dp
    val BorderWidth = 0.5.dp
    val ComposerOuterRadius = 22.dp
}

@Composable
fun convoRingColor(): Color {
    val dark = isSystemInDarkTheme()
    return if (dark) {
        Color.White.copy(alpha = 0.04f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    }
}

fun Modifier.convoRingGapSurface(
    outerShape: Shape,
    innerShape: Shape,
    fillColor: Color,
    gapColor: Color,
    ringColor: Color,
    ringGap: Dp = ConvoRingGapTokens.Gap,
    borderWidth: Dp = ConvoRingGapTokens.BorderWidth,
): Modifier = this
    .border(borderWidth, ringColor, outerShape)
    .clip(outerShape)
    .background(gapColor)
    .padding(ringGap)
    .clip(innerShape)
    .background(fillColor)
