package tedwester.convo.features.chat.state

import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tedwester.convo.core.network.model.ImageRequest
import tedwester.convo.core.network.model.OpenRouterModel
import tedwester.convo.core.network.model.SpeechRequest
import tedwester.convo.core.network.model.TranscriptionRequest
import tedwester.convo.core.network.model.VideoFrameImage
import tedwester.convo.core.network.model.VideoRequest
import tedwester.convo.features.chat.data.AttachmentStore
import tedwester.convo.features.chat.data.SpecialGenerationOutcome
import tedwester.convo.features.chat.data.SpecialGenerationRequest
import tedwester.convo.features.chat.data.VoicePreferences
import tedwester.convo.features.chat.data.VoiceTtsMode
import tedwester.convo.features.chat.model.ChatAttachment
import tedwester.convo.features.chat.model.ChatMessage
import tedwester.convo.features.chat.model.EMPTY_RESPONSE_TEXT
import tedwester.convo.features.chat.model.MessageAuthor

/**
 * Speech-output reply for typed text, a transcribed voice note, or a redo.
 * Always honors the current [VoiceTtsMode]: Speak my words synthesizes
 * [userText]; Conversation generates a chat reply (with history) then
 * speaks that. Display defaults are snapshotted per turn from
 * [VoicePreferences.showVoiceRepliesAsTextFirst].
 */
internal fun ChatState.runSpeechReply(
    model: OpenRouterModel,
    userText: String,
    assistantId: Long? = null,
    appendVariant: Boolean = false,
) {
    val chatId = currentChatId ?: return
    if (userText.isBlank()) return
    val prefs = voiceStore.load()
    val conversation = prefs.mode == VoiceTtsMode.Conversation
    val showTextFirst = prefs.showVoiceRepliesAsTextFirst
    val id = assistantId ?: ++currentId
    val label = if (conversation) "Thinking of a reply…" else "Generating audio…"
    if (assistantId == null) {
        messages += ChatMessage(
            id = id,
            author = MessageAuthor.Assistant,
            content = "",
            timestamp = System.currentTimeMillis(),
            isStreaming = true,
            statusLabel = label,
            showVoiceAsTextFirst = showTextFirst,
        )
    } else {
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) {
            messages[index] = messages[index].copy(
                content = "",
                isStreaming = true,
                statusLabel = label,
                showVoiceAsTextFirst = showTextFirst,
                voiceAutoPlayed = false,
            )
        }
    }
    val snapshot = messages.toList()
    val assistantIndex = snapshot.indexOfFirst { it.id == id }
    persist()
    isRunning = true
    completions.viewingChatId = chatId
    activeCompletionSessionId = completions.startSpecial(
        SpecialGenerationRequest(
            chatId = chatId,
            model = model,
            messages = snapshot,
            assistantId = id,
            appendVariant = appendVariant,
            systemMessage = systemMessage,
        ),
    ) { updateStatus ->
        var ttsInput = ""
        try {
            ttsInput = if (conversation) {
                updateStatus("Thinking of a reply…")
                val replyModelId = if (model.usesIntegratedConversationReply) {
                    model.id
                } else {
                    prefs.replyModelId.ifBlank {
                        VoicePreferences.DEFAULT_REPLY_MODEL
                    }
                }
                val conversationSystem = VoicePreferences.buildConversationReplySystemMessage(
                    voiceModel = model,
                    replyModelId = replyModelId,
                    replyModel = api.findCachedModel(replyModelId),
                    voiceId = resolveTtsVoice(model),
                )
                api.chatCompletion(
                    apiKey = apiKey,
                    model = replyModelId,
                    messages = buildApiHistoryFrom(
                        snapshot,
                        assistantIndex.takeIf { it >= 0 },
                        additionalSystemMessage = conversationSystem,
                    ),
                    maxTokens = apiPreferencesStore.load().maxTokens,
                )
            } else {
                userText
            }.trim()

            updateStatus("Generating audio…")
            val voice = resolveTtsVoice(model)
            val result = withContext(Dispatchers.IO) {
                api.createSpeech(
                    apiKey = apiKey,
                    request = SpeechRequest(
                        model = model.id,
                        input = ttsInput,
                        voice = voice,
                        responseFormat = "mp3",
                    ),
                )
            }
            val mime = audioMimeFor(result.contentType)
            val attachment = withContext(Dispatchers.IO) {
                AttachmentStore.ingestBytes(
                    context = context,
                    bytes = result.audioBytes,
                    mimeType = mime,
                    displayName = "voice_${System.currentTimeMillis()}.${extForMime(mime)}",
                )
            }
            if (attachment == null) {
                SpecialGenerationOutcome(EMPTY_RESPONSE_TEXT)
            } else {
                SpecialGenerationOutcome(content = ttsInput, attachments = listOf(attachment))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = "⚠ ${e.message?.takeIf { it.isNotBlank() } ?: "Voice generation failed"}"
            val content = ttsInput.takeIf { it.isNotBlank() }?.let { "$it\n\n$error" } ?: error
            SpecialGenerationOutcome(content = content, success = false)
        }
    }
}

/**
 * Transcribe a voice note via OpenRouter's STT endpoint for a
 * transcription-only model. Uses the same special-generation pipeline as
 * image / video / TTS so the assistant turn shows a live status label,
 * survives navigation, and supports stop.
 */
internal fun ChatState.runTranscriptionCompletion(
    model: OpenRouterModel,
    audioBytes: ByteArray,
    format: String,
    assistantId: Long? = null,
    appendVariant: Boolean = false,
) {
    val chatId = currentChatId ?: return
    if (audioBytes.isEmpty()) return
    val id = assistantId ?: ++currentId
    if (assistantId == null) {
        messages += ChatMessage(
            id = id,
            author = MessageAuthor.Assistant,
            content = "",
            timestamp = System.currentTimeMillis(),
            isStreaming = true,
            statusLabel = "Transcribing…",
        )
    } else {
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) {
            messages[index] = messages[index].copy(
                isStreaming = true,
                statusLabel = "Transcribing…",
            )
        }
    }
    val snapshot = messages.toList()
    persist(bumpRecency = true)
    isRunning = true
    completions.viewingChatId = chatId
    val encodedAudio = Base64.encodeToString(audioBytes, Base64.NO_WRAP)
    activeCompletionSessionId = completions.startSpecial(
        SpecialGenerationRequest(
            chatId = chatId,
            model = model,
            messages = snapshot,
            assistantId = id,
            appendVariant = appendVariant,
            systemMessage = systemMessage,
        ),
    ) { updateStatus ->
        updateStatus("Transcribing…")
        try {
            val transcript = withContext(Dispatchers.IO) {
                api.transcribeAudio(
                    apiKey = apiKey,
                    request = TranscriptionRequest(
                        model = model.id,
                        audioBase64 = encodedAudio,
                        format = format,
                    ),
                ).trim().takeIf { it.isNotBlank() }
            }
            if (transcript == null) {
                SpecialGenerationOutcome(
                    content = "⚠ Couldn't transcribe audio.",
                    success = false,
                )
            } else {
                SpecialGenerationOutcome(content = transcript)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SpecialGenerationOutcome(
                content = "⚠ ${e.message?.takeIf { it.isNotBlank() } ?: "Transcription failed"}",
                success = false,
            )
        }
    }
}

/**
 * Generate images via OpenRouter's `/api/v1/images` endpoint for an
 * image-generation model. Each returned image is saved as an attachment.
 */
internal fun ChatState.runImageCompletion(model: OpenRouterModel, prompt: String) {
    val chatId = currentChatId ?: return
    if (prompt.isBlank()) return
    val assistantId = ++currentId
    messages += ChatMessage(
        id = assistantId,
        author = MessageAuthor.Assistant,
        content = "",
        timestamp = System.currentTimeMillis(),
        isStreaming = true,
        statusLabel = "Generating image…",
    )
    persist()
    isRunning = true
    completions.viewingChatId = chatId
    activeCompletionSessionId = completions.startSpecial(
        SpecialGenerationRequest(
            chatId = chatId,
            model = model,
            messages = messages.toList(),
            assistantId = assistantId,
            appendVariant = false,
            systemMessage = systemMessage,
        ),
    ) {
        try {
            val result = withContext(Dispatchers.IO) {
                api.createImage(
                    apiKey = apiKey,
                    request = ImageRequest(model = model.id, prompt = prompt),
                )
            }
            val stamp = System.currentTimeMillis()
            val attachments = result.images.mapIndexedNotNull { i, img ->
                AttachmentStore.ingestBytes(
                    context = context,
                    bytes = img.bytes,
                    mimeType = img.mediaType,
                    displayName = "image_${stamp}_$i.${extForMime(img.mediaType)}",
                )
            }
            if (attachments.isEmpty()) {
                SpecialGenerationOutcome(EMPTY_RESPONSE_TEXT)
            } else {
                SpecialGenerationOutcome(content = "", attachments = attachments)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SpecialGenerationOutcome(
                content = "⚠ ${e.message ?: "Image generation failed"}",
                success = false,
            )
        }
    }
}

/**
 * Generate video via OpenRouter's async `/api/v1/videos` endpoint. Attached
 * images become first/last frames (and extra style references). The returned
 * clip is saved as an attachment.
 */
internal fun ChatState.runVideoCompletion(
    model: OpenRouterModel,
    prompt: String,
    referenceImages: List<ChatAttachment> = emptyList(),
) {
    val chatId = currentChatId ?: return
    val trimmed = prompt.trim()
    if (trimmed.isBlank() && referenceImages.isEmpty()) return
    val assistantId = ++currentId
    messages += ChatMessage(
        id = assistantId,
        author = MessageAuthor.Assistant,
        content = "",
        timestamp = System.currentTimeMillis(),
        isStreaming = true,
        statusLabel = "Generating video…",
    )
    persist()
    isRunning = true
    completions.viewingChatId = chatId
    activeCompletionSessionId = completions.startSpecial(
        SpecialGenerationRequest(
            chatId = chatId,
            model = model,
            messages = messages.toList(),
            assistantId = assistantId,
            appendVariant = false,
            systemMessage = systemMessage,
        ),
    ) {
        try {
            val dataUrls = withContext(Dispatchers.IO) {
                referenceImages.mapNotNull { encodeImageDataUrl(it) }
            }
            val frameImages = buildList {
                dataUrls.getOrNull(0)?.let {
                    add(VideoFrameImage(dataUrl = it, frameType = "first_frame"))
                }
                dataUrls.getOrNull(1)?.let {
                    add(VideoFrameImage(dataUrl = it, frameType = "last_frame"))
                }
            }
            val result = api.createVideo(
                apiKey = apiKey,
                request = VideoRequest(
                    model = model.id,
                    prompt = trimmed.takeIf { it.isNotBlank() },
                    frameImages = frameImages,
                    inputReferences = dataUrls.drop(2),
                ),
            )
            val stamp = System.currentTimeMillis()
            val attachments = result.videos.mapIndexedNotNull { i, video ->
                AttachmentStore.ingestBytes(
                    context = context,
                    bytes = video.bytes,
                    mimeType = video.mediaType,
                    displayName = "video_${stamp}_$i.${extForMime(video.mediaType)}",
                )
            }
            if (attachments.isEmpty()) {
                SpecialGenerationOutcome(EMPTY_RESPONSE_TEXT)
            } else {
                SpecialGenerationOutcome(content = "", attachments = attachments)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SpecialGenerationOutcome(
                content = "⚠ ${e.message ?: "Video generation failed"}",
                success = false,
            )
        }
    }
}

internal fun ChatState.audioMimeFor(contentType: String?): String {
    if (contentType.isNullOrBlank()) return "audio/mpeg"
    return when {
        contentType.contains("mpeg", ignoreCase = true) -> "audio/mpeg"
        contentType.contains("wav", ignoreCase = true) -> "audio/wav"
        contentType.contains("ogg", ignoreCase = true) -> "audio/ogg"
        contentType.contains("pcm", ignoreCase = true) -> "audio/pcm"
        else -> "audio/mpeg"
    }
}

internal fun ChatState.extForMime(mimeType: String): String = when {
    mimeType.contains("png", ignoreCase = true) -> "png"
    mimeType.contains("jpeg", ignoreCase = true) ||
        mimeType.contains("jpg", ignoreCase = true) -> "jpg"
    mimeType.contains("webp", ignoreCase = true) -> "webp"
    mimeType.contains("svg", ignoreCase = true) -> "svg"
    mimeType.contains("mpeg", ignoreCase = true) ||
        mimeType.contains("mp3", ignoreCase = true) -> "mp3"
    mimeType.contains("wav", ignoreCase = true) -> "wav"
    mimeType.contains("ogg", ignoreCase = true) -> "ogg"
    mimeType.contains("pcm", ignoreCase = true) -> "pcm"
    mimeType.contains("mp4", ignoreCase = true) -> "mp4"
    mimeType.contains("webm", ignoreCase = true) -> "webm"
    mimeType.contains("quicktime", ignoreCase = true) ||
        mimeType.contains("mov", ignoreCase = true) -> "mov"
    else -> "bin"
}
