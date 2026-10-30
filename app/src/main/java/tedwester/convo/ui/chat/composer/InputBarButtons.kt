package tedwester.convo.ui.chat.composer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tedwester.convo.ui.chat.composerHintHighlightRing
import tedwester.convo.ui.components.ConvoIconButton
import tedwester.convo.ui.icons.ConvoIcons

@Composable
internal fun VoiceSelectorButton(
    label: String,
    isOpen: Boolean,
    isRecording: Boolean,
    onClick: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val defaultFill = if (dark) {
        Color.White.copy(alpha = 0.025f)
    } else {
        Color.Black.copy(alpha = 0.03f)
    }
    val defaultBorder = if (dark) {
        Color.White.copy(alpha = 0.055f)
    } else {
        Color.Black.copy(alpha = 0.055f)
    }
    val activeFill = if (dark) Color.White else MaterialTheme.colorScheme.primary
    val activeContent = if (dark) Color.Black else MaterialTheme.colorScheme.onPrimary
    val idleContent = if (dark) Color(0xFF6E6E6E) else Color(0xFF9AA3B1)

    val containerColor by animateColorAsState(
        targetValue = if (isOpen) activeFill else defaultFill,
        animationSpec = tween(240),
        label = "voiceSelectorContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isOpen) activeContent else idleContent,
        animationSpec = tween(240),
        label = "voiceSelectorContent",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isOpen) Color.Transparent else defaultBorder,
        animationSpec = tween(240),
        label = "voiceSelectorBorder",
    )

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "voiceSelectorScale",
    )
    val shape = RoundedCornerShape(999.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .widthIn(min = 72.dp, max = 132.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(containerColor)
            .border(Dp.Hairline, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isRecording,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Icon(
            painter = ConvoIcons.AudioLines(),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
internal fun AttachmentAddButton(
    isOpen: Boolean,
    isRecording: Boolean,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isOpen) 135f else 0f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 360f,
        ),
        label = "attachRotation",
    )

    ConvoIconButton(
        painter = ConvoIcons.Add(),
        contentDescription = "Add attachment",
        onClick = onClick,
        enabled = !isRecording,
        size = 36.dp,
        iconSize = 18.dp,
        iconRotation = rotation,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReasoningToggleButton(
    dark: Boolean,
    enabled: Boolean,
    canDisable: Boolean,
    isRecording: Boolean,
    highlighted: Boolean = false,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
) {
    ComposerToggleButton(
        dark = dark,
        active = enabled,
        isRecording = isRecording,
        highlighted = highlighted,
        onClick = {
            if (!enabled || canDisable) {
                onToggle()
            }
        },
        onLongClick = onLongPress,
        painter = ConvoIcons.Brain(),
        contentDescription = when {
            !enabled -> "Enable reasoning"
            canDisable -> "Disable reasoning"
            else -> "Reasoning always on — hold for settings"
        },
        containerColorLabel = "reasoningContainerColor",
        iconColorLabel = "reasoningIconColor",
        borderColorLabel = "reasoningBorderColor",
        scaleLabel = "reasoningBtnScale",
    )
}

@Composable
internal fun SearchToggleButton(
    dark: Boolean,
    enabled: Boolean,
    isRecording: Boolean,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    ComposerToggleButton(
        dark = dark,
        active = enabled,
        isRecording = isRecording,
        highlighted = highlighted,
        onClick = onClick,
        painter = ConvoIcons.Search(),
        contentDescription = if (enabled) "Disable web search" else "Enable web search",
        containerColorLabel = "searchContainerColor",
        iconColorLabel = "searchIconColor",
        borderColorLabel = "searchBorderColor",
        scaleLabel = "searchBtnScale",
    )
}

@Composable
internal fun SystemMessageButton(
    dark: Boolean,
    hasMessage: Boolean,
    isOpen: Boolean,
    isRecording: Boolean,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    ComposerToggleButton(
        dark = dark,
        active = hasMessage || isOpen,
        isRecording = isRecording,
        highlighted = highlighted,
        onClick = onClick,
        painter = ConvoIcons.MonitorCog(),
        contentDescription = "System message",
        containerColorLabel = "systemMessageContainerColor",
        iconColorLabel = "systemMessageIconColor",
        borderColorLabel = "systemMessageBorderColor",
        scaleLabel = "systemMessageBtnScale",
    )
}

@Composable
internal fun DictationMicButton(
    isRecording: Boolean,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    ConvoIconButton(
        painter = ConvoIcons.Mic(),
        contentDescription = "Dictate",
        onClick = onClick,
        enabled = !isRecording,
        size = 36.dp,
        iconSize = 18.dp,
        showBorder = !highlighted,
        modifier = Modifier.composerHintHighlightRing(highlighted),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ComposerToggleButton(
    dark: Boolean,
    active: Boolean,
    isRecording: Boolean,
    onClick: () -> Unit,
    painter: Painter,
    contentDescription: String,
    containerColorLabel: String,
    iconColorLabel: String,
    borderColorLabel: String,
    scaleLabel: String,
    highlighted: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val defaultFill = if (dark) {
        Color.White.copy(alpha = 0.025f)
    } else {
        Color.Black.copy(alpha = 0.03f)
    }
    val defaultBorder = if (dark) {
        Color.White.copy(alpha = 0.055f)
    } else {
        Color.Black.copy(alpha = 0.055f)
    }
    val activeFill = if (dark) Color.White else MaterialTheme.colorScheme.primary
    val activeIcon = if (dark) Color.Black else MaterialTheme.colorScheme.onPrimary
    val idleIcon = if (dark) Color(0xFF6E6E6E) else Color(0xFF9AA3B1)

    val containerColor by animateColorAsState(
        targetValue = if (active) activeFill else defaultFill,
        animationSpec = tween(240),
        label = containerColorLabel,
    )
    val iconColor by animateColorAsState(
        targetValue = if (active) activeIcon else idleIcon,
        animationSpec = tween(240),
        label = iconColorLabel,
    )
    val borderColor by animateColorAsState(
        targetValue = if (active && !highlighted) Color.Transparent else defaultBorder,
        animationSpec = tween(240),
        label = borderColorLabel,
    )

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = scaleLabel,
    )
    val clickEnabled = !isRecording

    Box(
        modifier = Modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(containerColor)
            .then(
                if (!highlighted && !active) {
                    Modifier.border(Dp.Hairline, borderColor, CircleShape)
                } else {
                    Modifier
                },
            )
            .composerHintHighlightRing(highlighted)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = clickEnabled,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = clickEnabled,
                        onClick = onClick,
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(18.dp),
        )
    }
}
