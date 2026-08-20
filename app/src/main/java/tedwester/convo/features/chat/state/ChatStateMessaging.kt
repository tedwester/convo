package tedwester.convo.features.chat.state

import android.os.SystemClock
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tedwester.convo.core.network.model.ChatMessageDto
import tedwester.convo.core.network.model.ModelKind
import tedwester.convo.core.network.model.OpenRouterModel
import tedwester.convo.core.network.model.TranscriptionRequest
import tedwester.convo.features.chat.data.AttachmentStore
import tedwester.convo.features.chat.data.VoicePreferences
import tedwester.convo.features.chat.model.ChatMessage
import tedwester.convo.features.chat.model.MessageAuthor
import tedwester.convo.features.chat.model.asStopped
import tedwester.convo.features.chat.model.ensureVariantContinuations
import tedwester.convo.features.chat.model.withActiveVariantFields
import java.io.File

internal fun ChatState.sendImpl() {
    if (selectedModel == null) return
    val editingId = editingMessageId
    if (editingId != null) {
        val text = input.trim()
        val message = messages.firstOrNull {
            it.id == editingId && it.author == MessageAuthor.User
        }
        if (message != null && (text.isNotBlank() || message.attachments.isNotEmpty())) {
            editingMessageId = null
            input = ""
            resendUserMessageImpl(editingId, text)
        }
        return
    }
    val text = input.trim()
    val attachments = pendingAttachments.toList()
    if (text.isEmpty() && attachments.isEmpty()) return

    scope.launch {
        ensureCurrentChat()

        val files = attachments.filterNot { it.isImage }
        val fileNote = if (files.isNotEmpty()) {
            files.joinToString(separator = "\n") { "📎 ${it.displayName}" }
        } else {
            ""
        }
        val displayContent = listOf(text, fileNote)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")

        messages += ChatMessage(
            id = ++currentId,
            author = MessageAuthor.User,
            content = displayContent,
            timestamp = System.currentTimeMillis(),
            attachments = attachments,
        )
        input = ""
        clearPendingAttachments()
        persist()
        startReplyForLatestUserMessage()
    }
}

internal fun ChatState.resendUserMessageImpl(messageId: Long, editedText: String? = null) {
    if (isRunning) return
    if (selectedModel == null) return
    if (currentChatId == null) return
    val index = messages.indexOfFirst {
        it.id == messageId && it.author == MessageAuthor.User
    }
    if (index < 0) return

    val existing = messages[index]
    val updated = if (editedText != null) {
        existing.withUserDisplayText(editedText)
    } else {
        existing
    }
    if (updated.content.isBlank() && updated.attachments.isEmpty()) return

    val next = messages.getOrNull(index + 1)
    val unchangedPrompt = updated == existing
    if (unchangedPrompt && next != null && next.author == MessageAuthor.Assistant) {
        regenerateImpl(next.id)
        return
    }

    if (updated != existing) {
        messages[index] = updated
    }
    if (index + 1 < messages.size) {
        messages.removeRange(index + 1, messages.size)
        bumpMessageListRevision()
    }
    persist(bumpRecency = true)
    startReplyForLatestUserMessage()
}

internal fun ChatState.startReplyForLatestUserMessage() {
    val model = selectedModel ?: return
    val user = messages.lastOrNull() ?: return
    if (user.author != MessageAuthor.User) return

    val text = user.userDisplayText()
    val attachments = user.attachments
    val images = attachments.filter { it.isImage }
    val files = attachments.filterNot { it.isImage }
    val displayContent = user.content

    if (model.supportsSpeechOutput) {
        runSpeechReply(model, text.ifBlank { displayContent })
        return
    }

    when (model.modelKind) {
        ModelKind.ImageGen -> {
            runImageCompletion(model, text.ifBlank { displayContent })
            return
        }
        ModelKind.VideoGen -> {
            runVideoCompletion(model, text, images)
            return
        }
        ModelKind.Embedding, ModelKind.Rerank -> {
            appendUnsupportedModelWarning(
                model,
                "isn't a chat model — it can't hold a conversation.",
            )
            return
        }
        ModelKind.Transcription -> {
            val recording = loadVoiceRecordingFromMessage(user)
            if (recording != null) {
                val (audioBytes, format) = recording
                runTranscriptionCompletion(model, audioBytes, format)
            } else {
                appendUnsupportedModelWarning(
                    model,
                    "only transcribes audio. Send a voice message to use it.",
                )
            }
            return
        }
        ModelKind.Chat, ModelKind.Tts -> Unit
    }

    if (rejectUnsupportedInputs(
            model = model,
            hasImages = images.isNotEmpty(),
            hasFiles = files.isNotEmpty(),
        )
    ) {
        return
    }

    scope.launch {
        val history = buildApiHistory()
        runCompletion(model, history)
    }
}

internal suspend fun ChatState.sendVoiceImpl(
    audioBytes: ByteArray,
    format: String,
    transcript: String?,
) {
    val model = selectedModel ?: return
    if (audioBytes.isEmpty()) return
    val spoken = transcript?.trim().orEmpty()
    if (spoken.isBlank()) return

    ensureCurrentChat()

    messages += ChatMessage(
        id = ++currentId,
        author = MessageAuthor.User,
        content = spoken,
        timestamp = System.currentTimeMillis(),
        isVoice = true,
    )
    persist()

    if (model.supportsSpeechOutput) {
        runSpeechReply(model, spoken)
        return
    }

    if (rejectUnsupportedInputs(model = model, hasAudio = true)) {
        return
    }

    val history = withSystemMessage(
        textHistory().dropLast(1) + ChatMessageDto(
            role = "user",
            content = spoken,
            audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP),
            audioFormat = format,
        ),
    )
    runCompletion(model, history)
}

internal suspend fun ChatState.transcribeRecordingImpl(
    audioBytes: ByteArray,
    format: String,
): String? {
    if (audioBytes.isEmpty()) return null
    val model = selectedModel
    val transcriptionModelId = when {
        model?.usesTranscriptionComposer == true -> model.id
        model?.supportsSpeechOutput == true && model.transcribesAudioNatively -> model.id
        else -> voiceStore.load().transcriptionModelId.ifBlank {
            VoicePreferences.DEFAULT_TRANSCRIPTION_MODEL
        }
    }
    return withContext(Dispatchers.IO) {
        try {
            api.transcribeAudio(
                apiKey = apiKey,
                request = TranscriptionRequest(
                    model = transcriptionModelId,
                    audioBase64 = Base64.encodeToString(audioBytes, Base64.NO_WRAP),
                    format = format,
                ),
            ).takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}

internal fun ChatState.sendTranscriptionVoiceImpl(
    audioBytes: ByteArray,
    format: String,
) {
    scope.launch {
        sendTranscriptionVoiceBody(audioBytes, format)
    }
}

private suspend fun ChatState.sendTranscriptionVoiceBody(
    audioBytes: ByteArray,
    format: String,
) {
    val model = selectedModel ?: return
    if (!model.usesTranscriptionComposer || audioBytes.isEmpty()) return

    ensureCurrentChat()

    val attachment = withContext(Dispatchers.IO) {
        AttachmentStore.ingestBytes(
            context = context,
            bytes = audioBytes,
            mimeType = "audio/mp4",
            displayName = "voice_${System.currentTimeMillis()}.$format",
        )
    } ?: return

    messages += ChatMessage(
        id = ++currentId,
        author = MessageAuthor.User,
        content = "",
        timestamp = System.currentTimeMillis(),
        isVoice = true,
        attachments = listOf(attachment),
    )
    persist(bumpRecency = true)
    bumpMessageListRevision()

    runTranscriptionCompletion(model, audioBytes, format)
}

internal fun ChatState.interruptInFlightImpl() {
    if (!isRunning) return

    currentChatId?.let { completions.cancelSilent(it) }
    activeCompletionSessionId = 0L
    val index = messages.indexOfLast {
        it.author == MessageAuthor.Assistant && it.isStreaming
    }
    if (index >= 0) settleInterruptedAssistant(index)
    isRunning = false
    persist()
}

private fun ChatState.settleInterruptedAssistant(index: Int) {
    val existing = messages[index]
    if (!existing.isStreaming) return
    val partial = existing.content
    val hasBody = partial.isNotBlank() ||
        existing.reasoning.isNotBlank() ||
        existing.attachments.isNotEmpty()
    if (hasBody) {
        val variants = existing.variants.ifEmpty { listOf(partial) }
        messages[index] = existing.copy(
            isStreaming = false,
            statusLabel = null,
            thinkingStartedAtElapsed = null,
            webSearchSteps = existing.webSearchSteps.map { it.copy(isSearching = false) },
            variants = variants,
            variantIndex = variants.lastIndex,
        )
        return
    }
    val saved = existing.savedVariants()
    if (saved.isNotEmpty()) {
        messages[index] = existing.copy(
            content = saved.last(),
            isStreaming = false,
            statusLabel = null,
            thinkingStartedAtElapsed = null,
            webSearchSteps = existing.webSearchSteps.map { it.copy(isSearching = false) },
            variantIndex = saved.lastIndex,
        )
        return
    }
    messages[index] = existing.asStopped()
}

internal fun ChatState.stopImpl() {
    val index = messages.indexOfLast {
        it.author == MessageAuthor.Assistant && it.isStreaming
    }
    if (index >= 0) {
        messages[index] = messages[index].asStopped()
    }
    activeCompletionSessionId = 0L
    currentChatId?.let { completions.stop(it) }
    isRunning = false
    persist()
}

internal fun ChatState.saveTailToVariantContinuation(assistantIndex: Int) {
    if (assistantIndex !in messages.indices) return
    val message = messages[assistantIndex]
    val currentTail = if (assistantIndex + 1 < messages.size) {
        messages.subList(assistantIndex + 1, messages.size).toList()
    } else {
        emptyList()
    }
    if (currentTail.isEmpty() && message.variantContinuations.isEmpty()) return
    val variantCount = message.savedVariants().size.coerceAtLeast(1)
    val continuations = message.ensureVariantContinuations(variantCount).toMutableList()
    continuations[message.variantIndex.coerceIn(0, variantCount - 1)] = currentTail
    messages[assistantIndex] = message.copy(variantContinuations = continuations)
}

internal fun ChatState.removeTailAfter(index: Int) {
    if (index + 1 < messages.size) {
        messages.removeRange(index + 1, messages.size)
        bumpMessageListRevision()
    }
}

internal fun ChatState.prepareVariantContinuationForNewVariant(assistantIndex: Int, priorVariantCount: Int) {
    if (assistantIndex !in messages.indices) return
    val message = messages[assistantIndex]
    val continuations = message.ensureVariantContinuations(priorVariantCount + 1).toMutableList()
    if (continuations.size <= priorVariantCount) {
        continuations.add(emptyList())
    }
    messages[assistantIndex] = message.copy(variantContinuations = continuations)
}

internal fun ChatState.regenerateImpl(messageId: Long) {
    if (isRunning) return
    val model = selectedModel ?: return
    val chatId = currentChatId ?: return
    val index = messages.indexOfFirst {
        it.id == messageId && it.author == MessageAuthor.Assistant
    }
    if (index < 0) return
    activeCompletionSessionId = 0L
    completions.cancelSilent(chatId)
    isRunning = false

    if (model.supportsSpeechOutput) {
        val prompt = userPromptBefore(index) ?: return
        val existing = messages[index]
        saveTailToVariantContinuation(index)
        removeTailAfter(index)
        val priorVariants = existing.savedVariants()
        prepareVariantContinuationForNewVariant(index, priorVariants.size)
        val savedContinuations = messages[index].variantContinuations
        messages[index] = existing.copy(
            content = "",
            attachments = emptyList(),
            isStreaming = true,
            variants = priorVariants,
            attachmentVariants = existing.savedAttachmentVariants(),
            variantIndex = priorVariants.size.coerceAtLeast(0),
            variantContinuations = savedContinuations,
            showVoiceAsTextFirst = snapshotShowVoiceAsTextFirst(),
            voiceAutoPlayed = false,
        )
        persist(bumpRecency = true)
        runSpeechReply(
            model = model,
            userText = prompt,
            assistantId = messageId,
            appendVariant = priorVariants.isNotEmpty(),
        )
        return
    }

    when (model.modelKind) {
        ModelKind.ImageGen -> {
            val prompt = userPromptBefore(index) ?: return
            saveTailToVariantContinuation(index)
            removeTailAfter(index)
            messages.removeAt(index)
            bumpMessageListRevision()
            persist(bumpRecency = true)
            runImageCompletion(model, prompt)
            return
        }
        ModelKind.VideoGen -> {
            val userTurn = userMessageBefore(index) ?: return
            val prompt = userPromptBefore(index).orEmpty()
            val images = userTurn.attachments.filter { it.isImage }
            if (prompt.isBlank() && images.isEmpty()) return
            saveTailToVariantContinuation(index)
            removeTailAfter(index)
            messages.removeAt(index)
            bumpMessageListRevision()
            persist(bumpRecency = true)
            runVideoCompletion(model, prompt, images)
            return
        }
        ModelKind.Transcription -> {
            val userTurn = userMessageBefore(index) ?: return
            val recording = loadVoiceRecordingFromMessage(userTurn) ?: return
            val (audioBytes, format) = recording
            val existing = messages[index]
            saveTailToVariantContinuation(index)
            removeTailAfter(index)
            val priorVariants = existing.savedVariants()
            prepareVariantContinuationForNewVariant(index, priorVariants.size)
            val savedContinuations = messages[index].variantContinuations
            messages[index] = existing.copy(
                content = "",
                attachments = emptyList(),
                isStreaming = true,
                statusLabel = "Transcribing…",
                variants = priorVariants,
                variantIndex = priorVariants.size.coerceAtLeast(0),
                variantContinuations = savedContinuations,
                thinkingStartedAtElapsed = null,
            )
            persist(bumpRecency = true)
            runTranscriptionCompletion(
                model = model,
                audioBytes = audioBytes,
                format = format,
                assistantId = messageId,
                appendVariant = priorVariants.isNotEmpty(),
            )
            return
        }
        else -> Unit
    }

    val existing = messages[index]
    saveTailToVariantContinuation(index)
    removeTailAfter(index)
    val prior = existing.savedVariants()
    prepareVariantContinuationForNewVariant(index, prior.size)
    val priorReasoning = if (existing.reasoningVariants.isNotEmpty()) {
        existing.reasoningVariants
    } else if (existing.reasoning.isNotBlank() && prior.isNotEmpty()) {
        listOf(existing.reasoning) + List((prior.size - 1).coerceAtLeast(0)) { "" }
    } else {
        existing.reasoningVariants
    }
    val priorThoughtDurations = if (existing.thoughtDurationVariants.isNotEmpty()) {
        existing.thoughtDurationVariants
    } else if (existing.thoughtDurationMs != null && prior.isNotEmpty()) {
        listOf(existing.thoughtDurationMs) + List((prior.size - 1).coerceAtLeast(0)) { null }
    } else {
        existing.thoughtDurationVariants
    }
    val priorWebSearch = if (existing.webSearchStepVariants.isNotEmpty()) {
        existing.webSearchStepVariants
    } else if (existing.webSearchSteps.isNotEmpty() && prior.isNotEmpty()) {
        listOf(existing.webSearchSteps) + List((prior.size - 1).coerceAtLeast(0)) { emptyList() }
    } else {
        existing.webSearchStepVariants
    }
    messages[index] = existing.copy(
        content = "",
        attachments = emptyList(),
        reasoning = "",
        thoughtDurationMs = null,
        thoughtDurationVariants = priorThoughtDurations,
        webSearchSteps = emptyList(),
        webSearchStepVariants = priorWebSearch,
        stoppedWhileThinking = false,
        expectStreamedThinking = snapshotExpectStreamedThinking(model),
        expectWebSearch = isSearchEnabled && model.supportsWebSearch,
        isStreaming = true,
        thinkingStartedAtElapsed = SystemClock.elapsedRealtime(),
        variants = prior,
        attachmentVariants = existing.savedAttachmentVariants(),
        reasoningVariants = priorReasoning,
        variantIndex = prior.size.coerceAtLeast(0),
        variantContinuations = messages[index].variantContinuations,
    )
    persist(bumpRecency = true)
    scope.launch {
        val history = buildApiHistory(beforeAssistantIndex = index)
        runCompletion(
            model = model,
            history = history,
            assistantId = messageId,
            appendVariant = prior.isNotEmpty(),
        )
    }
}

internal fun ChatState.selectVariantImpl(messageId: Long, delta: Int) {
    if (isRunning) return
    val index = messages.indexOfFirst { it.id == messageId }
    if (index < 0) return
    val message = messages[index]
    if (message.isStreaming) return
    val saved = message.savedVariants()
    if (saved.size <= 1) return
    val oldVariant = message.variantIndex
    val next = (oldVariant + delta).coerceIn(0, saved.lastIndex)
    if (next == oldVariant) return

    val currentTail = if (index + 1 < messages.size) {
        messages.subList(index + 1, messages.size).toList()
    } else {
        emptyList()
    }
    val continuations = message.ensureVariantContinuations(saved.size).toMutableList()
    continuations[oldVariant] = currentTail
    val restoredTail = continuations.getOrElse(next) { emptyList() }

    messages[index] = message
        .copy(variantContinuations = continuations)
        .withActiveVariantFields(next)

    if (index + 1 < messages.size) {
        messages.removeRange(index + 1, messages.size)
        bumpMessageListRevision()
    }
    if (restoredTail.isNotEmpty()) {
        messages.addAll(restoredTail)
        bumpMessageListRevision()
    }
    persist()
}

internal fun ChatState.appendUnsupportedModelWarning(model: OpenRouterModel, reason: String) {
    messages += ChatMessage(
        id = ++currentId,
        author = MessageAuthor.Assistant,
        content = "⚠ ${model.name} $reason",
        timestamp = System.currentTimeMillis(),
    )
    persist()
}

internal fun ChatState.rejectUnsupportedInputs(
    model: OpenRouterModel,
    hasImages: Boolean = false,
    hasAudio: Boolean = false,
    hasFiles: Boolean = false,
): Boolean {
    val unsupported = model.unsupportedInputs(hasImages, hasAudio, hasFiles)
    if (unsupported.isEmpty()) return false
    val content = if (unsupported.size == 1) {
        "⚠ ${model.name} ${unsupported.first().reason}"
    } else {
        val bullets = unsupported.joinToString("\n") { "• ${it.reason}" }
        "⚠ ${model.name} can't take everything you attached:\n$bullets"
    }
    messages += ChatMessage(
        id = ++currentId,
        author = MessageAuthor.Assistant,
        content = content,
        timestamp = System.currentTimeMillis(),
    )
    persist()
    return true
}

internal fun ChatState.loadVoiceRecordingFromMessage(message: ChatMessage): Pair<ByteArray, String>? {
    val attachment = message.attachments.firstOrNull {
        it.mimeType?.startsWith("audio/", ignoreCase = true) == true
    } ?: return null
    val file = File(attachment.path)
    if (!file.exists() || !file.isFile) return null
    val bytes = runCatching { file.readBytes() }.getOrNull()?.takeIf { it.isNotEmpty() }
        ?: return null
    val format = attachment.displayName.substringAfterLast('.', missingDelimiterValue = "")
        .ifBlank { attachment.path.substringAfterLast('.', missingDelimiterValue = "m4a") }
        .lowercase()
    return bytes to format
}

internal fun ChatState.userPromptBefore(index: Int): String? {
    return userMessageBefore(index)?.let { message ->
        message.userDisplayText().ifBlank { message.content }
    }
}

internal fun ChatState.userMessageBefore(index: Int): ChatMessage? {
    for (i in (index - 1) downTo 0) {
        val m = messages[i]
        if (m.author == MessageAuthor.User) return m
    }
    return null
}

internal fun ChatState.markVoiceAutoPlayedImpl(messageId: Long) {
    val index = messages.indexOfFirst { it.id == messageId }
    if (index < 0) return
    val existing = messages[index]
    if (existing.voiceAutoPlayed) return
    messages[index] = existing.copy(voiceAutoPlayed = true)
    persist()
}
