package tedwester.convo.features.chat.state

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tedwester.convo.core.network.model.OpenRouterModel
import tedwester.convo.features.chat.model.ReasoningEffort
import tedwester.convo.features.chat.model.ReasoningPreferences
import tedwester.convo.features.chat.model.MessageAuthor
import tedwester.convo.ui.chat.modals.ModelFilterState

internal fun ChatState.onInputChangeImpl(value: String) {
    input = value
}

internal fun ChatState.startEditingMessageImpl(messageId: Long) {
    if (isRunning) return
    val message = messages.firstOrNull {
        it.id == messageId && it.author == MessageAuthor.User
    } ?: return
    editingMessageId = messageId
    input = message.userDisplayText()
}

internal fun ChatState.cancelEditingMessageImpl() {
    editingMessageId = null
    input = ""
}

internal fun ChatState.toggleSearchImpl() {
    val model = selectedModel ?: return
    if (!model.supportsWebSearch) return
    isSearchEnabled = !isSearchEnabled
}

internal fun ChatState.toggleReasoningImpl() {
    val model = selectedModel ?: return
    if (!model.supportsReasoning) return
    if (!model.canDisableReasoning && reasoningPreferences.enabled) return
    val next = reasoningPreferences.copy(enabled = !reasoningPreferences.enabled)
    persistReasoningPreferences(model.id, next)
}

internal fun ChatState.setReasoningEffortImpl(effort: ReasoningEffort) {
    val model = selectedModel ?: return
    if (!model.supportsReasoning) return
    if (!model.isEffortSupported(effort)) return
    persistReasoningPreferences(model.id, reasoningPreferences.copy(effort = effort))
}

internal fun ChatState.setStreamThinkingImpl(enabled: Boolean) {
    val model = selectedModel ?: return
    if (!model.supportsReasoning) return
    persistReasoningPreferences(
        model.id,
        reasoningPreferences.copy(streamThinking = enabled),
    )
}

internal fun ChatState.updateReasoningPreferencesImpl(prefs: ReasoningPreferences) {
    val model = selectedModel ?: return
    if (!model.supportsReasoning) return
    val effort = prefs.effort.takeIf { model.isEffortSupported(it) }
        ?: model.supportedEffortLevels().firstOrNull()
        ?: prefs.effort
    val normalized = (if (model.requiresMandatoryReasoning) {
        prefs.copy(enabled = true)
    } else {
        prefs
    }).copy(effort = effort)
    persistReasoningPreferences(model.id, normalized)
}

internal fun ChatState.saveSystemMessageImpl(value: String) {
    systemMessage = value
    val id = currentChatId ?: return
    scope.launch {
        withContext(Dispatchers.IO) {
            repository.setChatSystemMessage(id, value)
        }
        chats = withContext(Dispatchers.IO) { repository.listChats() }
    }
}

internal fun ChatState.selectModelImpl(model: OpenRouterModel) {
    bindSelectedModel(enrichModel(model))
    persist()
}

internal fun ChatState.updateModelSelectorFiltersImpl(state: ModelFilterState) {
    modelSelectorFilters = state
    val id = currentChatId ?: return
    scope.launch(Dispatchers.IO) {
        chatModelFilterStore.save(id, state)
    }
}

internal suspend fun ChatState.refreshSelectedModelMetadata() {
    val current = selectedModel ?: return
    val hasModalities =
        current.inputModalities.isNotEmpty() && current.outputModalities.isNotEmpty()
    if (hasModalities && current.supportedParameters.isNotEmpty()) return
    val cached = api.findCachedModel(current.id)
    if (cached != null) {
        applySelectedModel(cached)
        return
    }
    runCatching {
        withContext(Dispatchers.IO) { api.fetchModels(apiKey) }
    }.getOrNull()?.find { it.id == current.id }?.let { applySelectedModel(it) }
}

internal fun ChatState.applySelectedModel(model: OpenRouterModel) {
    bindSelectedModel(enrichModel(model))
}

internal fun ChatState.setSelectedTtsVoiceImpl(voiceId: String?) {
    val model = selectedModel ?: return
    if (!model.supportsVoiceSelection) return
    val normalized = voiceId?.takeIf { model.supportedVoices.isEmpty() || it in model.supportedVoices }
    persistTtsVoice(model.id, normalized)
}

internal fun ChatState.bindSelectedModel(enriched: OpenRouterModel) {
    selectedModel = enriched
    reasoningPreferences = loadReasoningPreferences(enriched.id)
    selectedTtsVoice = loadTtsVoice(enriched)
    if (!enriched.supportsWebSearch) {
        isSearchEnabled = false
    }
}

internal fun ChatState.enrichModel(model: OpenRouterModel): OpenRouterModel {
    val cached = api.findCachedModel(model.id) ?: return model
    return model.copy(
        contextLength = model.contextLength ?: cached.contextLength,
        pricing = model.pricing ?: cached.pricing,
        description = model.description ?: cached.description,
        inputModalities = model.inputModalities.ifEmpty { cached.inputModalities },
        outputModalities = model.outputModalities.ifEmpty { cached.outputModalities },
        supportedParameters = model.supportedParameters.ifEmpty { cached.supportedParameters },
        reasoningConfig = model.reasoningConfig ?: cached.reasoningConfig,
        supportedVoices = model.supportedVoices.ifEmpty { cached.supportedVoices },
    )
}

internal fun ChatState.loadReasoningPreferences(modelId: String?): ReasoningPreferences {
    if (modelId.isNullOrBlank()) return ReasoningPreferences.Default
    val loaded = reasoningStore.load(modelId)
    val model = selectedModel?.takeIf { it.id == modelId } ?: api.findCachedModel(modelId)
    val levels = model?.supportedEffortLevels().orEmpty()
    val normalizedEffort = loaded.effort.takeIf { it in levels }
        ?: model?.reasoningConfig?.defaultEffort?.takeIf { it in levels }
        ?: levels.firstOrNull { it == ReasoningEffort.Medium }
        ?: levels.firstOrNull()
        ?: loaded.effort
    return loaded.copy(
        effort = normalizedEffort,
        enabled = if (model?.requiresMandatoryReasoning == true) true else loaded.enabled,
    )
}

internal fun ChatState.persistReasoningPreferences(modelId: String, prefs: ReasoningPreferences) {
    val model = selectedModel?.takeIf { it.id == modelId }
    val normalized = if (model?.requiresMandatoryReasoning == true) {
        prefs.copy(enabled = true)
    } else {
        prefs
    }
    reasoningPreferences = normalized
    scope.launch(Dispatchers.IO) {
        reasoningStore.save(modelId, normalized)
    }
}

internal fun ChatState.loadTtsVoice(model: OpenRouterModel): String? {
    if (!model.supportsVoiceSelection) return null
    val saved = ttsVoiceStore.load(model.id)
    return saved?.takeIf { model.supportedVoices.isEmpty() || it in model.supportedVoices }
}

internal fun ChatState.persistTtsVoice(modelId: String, voiceId: String?) {
    selectedTtsVoice = voiceId
    scope.launch(Dispatchers.IO) {
        ttsVoiceStore.save(modelId, voiceId)
    }
}

internal fun ChatState.resolveTtsVoice(model: OpenRouterModel): String? =
    model.resolveVoice(selectedTtsVoice)

internal fun ChatState.snapshotExpectStreamedThinking(model: OpenRouterModel): Boolean {
    if (!model.supportsReasoning) return false
    val reasoningEnabled = reasoningPreferences.enabled || model.requiresMandatoryReasoning
    return reasoningEnabled && reasoningPreferences.streamThinking
}

internal fun ChatState.snapshotShowVoiceAsTextFirst(): Boolean =
    voiceStore.load().showVoiceRepliesAsTextFirst
