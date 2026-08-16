package tedwester.convo.ui.chat.modals

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tedwester.convo.core.network.model.OpenRouterModel
import tedwester.convo.features.chat.model.ReasoningEffort
import tedwester.convo.features.chat.model.ReasoningPreferences
import tedwester.convo.ui.components.ConvoBottomSheet
import tedwester.convo.ui.components.ConvoToggle
import tedwester.convo.ui.components.rememberConvoSheetController

/**
 * Bottom sheet for per-model reasoning effort and stream-thinking settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReasoningSettingsModal(
    model: OpenRouterModel,
    preferences: ReasoningPreferences,
    onPreferencesChange: (ReasoningPreferences) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val sheet = rememberConvoSheetController()
    val haptics = LocalHapticFeedback.current

    ConvoBottomSheet(
        controller = sheet,
        onDismissRequest = onDismiss,
        useDialog = true,
        contentHorizontalPadding = 20.dp,
        contentVerticalPadding = 10.dp,
        consumeSheetClicks = false,
        title = "Reasoning",
        titleBottomSpacing = 8.dp,
        modifier = modifier,
    ) {
        if (model.requiresMandatoryReasoning) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This model always reasons, you cannot turn it off.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            )
        } else if (!preferences.enabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Reasoning is off — settings apply when you turn it on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Effort",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
        )
        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ReasoningEffort.entries.forEach { effort ->
                EffortChip(
                    label = effort.label,
                    selected = preferences.effort == effort,
                    enabled = model.isEffortSupported(effort),
                    onClick = {
                        if (preferences.effort == effort) return@EffortChip
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onPreferencesChange(preferences.copy(effort = effort))
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp),
            ) {
                Text(
                    text = "Stream thinking",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Show the model’s reasoning while it thinks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                )
            }
            ConvoToggle(
                checked = preferences.streamThinking,
                onCheckedChange = { checked ->
                    onPreferencesChange(preferences.copy(streamThinking = checked))
                },
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun EffortChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val selectedBorder = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.28f)
    val idleBorder = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
    val disabledBorder = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
    val selectedBg = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
    val selectedFg = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.95f)
    val idleFg = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
    val disabledFg = if (dark) {
        Color.White.copy(alpha = 0.22f)
    } else {
        Color.Black.copy(alpha = 0.28f)
    }

    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> disabledBorder
            selected -> selectedBorder
            else -> idleBorder
        },
        animationSpec = tween(200),
        label = "effortChipBorder",
    )
    val bgColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Transparent
            selected -> selectedBg
            else -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "effortChipBg",
    )
    val fgColor by animateColorAsState(
        targetValue = when {
            !enabled -> disabledFg
            selected -> selectedFg
            else -> idleFg
        },
        animationSpec = tween(200),
        label = "effortChipFg",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "effortChipScale",
    )

    val shape = RoundedCornerShape(999.dp)

    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
        fontWeight = if (selected && enabled) FontWeight.Medium else FontWeight.Normal,
        color = fgColor,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(bgColor)
            .border(1.dp, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
