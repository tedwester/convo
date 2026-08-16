package tedwester.convo.ui.chat

import androidx.compose.runtime.Composable
import tedwester.convo.features.chat.data.QuickSettingsConfig
import tedwester.convo.features.chat.data.SearchPreferences
import tedwester.convo.features.chat.data.VoicePreferences
import tedwester.convo.features.chat.data.VoiceTtsMode
import tedwester.convo.features.chat.model.QuickSetting
import tedwester.convo.features.chat.model.QuickSettingIds
import tedwester.convo.features.chat.model.QuickSettingState
import tedwester.convo.features.chat.model.quickSettingDescriptor

private fun formatModelSlug(id: String): String =
    id.substringAfterLast('/').ifBlank { id }.replace('-', ' ')

/** Resolves the persisted [QuickSettingsConfig] into a live dock button list. */
@Composable
internal fun buildQuickSettings(
    config: QuickSettingsConfig,
    voicePreferences: VoicePreferences,
    onVoicePreferencesChanged: (VoicePreferences) -> Unit,
    searchPreferences: SearchPreferences,
    onSearchPreferencesChanged: (SearchPreferences) -> Unit,
    onLockApp: () -> Unit,
    onShowCredits: () -> Unit,
    onOpenTranscriptionPicker: () -> Unit,
    onOpenReplyPicker: () -> Unit,
    onOpenVoiceMode: () -> Unit,
    onNewChat: () -> Unit,
): List<QuickSetting> {
    return config.items.mapNotNull { id ->
        val descriptor = quickSettingDescriptor(id) ?: return@mapNotNull null
        val state: QuickSettingState
        val onTap: () -> Unit
        when (id) {
            QuickSettingIds.NEW_CHAT -> {
                state = QuickSettingState.Action()
                onTap = onNewChat
            }
            QuickSettingIds.BIOMETRIC_LOCK -> {
                state = QuickSettingState.Action(enabled = true)
                onTap = onLockApp
            }
            QuickSettingIds.CREDITS -> {
                state = QuickSettingState.Action(enabled = true)
                onTap = onShowCredits
            }
            QuickSettingIds.KEEP_SEARCH_ON -> {
                state = QuickSettingState.Toggle(on = searchPreferences.persistAfterPrompt)
                onTap = {
                    onSearchPreferencesChanged(
                        searchPreferences.copy(persistAfterPrompt = !searchPreferences.persistAfterPrompt),
                    )
                }
            }
            QuickSettingIds.VOICE_MODE -> {
                state = QuickSettingState.Picker(
                    valueLabel = if (voicePreferences.mode == VoiceTtsMode.SpeakMyWords) {
                        "Speak my words"
                    } else {
                        "Conversation"
                    },
                    enabled = true,
                )
                onTap = onOpenVoiceMode
            }
            QuickSettingIds.TRANSCRIPTION_MODEL -> {
                state = QuickSettingState.Picker(
                    valueLabel = formatModelSlug(voicePreferences.transcriptionModelId),
                    enabled = true,
                )
                onTap = onOpenTranscriptionPicker
            }
            QuickSettingIds.REPLY_MODEL -> {
                state = QuickSettingState.Picker(
                    valueLabel = formatModelSlug(voicePreferences.replyModelId),
                    enabled = voicePreferences.mode == VoiceTtsMode.Conversation,
                )
                onTap = onOpenReplyPicker
            }
            else -> return@mapNotNull null
        }
        QuickSetting(
            id = descriptor.id,
            label = descriptor.label,
            kind = descriptor.kind,
            icon = descriptor.icon,
            state = state,
            onTap = onTap,
        )
    }
}
