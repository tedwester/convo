package tedwester.convo.ui.chat.conversation

import androidx.compose.runtime.Composable
import tedwester.convo.core.network.OpenRouterApi
import tedwester.convo.core.security.KeyStorage
import tedwester.convo.core.security.StoredModel
import tedwester.convo.features.chat.ChatState
import tedwester.convo.ui.chat.modals.AttachmentOptionsModal
import tedwester.convo.ui.chat.modals.ModelSelectorModal
import tedwester.convo.ui.chat.modals.ReasoningSettingsModal
import tedwester.convo.ui.chat.modals.SystemMessageModal
import tedwester.convo.ui.chat.modals.VoiceSelectorModal

@Composable
internal fun ConversationScreenOverlays(
    apiKey: String,
    api: OpenRouterApi,
    keyStorage: KeyStorage,
    chatState: ChatState,
    showAttachmentOptions: Boolean,
    attachmentLaunchers: ConversationAttachmentLaunchers,
    onDismissAttachmentOptions: () -> Unit,
    showSystemMessage: Boolean,
    onDismissSystemMessage: () -> Unit,
    showReasoningSettings: Boolean,
    onDismissReasoningSettings: () -> Unit,
    showVoiceSelector: Boolean,
    onDismissVoiceSelector: () -> Unit,
    showModelSelector: Boolean,
    onDismissModelSelector: () -> Unit,
) {
    val attachModel = chatState.selectedModel
    if (showAttachmentOptions && attachModel?.supportsComposerAttachments != false) {
        AttachmentOptionsModal(
            onImageClick = attachmentLaunchers.launchGallery,
            onCameraClick = attachmentLaunchers.launchCamera,
            onFileClick = attachmentLaunchers.launchFiles,
            onDismiss = onDismissAttachmentOptions,
            allowImages = attachModel?.supportsImageAttachments != false,
            allowFiles = attachModel?.supportsFileAttachments != false,
        )
    }

    if (showSystemMessage) {
        SystemMessageModal(
            initialMessage = chatState.systemMessage,
            onSave = chatState::saveSystemMessage,
            onDismiss = onDismissSystemMessage,
        )
    }

    val reasoningModel = chatState.selectedModel
    if (showReasoningSettings && reasoningModel != null && reasoningModel.supportsReasoning) {
        ReasoningSettingsModal(
            model = reasoningModel,
            preferences = chatState.reasoningPreferences,
            onPreferencesChange = chatState::updateReasoningPreferences,
            onDismiss = onDismissReasoningSettings,
        )
    }

    val voiceModel = chatState.selectedModel
    if (showVoiceSelector && voiceModel != null && voiceModel.supportsVoiceSelection) {
        VoiceSelectorModal(
            model = voiceModel,
            selectedVoice = chatState.selectedTtsVoice,
            onVoiceSelected = chatState::setSelectedTtsVoice,
            onDismiss = onDismissVoiceSelector,
        )
    }

    if (showModelSelector) {
        ModelSelectorModal(
            apiKey = apiKey,
            api = api,
            currentModelId = chatState.selectedModel?.id,
            initialFilters = chatState.modelSelectorFilters,
            onFiltersChange = chatState::updateModelSelectorFilters,
            onSelect = { model ->
                chatState.selectModel(model)
                keyStorage.saveModel(StoredModel(id = model.id, name = model.name))
            },
            onDismiss = onDismissModelSelector,
        )
    }
}
