package tedwester.convo.ui.chat.modals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tedwester.convo.features.chat.data.VoicePreferences
import tedwester.convo.features.chat.data.VoiceTtsMode
import tedwester.convo.ui.components.ConvoBottomSheet
import tedwester.convo.ui.components.ConvoPickerField
import tedwester.convo.ui.components.ConvoToggle
import tedwester.convo.ui.components.VoiceModeChip
import tedwester.convo.ui.components.rememberConvoSheetController

private fun formatModelSlug(id: String): String =
    id.substringAfterLast('/').ifBlank { id }.replace('-', ' ')

@Composable
fun VoiceModeModal(
    voicePreferences: VoicePreferences,
    onVoicePreferencesChanged: (VoicePreferences) -> Unit,
    apiKey: String,
    onOpenTranscriptionPicker: () -> Unit,
    onOpenReplyPicker: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheet = rememberConvoSheetController()
    val hasApiKey = apiKey.isNotBlank()

    val transcriptionDisplayName = if (hasApiKey) {
        formatModelSlug(voicePreferences.transcriptionModelId)
    } else {
        "Save an API key first"
    }
    val replyDisplayName = if (hasApiKey) {
        formatModelSlug(voicePreferences.replyModelId)
    } else {
        "Save an API key first"
    }

    ConvoBottomSheet(
        controller = sheet,
        onDismissRequest = onDismiss,
        useDialog = true,
        contentHorizontalPadding = 20.dp,
        contentVerticalPadding = 10.dp,
        consumeSheetClicks = false,
        title = "Voice mode",
        titleBottomSpacing = 8.dp,
        modifier = modifier,
    ) {
        Text(
            text = "When a voice model is selected, this decides what it speaks — your words, " +
                "or a generated reply.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VoiceModeChip(
                label = "Speak my words",
                description = "Speak your words in the model's voice",
                selected = voicePreferences.mode == VoiceTtsMode.SpeakMyWords,
                onClick = {
                    onVoicePreferencesChanged(
                        voicePreferences.copy(mode = VoiceTtsMode.SpeakMyWords),
                    )
                },
                modifier = Modifier.weight(1f),
            )
            VoiceModeChip(
                label = "Conversation",
                description = "Generate a reply, then speak it",
                selected = voicePreferences.mode == VoiceTtsMode.Conversation,
                onClick = {
                    onVoicePreferencesChanged(
                        voicePreferences.copy(mode = VoiceTtsMode.Conversation),
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        ConvoPickerField(
            label = "Transcription model",
            value = transcriptionDisplayName,
            enabled = hasApiKey,
            onClick = onOpenTranscriptionPicker,
        )

        Spacer(modifier = Modifier.height(16.dp))

        ConvoPickerField(
            label = "Reply model (Conversation mode)",
            value = replyDisplayName,
            enabled = hasApiKey && voicePreferences.mode == VoiceTtsMode.Conversation,
            onClick = onOpenReplyPicker,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Show voice replies as text first",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Open the script instead of the audio player; use the type button to switch back",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            ConvoToggle(
                checked = voicePreferences.showVoiceRepliesAsTextFirst,
                onCheckedChange = { enabled ->
                    onVoicePreferencesChanged(
                        voicePreferences.copy(showVoiceRepliesAsTextFirst = enabled),
                    )
                },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Play voice replies automatically",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Start playback when a voice reply arrives (happens in Conversation mode regardless of this setting)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            ConvoToggle(
                checked = voicePreferences.autoPlayVoiceReplies,
                onCheckedChange = { enabled ->
                    onVoicePreferencesChanged(
                        voicePreferences.copy(autoPlayVoiceReplies = enabled),
                    )
                },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
