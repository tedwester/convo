package tedwester.convo.ui.chat.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tedwester.convo.features.chat.model.ChatMessage
import tedwester.convo.ui.components.CopyButton
import tedwester.convo.ui.icons.ConvoIcons
import tedwester.convo.ui.theme.AssistantSerifFamily

@Composable
internal fun AssistantResponseControls(
    message: ChatMessage,
    controlsVisible: Boolean,
    responseActionsEnabled: Boolean = controlsVisible,
    controlsAlpha: Float,
    onRegenerate: (() -> Unit)?,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDownload: (() -> Unit)? = null,
    showVoiceAsText: Boolean = false,
    onToggleVoiceDisplay: (() -> Unit)? = null,
    onVariantChange: (delta: Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val muted = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    // Media variants page along attachments rather than text, so the chevrons follow
    // the shared slot count — otherwise they stay dead on image and video replies.
    val hasAudio = message.hasAudioAttachment()
    val hasDownloadableMedia = hasAudio || message.hasVideoAttachment()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .height(36.dp)
            .graphicsLayer { alpha = controlsAlpha.coerceIn(0f, 1f) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MessageActionButton(
                painter = ConvoIcons.Repeat(),
                contentDescription = "Regenerate",
                enabled = responseActionsEnabled && onRegenerate != null,
                onClick = { onRegenerate?.invoke() },
            )
            CopyButton(
                enabled = controlsVisible && message.copyableText().isNotBlank(),
                onCopy = onCopy,
            )
            MessageActionButton(
                painter = ConvoIcons.Share2(),
                contentDescription = "Share",
                enabled = controlsVisible,
                onClick = onShare,
            )
            if (hasAudio && onToggleVoiceDisplay != null) {
                MessageActionButton(
                    painter = if (showVoiceAsText) {
                        ConvoIcons.Speech()
                    } else {
                        ConvoIcons.Type()
                    },
                    contentDescription = if (showVoiceAsText) {
                        "Show voice message"
                    } else {
                        "Show text"
                    },
                    enabled = controlsVisible,
                    onClick = onToggleVoiceDisplay,
                )
            }
            if (hasDownloadableMedia && onDownload != null) {
                MessageActionButton(
                    painter = ConvoIcons.Download(),
                    contentDescription = "Download",
                    enabled = controlsVisible,
                    onClick = onDownload,
                )
            }
        }
        if (message.variantCount > 1) {
            VariantStepperRow(
                message = message,
                actionsEnabled = responseActionsEnabled,
                onVariantChange = onVariantChange,
                prevDescription = "Previous response",
                nextDescription = "Next response",
                counterColor = muted,
            )
        }
    }
}

@Composable
internal fun AssistantStatusText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = TextStyle(
            fontFamily = AssistantSerifFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 15.5.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.1.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    )
}
