package tedwester.convo.core.network.model

import tedwester.convo.features.chat.model.ReasoningEffort

data class OpenRouterModel(
    val id: String,
    val name: String,
    val contextLength: Int? = null,
    val pricing: ModelPricing? = null,
    val description: String? = null,
    val inputModalities: List<String> = emptyList(),
    val outputModalities: List<String> = emptyList(),
    val supportedParameters: List<String> = emptyList(),
    val reasoningConfig: ModelReasoningConfig? = null,
    val supportedVoices: List<String> = emptyList(),
    val created: Long? = null,
) {
    val authorSlug: String
        get() = id.trimStart('~').substringBefore('/').ifBlank { id }

    val authorLabel: String
        get() = AUTHOR_LABELS[authorSlug] ?: authorSlug.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }

    val supportsAudioInput: Boolean
        get() = inputModalities.isEmpty() ||
            inputModalities.any { it.equals("audio", ignoreCase = true) }

    val supportsImageInput: Boolean
        get() = inputModalities.isEmpty() ||
            inputModalities.any { it.equals("image", ignoreCase = true) }

    val hasImageInput: Boolean
        get() = inputModalities.any { it.equals("image", ignoreCase = true) }

    val hasAudioInput: Boolean
        get() = inputModalities.any { it.equals("audio", ignoreCase = true) }

    val hasFileInput: Boolean
        get() = inputModalities.any { it.equals("file", ignoreCase = true) }

    val supportsComposerAttachments: Boolean
        get() = inputModalities.isEmpty() || hasImageInput || hasFileInput

    val supportsImageAttachments: Boolean
        get() = inputModalities.isEmpty() || hasImageInput

    val supportsFileAttachments: Boolean
        get() = inputModalities.isEmpty() || hasFileInput

    val supportsImageOutput: Boolean
        get() = outputModalities.any { it.equals("image", ignoreCase = true) }

    val supportsVideoOutput: Boolean
        get() = outputModalities.any { it.equals("video", ignoreCase = true) }

    val hasTextOutput: Boolean
        get() = outputModalities.any { it.equals("text", ignoreCase = true) }

    val hasTranscriptionOutput: Boolean
        get() = outputModalities.any { it.equals("transcription", ignoreCase = true) }

    val supportsSpeechOutput: Boolean
        get() = outputModalities.any {
            it.equals("speech", ignoreCase = true) || it.equals("audio", ignoreCase = true)
        }

    val isSpeechModel: Boolean
        get() = supportsSpeechOutput ||
            supportsVoiceParameter ||
            supportedVoices.isNotEmpty()

    val supportsVoiceComposer: Boolean
        get() = supportsConversationOrb || usesTranscriptionComposer

    val supportsConversationOrb: Boolean
        get() = supportsSpeechOutput && !hasTranscriptionOutput && !isTranscriptionModel

    val usesTranscriptionComposer: Boolean
        get() = modelKind == ModelKind.Transcription

    val isNativeAudioConversationModel: Boolean
        get() = supportsSpeechOutput && hasAudioInput

    val usesIntegratedConversationReply: Boolean
        get() = isNativeAudioConversationModel && hasTextOutput

    val transcribesAudioNatively: Boolean
        get() = hasTranscriptionOutput

    val supportsVoiceParameter: Boolean
        get() = supportedParameters.any { it.equals("voice", ignoreCase = true) }

    val defaultVoiceId: String?
        get() = supportedVoices.firstOrNull()

    val supportsVoiceSelection: Boolean
        get() = isSpeechModel &&
            (supportsVoiceParameter || supportedVoices.isNotEmpty())

    fun resolveVoice(selected: String?): String? {
        if (supportedVoices.isEmpty()) {
            return selected?.takeIf { it.isNotBlank() }
        }
        return selected?.takeIf { it in supportedVoices } ?: supportedVoices.first()
    }

    fun voiceDisplayLabel(selected: String? = null): String {
        val id = resolveVoice(selected)
        return id?.let { formatVoiceLabel(it) } ?: "Default"
    }

    val isImageGenerationModel: Boolean
        get() = supportsImageOutput && !supportsVideoOutput

    val isVideoGenerationModel: Boolean
        get() = if (outputModalities.isEmpty()) looksLikeVideoModel(id) else supportsVideoOutput

    val isEmbeddingModel: Boolean
        get() = outputModalities.any { it.equals("embeddings", ignoreCase = true) }

    val isRerankModel: Boolean
        get() = outputModalities.any { it.equals("rerank", ignoreCase = true) }

    val isTranscriptionModel: Boolean
        get() = outputModalities.any { it.equals("transcription", ignoreCase = true) } &&
            !outputModalities.any { it.equals("text", ignoreCase = true) }

    val modelKind: ModelKind
        get() = when {
            isTranscriptionModel -> ModelKind.Transcription
            hasTranscriptionOutput && !supportsSpeechOutput -> ModelKind.Transcription
            supportsSpeechOutput && !hasTextOutput -> ModelKind.Tts
            isVideoGenerationModel -> ModelKind.VideoGen
            isImageGenerationModel -> ModelKind.ImageGen
            isEmbeddingModel -> ModelKind.Embedding
            isRerankModel -> ModelKind.Rerank
            else -> ModelKind.Chat
        }

    val isChatCapable: Boolean
        get() = modelKind == ModelKind.Chat

    val supportsReasoning: Boolean
        get() = reasoningConfig != null || supportedParameters.any {
            it.equals("reasoning", ignoreCase = true) ||
                it.equals("include_reasoning", ignoreCase = true) ||
                it.equals("reasoning_effort", ignoreCase = true)
        }

    val supportsWebSearch: Boolean
        get() = isChatCapable && supportedParameters.any {
            it.equals("tools", ignoreCase = true)
        }

    val requiresMandatoryReasoning: Boolean
        get() {
            if (!supportsReasoning) return false
            if (reasoningConfig != null) return reasoningConfig.mandatory
            val slug = id.lowercase()
            return slug.contains(":thinking") ||
                slug.contains("/o1") ||
                slug.contains("/o3") ||
                slug.contains("/o4") ||
                slug.contains("deepseek-r1") ||
                slug.contains("deepseek-reasoner") ||
                slug.contains("qwq")
        }

    val canDisableReasoning: Boolean
        get() = supportsReasoning && !requiresMandatoryReasoning

    fun supportedEffortLevels(): List<ReasoningEffort> {
        if (!supportsReasoning) return emptyList()
        reasoningConfig?.supportedEfforts?.let { return it }
        if (reasoningConfig != null) {
            return ReasoningEffort.entries.toList()
        }
        val all = listOf(
            ReasoningEffort.Minimal,
            ReasoningEffort.Low,
            ReasoningEffort.Medium,
            ReasoningEffort.High,
            ReasoningEffort.XHigh,
        )
        val slug = id.lowercase()
        val isOpenAiOSeries = slug.contains("/o1") ||
            slug.contains("/o3") ||
            slug.contains("/o4")
        return if (isOpenAiOSeries) {
            all + ReasoningEffort.Max
        } else {
            all
        }
    }

    fun isEffortSupported(effort: ReasoningEffort): Boolean {
        if (!supportsReasoning) return false
        val allowed = reasoningConfig?.supportedEfforts
        return when {
            allowed != null -> effort in allowed
            reasoningConfig != null -> true
            else -> effort in supportedEffortLevels()
        }
    }

    val promptPricePerMillion: Double?
        get() = pricing?.prompt?.toDoubleOrNull()?.times(1_000_000.0)

    val isFree: Boolean
        get() {
            if (id.contains(":free", ignoreCase = true)) return true
            val prompt = pricing?.prompt?.toDoubleOrNull()
            val completion = pricing?.completion?.toDoubleOrNull()
            if (prompt == null && completion == null) return false
            return (prompt ?: 0.0) == 0.0 && (completion ?: 0.0) == 0.0
        }

    fun matchesSearch(query: String): Boolean {
        if (query.isBlank()) return true
        return name.contains(query, ignoreCase = true) ||
            id.contains(query, ignoreCase = true) ||
            authorLabel.contains(query, ignoreCase = true)
    }

    fun unsupportedInputs(
        hasImages: Boolean = false,
        hasAudio: Boolean = false,
        hasFiles: Boolean = false,
    ): List<UnsupportedInput> {
        if (inputModalities.isEmpty()) return emptyList()
        val result = mutableListOf<UnsupportedInput>()
        if (hasImages && !hasImageInput) result += UnsupportedInput.Image
        if (hasAudio && !hasAudioInput) result += UnsupportedInput.Audio
        if (hasFiles && !hasFileInput) result += UnsupportedInput.File
        return result
    }

    companion object {
        fun formatVoiceLabel(voiceId: String): String =
            voiceId
                .replace('_', ' ')
                .replace('-', ' ')
                .split(' ')
                .filter { it.isNotBlank() }
                .joinToString(" ") { word ->
                    word.lowercase().replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase() else it.toString()
                    }
                }

        private fun looksLikeVideoModel(id: String): Boolean {
            val slug = id.lowercase()
            return slug.contains("/veo") ||
                slug.contains("/sora") ||
                slug.contains("seedance") ||
                slug.contains("kling") ||
                slug.contains("runway") ||
                slug.contains("luma-dream") ||
                slug.contains("grok-imagine-video") ||
                slug.contains("minimax") && slug.contains("video") ||
                slug.contains("/wan") && (
                    slug.contains("video") ||
                        slug.contains("t2v") ||
                        slug.contains("i2v")
                    )
        }

        private val AUTHOR_LABELS = mapOf(
            "openai" to "OpenAI",
            "anthropic" to "Anthropic",
            "google" to "Google",
            "meta-llama" to "Meta",
            "deepseek" to "DeepSeek",
            "qwen" to "Qwen",
            "mistralai" to "Mistral",
            "x-ai" to "xAI",
            "cohere" to "Cohere",
            "amazon" to "Amazon",
            "perplexity" to "Perplexity",
            "nvidia" to "NVIDIA",
            "microsoft" to "Microsoft",
            "inflection" to "Inflection",
            "ai21" to "AI21",
        )
    }
}

data class ModelReasoningConfig(
    val supportedEfforts: List<ReasoningEffort>? = null,
    val defaultEffort: ReasoningEffort? = null,
    val defaultEnabled: Boolean? = null,
    val mandatory: Boolean = false,
    val supportsMaxTokens: Boolean = false,
)

data class ModelPricing(
    val prompt: String? = null,
    val completion: String? = null,
)

data class SpeechRequest(
    val model: String,
    val input: String,
    val voice: String? = null,
    val responseFormat: String? = null,
    val speed: Float? = null,
)

data class SpeechResult(
    val audioBytes: ByteArray,
    val contentType: String?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpeechResult) return false
        return contentType == other.contentType &&
            audioBytes.contentEquals(other.audioBytes)
    }

    override fun hashCode(): Int {
        var result = audioBytes.contentHashCode()
        result = 31 * result + (contentType?.hashCode() ?: 0)
        return result
    }
}

data class ImageRequest(
    val model: String,
    val prompt: String,
    val n: Int = 1,
    val resolution: String? = null,
    val aspectRatio: String? = null,
    val outputFormat: String? = null,
)

data class GeneratedImage(
    val bytes: ByteArray,
    val mediaType: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GeneratedImage) return false
        return mediaType == other.mediaType && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mediaType.hashCode()
        return result
    }
}

data class ImageResult(
    val images: List<GeneratedImage>,
)

data class VideoRequest(
    val model: String,
    val prompt: String? = null,
    val frameImages: List<VideoFrameImage> = emptyList(),
    val inputReferences: List<String> = emptyList(),
)

data class VideoFrameImage(
    val dataUrl: String,
    val frameType: String,
)

data class GeneratedVideo(
    val bytes: ByteArray,
    val mediaType: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GeneratedVideo) return false
        return mediaType == other.mediaType && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mediaType.hashCode()
        return result
    }
}

data class VideoResult(
    val videos: List<GeneratedVideo>,
)

data class TranscriptionRequest(
    val model: String,

    val audioBase64: String,

    val format: String,

    val language: String? = null,
)

data class ChatMessageDto(

    val role: String,
    val content: String,

    val audioBase64: String? = null,

    val audioFormat: String? = null,

    val imageDataUrls: List<String> = emptyList(),
)

data class OpenRouterKeyInfo(
    val label: String? = null,

    val usage: Double = 0.0,

    val limit: Double? = null,

    val limitRemaining: Double? = null,

    val totalCredits: Double? = null,

    val totalUsage: Double? = null,
) {

    val accountCreditsRemaining: Double?
        get() {
            val purchased = totalCredits ?: return null
            val used = totalUsage ?: return null
            return purchased - used
        }

    val creditsRemaining: Double?
        get() = accountCreditsRemaining ?: limitRemaining
}

enum class UnsupportedInput(val reason: String) {
    Image("doesn't support image input. Pick a vision-capable model on OpenRouter."),
    Audio("doesn't support voice input. Pick an audio-capable model on OpenRouter."),
    File("doesn't support file input. Pick a file-capable model on OpenRouter."),
}

enum class ModelKind {

    Chat,

    Tts,

    ImageGen,

    VideoGen,

    Embedding,

    Rerank,

    Transcription,
}
