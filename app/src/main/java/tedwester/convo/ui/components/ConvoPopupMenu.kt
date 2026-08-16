package tedwester.convo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import tedwester.convo.ui.icons.ConvoIcons
import tedwester.convo.ui.theme.ConvoDestructive
import tedwester.convo.ui.theme.convoModalSurface

object ConvoMenuTokens {
    val CornerRadius = 16.dp
    val ItemRadius = 10.dp
    val MinWidth = 172.dp
    val MaxWidth = 260.dp
    val Padding = 5.dp
    val IconSize = 18.dp
    val TriggerSize = 36.dp
    val AnchorGap = 4.dp
    val ScreenMargin = 12.dp
    const val EnterMs = 180
    const val ExitMs = 140
}

private val LocalConvoMenuDismiss = compositionLocalOf<() -> Unit> { {} }

/**
 * Anchored action menu matching Convo chrome: modal fill, hairline ring,
 * soft corners, and a quiet fade/scale — no Material dropdown elevation.
 *
 * Place next to the anchor inside a [Box]. Prefer [ConvoOverflowMenu] for
 * the standard three-dot trigger.
 */
@Composable
fun ConvoPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val alpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var mounted by remember { mutableStateOf(false) }
    val dismissCallback = rememberUpdatedState(onDismissRequest)
    val positionProvider = remember(density) {
        ConvoMenuPositionProvider(density)
    }

    LaunchedEffect(expanded) {
        if (expanded) {
            mounted = true
            alpha.snapTo(0f)
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    ConvoMenuTokens.EnterMs,
                    easing = FastOutSlowInEasing,
                ),
            )
        } else if (mounted) {
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    ConvoMenuTokens.ExitMs,
                    easing = FastOutSlowInEasing,
                ),
            )
            mounted = false
        }
    }

    fun dismiss() {
        if (!mounted) return
        scope.launch {
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    ConvoMenuTokens.ExitMs,
                    easing = FastOutSlowInEasing,
                ),
            )
            mounted = false
            dismissCallback.value()
        }
    }

    if (!mounted) return

    val dark = isSystemInDarkTheme()
    val fill = convoModalSurface()
    val ring = if (dark) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.Black.copy(alpha = 0.10f)
    }
    val shape = RoundedCornerShape(ConvoMenuTokens.CornerRadius)
    val fade = alpha.value

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = ::dismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false,
        ),
    ) {
        CompositionLocalProvider(LocalConvoMenuDismiss provides ::dismiss) {
            Column(
                modifier = modifier
                    .graphicsLayer {
                        this.alpha = fade
                        val scale = 0.96f + 0.04f * fade
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(1f, 0f)
                    }
                    .width(IntrinsicSize.Max)
                    .widthIn(
                        min = ConvoMenuTokens.MinWidth,
                        max = ConvoMenuTokens.MaxWidth,
                    )
                    .clip(shape)
                    .background(fill)
                    .border(1.dp, ring, shape)
                    .padding(ConvoMenuTokens.Padding),
                content = content,
            )
        }
    }
}

@Composable
fun ConvoPopupMenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val dismiss = LocalConvoMenuDismiss.current
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val dark = isSystemInDarkTheme()

    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f)
        destructive -> ConvoDestructive
        else -> MaterialTheme.colorScheme.onBackground
    }
    val pressFill = if (dark) {
        Color.White.copy(alpha = 0.07f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }
    val fill by animateColorAsState(
        targetValue = if (pressed && enabled) pressFill else Color.Transparent,
        animationSpec = tween(120),
        label = "convoMenuItemFill",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "convoMenuItemScale",
    )
    val itemShape = RoundedCornerShape(ConvoMenuTokens.ItemRadius)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(itemShape)
            .background(fill)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    dismiss()
                    onClick()
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(ConvoMenuTokens.IconSize),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ConvoPopupMenuDivider(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val line = if (dark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .height(0.5.dp)
            .background(line),
    )
}

/**
 * Standard three-dot trigger plus [ConvoPopupMenu], used on list rows.
 */
@Composable
fun ConvoOverflowMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier) {
        OverflowTrigger(
            contentDescription = contentDescription,
            tint = tint,
            onClick = { onExpandedChange(true) },
        )
        ConvoPopupMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            content = content,
        )
    }
}

@Composable
private fun OverflowTrigger(
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressFill = if (dark) {
        Color.White.copy(alpha = 0.06f)
    } else {
        Color.Black.copy(alpha = 0.05f)
    }
    val fill by animateColorAsState(
        targetValue = if (pressed) pressFill else Color.Transparent,
        animationSpec = tween(120),
        label = "overflowTriggerFill",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "overflowTriggerScale",
    )
    val shape = RoundedCornerShape(ConvoMenuTokens.ItemRadius)

    Box(
        modifier = Modifier
            .size(ConvoMenuTokens.TriggerSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(fill)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = ConvoIcons.EllipsisVertical(),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(ConvoMenuTokens.IconSize),
        )
    }
}

private class ConvoMenuPositionProvider(
    private val density: Density,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val gap = with(density) { ConvoMenuTokens.AnchorGap.roundToPx() }
        val margin = with(density) { ConvoMenuTokens.ScreenMargin.roundToPx() }
        val maxX = (windowSize.width - popupContentSize.width - margin)
            .coerceAtLeast(margin)
        val alignedEnd = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.right - popupContentSize.width
        } else {
            anchorBounds.left
        }
        val x = alignedEnd.coerceIn(margin, maxX)

        val below = anchorBounds.bottom + gap
        val above = anchorBounds.top - popupContentSize.height - gap
        val y = if (below + popupContentSize.height <= windowSize.height - margin) {
            below
        } else {
            above.coerceAtLeast(margin)
        }
        return IntOffset(x, y)
    }
}
