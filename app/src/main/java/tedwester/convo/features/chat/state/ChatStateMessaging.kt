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
import tedwester.convo.core.network.model.UnsupportedInput
import tedwester.convo.features.chat.data.AttachmentStore
import tedwester.convo.features.chat.data.VoicePreferences
import tedwester.convo.features.chat.model.ChatMessage
import tedwester.convo.features.chat.model.MessageAuthor
import tedwester.convo.features.chat.model.asStopped
import java.io.File

internal fun ChatState.sendImpl() {
    val model = selectedModel ?: return
    val text = input.trim()
    val attachments = pendingAttachments.toList()
    if (text.isEmpty() && attachments.isEmpty()) return

    scope.launch {
        ensureCurrentChat()

        val images = attachments.filter { it.isImage }
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

        if (model.supportsSpeechOutput) {
            runSpeechReply(model, text.ifBlank { displayContent })
            return@launch
        }

        when (model.modelKind) {
            ModelKind.ImageGen -> {
                runImageCompletion(model, text.ifBlank { displayContent })
                return@launch
            }
            ModelKind.VideoGen -> {
                runVideoCompletion(model, text, images)
                return@launch
            }
            ModelKind.Embedding, ModelKind.Rerank -> {
                appendUnsupportedModelWarning(
                    model,
                    "isn't a chat model — it can't hold a conversation.",
                )
                return@launch
            }
            ModelKind.Transcription -> {
                appendUnsupportedModelWarning(
                    model,
                    "only transcribes audio. Send a voice message to use it.",
                )
                return@launch
            }
            ModelKind.Chat, ModelKind.Tts -> Unit
        }

        if (rejectUnsupportedInputs(
                model = model,
                hasImages = images.isNotEmpty(),
                hasFiles = files.isNotEmpty(),
            )
        ) {
            return@launch
        }

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

/**
 * Transcription-only model flow: show the user's recording as a voice bubble,
 * then stream the transcription through the special-generation pipeline.
 */
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

/**
 * Cancel an in-flight assistant turn when a live voice conversation ends.
 * Keeps any partial reply; settles an empty streaming placeholder as a
 * stopped turn so the user sees the usual "You stopped the response." message.
 */
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

/**
 * Redo an assistant turn: drop every message after its user prompt, keep
 * prior variants on this turn, and stream a new response.
 */
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
        if (index + 1 < messages.size) {
            messages.removeRange(index + 1, messages.size)
            bumpMessageListRevision()
        }
        val priorVariants = existing.savedVariants()
        messages[index] = existing.copy(
            content = "",
            attachments = emptyList(),
            isStreaming = true,
            variants = priorVariants,
            attachmentVariants = existing.savedAttachmentVariants(),
            variantIndex = priorVariants.size.coerceAtLeast(0),
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
            if (index + 1 < messages.size) messages.removeRange(index + 1, messages.size)
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
            if (index + 1 < messages.size) messages.removeRange(index + 1, messages.size)
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
            if (index + 1 < messages.size) {
                messages.removeRange(index + 1, messages.size)
                bumpMessageListRevision()
            }
            val priorVariants = existing.savedVariants()
            messages[index] = existing.copy(
                content = "",
                attachments = emptyList(),
                isStreaming = true,
                statusLabel = "Transcribing…",
                variants = priorVariants,
                variantIndex = priorVariants.size.coerceAtLeast(0),
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
    if (index + 1 < messages.size) {
        messages.removeRange(index + 1, messages.size)
        bumpMessageListRevision()
    }
    val prior = existing.savedVariants()
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
    val next = (message.variantIndex + delta).coerceIn(0, saved.lastIndex)
    if (next == message.variantIndex) return
    val reasoningForVariant = message.reasoningVariants.getOrNull(next)
        ?: message.reasoning.takeIf { next == message.variantIndex }
        ?: ""
    val thoughtDurationForVariant = message.thoughtDurationVariants.getOrNull(next)
        ?: message.thoughtDurationMs.takeIf { next == message.variantIndex }
    val webSearchForVariant = message.webSearchStepVariants.getOrNull(next)
        ?: message.webSearchSteps.takeIf { next == message.variantIndex }
        ?: emptyList()
    val attachmentsForVariant = message.savedAttachmentVariants().getOrNull(next)
        ?: message.attachments.takeIf { next == message.variantIndex }
        ?: emptyList()
    messages[index] = message.copy(
        content = saved[next],
        attachments = attachmentsForVariant,
        reasoning = reasoningForVariant,
        thoughtDurationMs = thoughtDurationForVariant,
        webSearchSteps = webSearchForVariant,
        variantIndex = next,
        variants = message.variants.ifEmpty { saved },
        attachmentVariants = message.attachmentVariants.ifEmpty {
            message.savedAttachmentVariants()
        },
    )
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
        message.content.lineSequence()
            .filterNot { it.trimStart().startsWith("📎") }
            .joinToString("\n")
            .trim()
            .ifBlank { message.content }
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
