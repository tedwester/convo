package tedwester.convo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tedwester.convo.ui.icons.ConvoIcons
import tedwester.convo.ui.theme.ConvoShapes

@Composable
fun ConvoButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    containerHeight: Int = 52,
    containerColor: Color = Color.White,
    contentColor: Color = Color.Black,
    disabledContainerColor: Color? = null,
    disabledContentColor: Color? = null,
    textStyle: TextStyle? = null,
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val dark = isSystemInDarkTheme()

    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !loading) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 420f),
        label = "convoButtonScale",
    )

    val mutedFill = disabledContainerColor ?: if (dark) {
        Color(0xFF2A2928)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }
    val mutedLabel = disabledContentColor ?: if (dark) {
        Color.White.copy(alpha = 0.22f)
    } else {
        Color.Black.copy(alpha = 0.28f)
    }

    val fill by animateColorAsState(
        targetValue = if (enabled) containerColor else mutedFill,
        animationSpec = tween(180),
        label = "convoButtonFill",
    )
    val labelColor by animateColorAsState(
        targetValue = if (enabled) contentColor else mutedLabel,
        animationSpec = tween(180),
        label = "convoButtonLabel",
    )

    val clickable = enabled && !loading

    Box(
        modifier = modifier
            .height(containerHeight.dp)
            .scale(scale)
            .clip(ConvoShapes.large)
            .background(fill)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = clickable,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            SpinningLoader(
                tint = if (enabled) contentColor else mutedLabel,
                modifier = Modifier.size(22.dp),
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                when {
                    iconPainter != null -> {
                        Icon(
                            painter = iconPainter,
                            contentDescription = null,
                            tint = labelColor,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    icon != null -> {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = labelColor,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
                Text(
                    text = text,
                    style = textStyle ?: MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                    color = labelColor,
                )
            }
        }
    }
}

@Composable
fun SpinningLoader(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "loaderSpin")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
        ),
        label = "loaderRotation",
    )
    Icon(
        painter = ConvoIcons.LoaderCircle(),
        contentDescription = null,
        tint = tint,
        modifier = modifier.rotate(rotation),
    )
}
