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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tedwester.convo.core.network.model.OpenRouterModel
import tedwester.convo.ui.components.ConvoBottomSheet
import tedwester.convo.ui.components.ConvoSheetHeader
import tedwester.convo.ui.components.rememberConvoSheetController
import tedwester.convo.ui.theme.ConvoModalTokens

private const val VoiceSelectorSheetHeightFraction = 0.63f

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceSelectorModal(
    model: OpenRouterModel,
    selectedVoice: String?,
    onVoiceSelected: (String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val sheet = rememberConvoSheetController()
    val haptics = LocalHapticFeedback.current
    val options = remember(model.id, model.supportedVoices) {
        if (model.supportedVoices.isEmpty()) {
            listOf(null)
        } else {
            model.supportedVoices
        }
    }

    fun selectAndDismiss(voiceId: String?) {
        if (sheet.closing) return
        scope.launch {
            if (!sheet.animateClose()) return@launch
            onVoiceSelected(voiceId)
            delay(ConvoModalTokens.AnimMs.toLong())
            onDismiss()
        }
    }

    ConvoBottomSheet(
        controller = sheet,
        onDismissRequest = onDismiss,
        useDialog = true,
        sheetHeightFraction = VoiceSelectorSheetHeightFraction,
        contentHorizontalPadding = 20.dp,
        contentVerticalPadding = 10.dp,
        consumeSheetClicks = false,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .fillMaxHeight(),
        ) {
            ConvoSheetHeader(
                title = "Voice",
                onClose = { sheet.dismiss(scope, onDismiss) },
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Choose how ${model.name} speaks your message",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    options.forEach { voiceId ->
                        val selected =
                            model.resolveVoice(selectedVoice) == model.resolveVoice(voiceId)
                        VoiceChip(
                            label = model.voiceDisplayLabel(voiceId),
                            selected = selected,
                            onClick = {
                                if (selected) return@VoiceChip
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                selectAndDismiss(voiceId)
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun VoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val selectedBorder = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.28f)
    val idleBorder = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
    val selectedBg = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
    val selectedFg = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.95f)
    val idleFg = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)

    val borderColor by animateColorAsState(
        targetValue = if (selected) selectedBorder else idleBorder,
        animationSpec = tween(180),
        label = "voiceChipBorder",
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) selectedBg else Color.Transparent,
        animationSpec = tween(180),
        label = "voiceChipBg",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) selectedFg else idleFg,
        animationSpec = tween(180),
        label = "voiceChipFg",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "voiceChipScale",
    )

    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        color = textColor,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(999.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
