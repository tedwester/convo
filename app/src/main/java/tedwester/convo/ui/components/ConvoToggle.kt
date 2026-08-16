package tedwester.convo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val ConvoToggleWidth = 51.dp
val ConvoToggleHeight = 31.dp
private val ConvoToggleThumbSize = 27.dp
private val ConvoTogglePadding = 2.dp

/**
 * Pill toggle matching the Convo design system — grey when off, white when on.
 *
 * Springs slightly on press and gives light haptic feedback, consistent with
 * [ConvoButton] and [ConvoIconButton].
 */
@Composable
fun ConvoToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val dark = isSystemInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val offTrack = if (dark) Color(0xFF2A2A2A) else Color(0xFFD8DCE3)
    val onTrack = Color.White
    val offThumb = if (dark) Color(0xFF6E6E6E) else Color.White
    val onThumb = if (dark) Color(0xFF171615) else Color(0xFF1A1D23)
    val offBorder = if (dark) {
        Color.White.copy(alpha = 0.055f)
    } else {
        Color.Black.copy(alpha = 0.055f)
    }
    val onBorder = if (dark) {
        Color.Transparent
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled -> (if (checked) onTrack else offTrack).copy(alpha = 0.45f)
            checked -> onTrack
            else -> offTrack
        },
        animationSpec = tween(200),
        label = "convoToggleTrack",
    )
    val thumbColor by animateColorAsState(
        targetValue = when {
            !enabled -> (if (checked) onThumb else offThumb).copy(alpha = 0.45f)
            checked -> onThumb
            else -> offThumb
        },
        animationSpec = tween(200),
        label = "convoToggleThumb",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> (if (checked) onBorder else offBorder).copy(alpha = 0.45f)
            checked -> onBorder
            else -> offBorder
        },
        animationSpec = tween(200),
        label = "convoToggleBorder",
    )

    val thumbTravel = ConvoToggleWidth - ConvoToggleThumbSize - ConvoTogglePadding * 2
    val thumbOffset by animateFloatAsState(
        targetValue = if (checked) thumbTravel.value else 0f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
        label = "convoToggleThumbOffset",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "convoToggleScale",
    )

    val trackShape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .width(ConvoToggleWidth)
            .height(ConvoToggleHeight)
            .clip(trackShape)
            .background(trackColor)
            .border(Dp.Hairline, borderColor, trackShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onCheckedChange(!checked)
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = ConvoTogglePadding + thumbOffset.dp)
                .size(ConvoToggleThumbSize)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}
