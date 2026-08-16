package tedwester.convo.ui.chat.modals

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tedwester.convo.ui.components.ConvoRingGapTokens
import tedwester.convo.ui.components.convoRingColor
import tedwester.convo.ui.components.convoRingGapSurface
import tedwester.convo.ui.chat.HintRingOutline
import tedwester.convo.ui.chat.hintHighlightRing
import tedwester.convo.ui.theme.ConvoTheme
import tedwester.convo.ui.theme.DarkChatBox
import tedwester.convo.ui.theme.LightChatBox

/**
 * Floating model picker chip for the chat top bar.
 *
 * Outer ring → gap → filled inner pill (secondary / chat-box color), so it
 * reads as a floating selector. Long names ellipsize so menu / new-chat
 * actions keep their padding.
 */
@Composable
fun ModelSelectorChip(
    modelName: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val dark = isSystemInDarkTheme()

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 460f),
        label = "modelChipScale",
    )

    val outerShape = RoundedCornerShape(50)
    val ringColor = convoRingColor()
    val fillColor = if (dark) DarkChatBox else LightChatBox
    val pageBackground = MaterialTheme.colorScheme.background

    Row(
        modifier = modifier
            .scale(scale)
            .widthIn(max = 272.dp)
            .convoRingGapSurface(
                outerShape = outerShape,
                innerShape = outerShape,
                fillColor = fillColor,
                gapColor = pageBackground,
                ringColor = ringColor,
                ringGap = ConvoRingGapTokens.Gap,
            )
            .hintHighlightRing(
                highlighted = highlighted,
                outline = HintRingOutline.Capsule,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = modelName ?: "Select Model",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = "Change model",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Preview(name = "Model selector chip")
@Composable
private fun ModelSelectorChipPreview() {
    ConvoTheme {
        ModelSelectorChip(
            modelName = "openai/gpt-4o",
            onClick = {},
        )
    }
}

@Preview(name = "Model selector chip long")
@Composable
private fun ModelSelectorChipLongPreview() {
    ConvoTheme {
        ModelSelectorChip(
            modelName = "anthropic/claude-3.5-sonnet-20240620",
            onClick = {},
        )
    }
}
