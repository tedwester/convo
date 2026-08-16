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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Header circular controls (search / back / settings / close). */
val ConvoIconButtonSize = 48.dp
/** ~48% of [ConvoIconButtonSize] so the glyph has room inside the ring. */
val ConvoIconGlyphSize = 22.dp
val ConvoIconButtonGap = 10.dp

/**
 * Circular icon control matching the reference chips:
 * subtle fill, thin outline ring, and a simple brighter fill on press.
 */
@Composable
fun ConvoIconButton(
    painter: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = ConvoIconButtonSize,
    iconSize: Dp = ConvoIconGlyphSize,
    containerColor: Color? = null,
    contentColor: Color? = null,
    pressedContainerColor: Color? = null,
    borderColor: Color? = null,
    showBorder: Boolean = true,
    iconRotation: Float = 0f,
) {
    val dark = isSystemInDarkTheme()
    val defaultContent = if (dark) {
        Color(0xFFE8E8E8)
    } else {
        Color(0xFF1A1A1A)
    }
    val tintBase = contentColor ?: defaultContent
    val iconTint = if (enabled) tintBase else tintBase.copy(alpha = 0.38f)

    ConvoIconButtonChrome(
        onClick = onClick,
        contentDescription = contentDescription,
        modifier = modifier,
        enabled = enabled,
        size = size,
        containerColor = containerColor,
        pressedContainerColor = pressedContainerColor,
        borderColor = borderColor,
        showBorder = showBorder,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier
                .size(iconSize)
                .graphicsLayer { rotationZ = iconRotation },
        )
    }
}

@Composable
private fun ConvoIconButtonChrome(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier,
    enabled: Boolean,
    size: Dp,
    containerColor: Color?,
    pressedContainerColor: Color?,
    borderColor: Color?,
    showBorder: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val defaultFill = if (dark) {
        Color.White.copy(alpha = 0.025f)
    } else {
        Color.Black.copy(alpha = 0.03f)
    }
    val defaultPressed = if (dark) {
        Color.White.copy(alpha = 0.065f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }
    val defaultBorder = if (dark) {
        Color.White.copy(alpha = 0.055f)
    } else {
        Color.Black.copy(alpha = 0.055f)
    }

    val idleFill = containerColor ?: defaultFill
    val pressFill = pressedContainerColor ?: defaultPressed
    val ring = borderColor ?: defaultBorder

    val fill by animateColorAsState(
        targetValue = when {
            !enabled -> idleFill.copy(alpha = idleFill.alpha * 0.5f)
            pressed -> pressFill
            else -> idleFill
        },
        animationSpec = tween(180),
        label = "iconBtnFill",
    )
    val ringColor by animateColorAsState(
        targetValue = when {
            !enabled -> ring.copy(alpha = ring.alpha * 0.5f)
            pressed -> if (dark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.07f)
            else -> ring
        },
        animationSpec = tween(180),
        label = "iconBtnRing",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 420f,
        ),
        label = "iconBtnScale",
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(fill)
            .then(
                if (showBorder) {
                    Modifier.border(
                        width = Dp.Hairline,
                        color = ringColor,
                        shape = CircleShape,
                    )
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Circular icon control with a custom icon slot (e.g. animated drawables). */
@Composable
fun ConvoIconButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = ConvoIconButtonSize,
    iconSize: Dp = ConvoIconGlyphSize,
    containerColor: Color? = null,
    contentColor: Color? = null,
    pressedContainerColor: Color? = null,
    borderColor: Color? = null,
    showBorder: Boolean = true,
    iconRotation: Float = 0f,
    icon: @Composable (Modifier) -> Unit,
) {
    ConvoIconButtonChrome(
        onClick = onClick,
        contentDescription = contentDescription,
        modifier = modifier,
        enabled = enabled,
        size = size,
        containerColor = containerColor,
        pressedContainerColor = pressedContainerColor,
        borderColor = borderColor,
        showBorder = showBorder,
    ) {
        icon(
            Modifier
                .size(iconSize)
                .graphicsLayer { rotationZ = iconRotation },
        )
    }
}

/** Convenience overload for Material / Compose [ImageVector] icons. */
@Composable
fun ConvoIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = ConvoIconButtonSize,
    iconSize: Dp = ConvoIconGlyphSize,
    containerColor: Color? = null,
    contentColor: Color? = null,
    pressedContainerColor: Color? = null,
    borderColor: Color? = null,
    showBorder: Boolean = true,
    iconRotation: Float = 0f,
) {
    ConvoIconButton(
        painter = rememberVectorPainter(icon),
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        size = size,
        iconSize = iconSize,
        containerColor = containerColor,
        contentColor = contentColor,
        pressedContainerColor = pressedContainerColor,
        borderColor = borderColor,
        showBorder = showBorder,
        iconRotation = iconRotation,
    )
}
