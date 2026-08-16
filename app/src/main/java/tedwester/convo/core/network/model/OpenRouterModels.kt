package tedwester.convo.core.network.model

import tedwester.convo.features.chat.model.ReasoningEffort

/**
 * Data models representing OpenRouter API responses.
 *
 * These are UI-agnostic DTOs used by the network layer. The fields map to the
 * JSON returned by the OpenRouter `/api/v1/models` and `/api/v1/chat/completions`
 * endpoints. Only the fields the app currently consumes are modelled.
 */

/**
 * A single model available on OpenRouter.
 */
data class OpenRouterModel(
    /** Canonical model id, e.g. `meta-llama/llama-3.3-70b-instruct`. */
    val id: String,
    /** Human friendly name, e.g. `Llama 3.3 70B Instruct`. */
    val name: String,
    /** Maximum context window in tokens, when provided by the API. */
    val contextLength: Int? = null,
    /** Pricing information (USD per token), when provided. */
    val pricing: ModelPricing? = null,
    /** Short description of the model, when provided. */
    val description: String? = null,
    /**
     * Input modalities reported by OpenRouter (e.g. `text`, `image`, `audio`).
     * Empty when unknown (e.g. restored from local storage).
     */
    val inputModalities: List<String> = emptyList(),
    /**
     * Output modalities reported by OpenRouter (e.g. `text`, `image`, `audio`,
     * `speech`, `embeddings`, `video`). Empty when unknown (e.g. restored from
     * local storage).
     */
    val outputModalities: List<String> = emptyList(),
    /** API parameters this model accepts (e.g. `tools`, `reasoning`). */
    val supportedParameters: List<String> = emptyList(),
    /** Per-model reasoning capabilities from OpenRouter, when provided. */
    val reasoningConfig: ModelReasoningConfig? = null,
    /**
     * Voice ids supported by a TTS (speech-output) model, when reported by
     * OpenRouter via the model's `supported_voices` field. Empty otherwise.
     */
    val supportedVoices: List<String> = emptyList(),
    /** Unix timestamp the model was created (OpenRouter `created`), for sorting. */
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

    /** Permissive: unknown modalities are treated as allowed (local/restored models). */
    val supportsImageInput: Boolean
        get() = inputModalities.isEmpty() ||
            inputModalities.any { it.equals("image", ignoreCase = true) }

    /** Strict: API reported image input (for filter badges / meta). */
    val hasImageInput: Boolean
        get() = inputModalities.any { it.equals("image", ignoreCase = true) }

    /** Strict: API reported audio input (for filter badges). */
    val hasAudioInput: Boolean
        get() = inputModalities.any { it.equals("audio", ignoreCase = true) }

    /** Strict: API reported file input (for filter badges). */
    val hasFileInput: Boolean
        get() = inputModalities.any { it.equals("file", ignoreCase = true) }

    /**
     * Composer + button: true when modalities are unknown, or the model
     * accepts images and/or files.
     */
    val supportsComposerAttachments: Boolean
        get() = inputModalities.isEmpty() || hasImageInput || hasFileInput

    val supportsImageAttachments: Boolean
        get() = inputModalities.isEmpty() || hasImageInput

    val supportsFileAttachments: Boolean
        get() = inputModalities.isEmpty() || hasFileInput

    /** Model can emit images (OpenRouter `architecture.output_modalities`). */
    val supportsImageOutput: Boolean
        get() = outputModalities.any { it.equals("image", ignoreCase = true) }

    /** Model can emit video (OpenRouter `architecture.output_modalities`). */
    val supportsVideoOutput: Boolean
        get() = outputModalities.any { it.equals("video", ignoreCase = true) }

    /** Model can emit text (OpenRouter `architecture.output_modalities`). */
    val hasTextOutput: Boolean
        get() = outputModalities.any { it.equals("text", ignoreCase = true) }

    /** Model can emit transcriptions (raw `transcription` output modality). */
    val hasTranscriptionOutput: Boolean
        get() = outputModalities.any { it.equals("transcription", ignoreCase = true) }

    /** Model produces speech/audio output (`speech` or `audio` modality). */
    val supportsSpeechOutput: Boolean
        get() = outputModalities.any {
            it.equals("speech", ignoreCase = true) || it.equals("audio", ignoreCase = true)
        }

    /** TTS / speech-output model, including when output modalities aren't loaded yet. */
    val isSpeechModel: Boolean
        get() = supportsSpeechOutput ||
            supportsVoiceParameter ||
            supportedVoices.isNotEmpty()

    /**
     * Empty composer should offer the waveform mic for speech-output models
     * (live conversation) and transcription-only models (tap-to-record).
     */
    val supportsVoiceComposer: Boolean
        get() = supportsConversationOrb || usesTranscriptionComposer

    /**
     * Live voice conversation (cloud orb, auto-reopen mic). Speech-output models
     * only — never transcription/STT models.
     */
    val supportsConversationOrb: Boolean
        get() = supportsSpeechOutput && !hasTranscriptionOutput && !isTranscriptionModel

    /**
     * Composer tap-to-record STT flow (mic only, no conversation orb).
     */
    val usesTranscriptionComposer: Boolean
        get() = modelKind == ModelKind.Transcription

    /**
     * Speech model that accepts audio directly (audio-in → speech-out). When it
     * also emits text, conversation mode can reply on this model instead of a
     * separate [VoicePreferences.replyModelId].
     */
    val isNativeAudioConversationModel: Boolean
        get() = supportsSpeechOutput && hasAudioInput

    val usesIntegratedConversationReply: Boolean
        get() = isNativeAudioConversationModel && hasTextOutput

    /** This model can run OpenRouter STT on its own — skip the settings STT model. */
    val transcribesAudioNatively: Boolean
        get() = hasTranscriptionOutput

    /** Whether the speech API accepts a `voice` parameter for this model. */
    val supportsVoiceParameter: Boolean
        get() = supportedParameters.any { it.equals("voice", ignoreCase = true) }

    /** Default provider voice id when OpenRouter lists supported voices. */
    val defaultVoiceId: String?
        get() = supportedVoices.firstOrNull()

    /** True when the composer should expose a voice picker for TTS requests. */
    val supportsVoiceSelection: Boolean
        get() = isSpeechModel &&
            (supportsVoiceParameter || supportedVoices.isNotEmpty())

    /** Resolve a user-selected voice id to a value safe to send to the API. */
    fun resolveVoice(selected: String?): String? {
        if (supportedVoices.isEmpty()) {
            return selected?.takeIf { it.isNotBlank() }
        }
        return selected?.takeIf { it in supportedVoices } ?: supportedVoices.first()
    }

    /** Human-readable label for the voice that would be used for [selected]. */
    fun voiceDisplayLabel(selected: String? = null): String {
        val id = resolveVoice(selected)
        return id?.let { formatVoiceLabel(it) } ?: "Default"
    }

    /**
     * Image-generation model: produces image output. This includes image-editing
     * models that also accept reference images (e.g. `x-ai/grok-imagine-image`),
     * which OpenRouter reports with both `image` input and `image` output — the
     * dedicated `POST /api/v1/images` endpoint handles both generation and edits.
     */
    val isImageGenerationModel: Boolean
        get() = supportsImageOutput && !supportsVideoOutput

    /**
     * Video-generation model: produces video output via OpenRouter's async
     * `POST /api/v1/videos` endpoint (text-to-video and image-to-video).
     * When modalities are unknown (local/restored models), falls back to id
     * heuristics so Veo / Sora / Wan / Seedance still route correctly.
     */
    val isVideoGenerationModel: Boolean
        get() = if (outputModalities.isEmpty()) looksLikeVideoModel(id) else supportsVideoOutput

    /** Embedding model: produces embedding vectors. Not offered in the picker. */
    val isEmbeddingModel: Boolean
        get() = outputModalities.any { it.equals("embeddings", ignoreCase = true) }

    /** Reranker model. Not offered in the picker. */
    val isRerankModel: Boolean
        get() = outputModalities.any { it.equals("rerank", ignoreCase = true) }

    /** Transcription-only model: audio input, no chat text output. */
    val isTranscriptionModel: Boolean
        get() = outputModalities.any { it.equals("transcription", ignoreCase = true) } &&
            !outputModalities.any { it.equals("text", ignoreCase = true) }

    /**
     * Coarse routing category used to decide which OpenRouter endpoint a model
     * should be sent to (chat completions vs. audio/speech vs. images vs.
     * videos vs. none).
     */
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

    /** True when this model can be driven through the chat-completions endpoint. */
    val isChatCapable: Boolean
        get() = modelKind == ModelKind.Chat

    val supportsReasoning: Boolean
        get() = reasoningConfig != null || supportedParameters.any {
            it.equals("reasoning", ignoreCase = true) ||
                it.equals("include_reasoning", ignoreCase = true) ||
                it.equals("reasoning_effort", ignoreCase = true)
        }

    /**
     * Whether OpenRouter's `openrouter:web_search` server tool can be used with
     * this model (chat-capable + tool calling).
     */
    val supportsWebSearch: Boolean
        get() = isChatCapable && supportedParameters.any {
            it.equals("tools", ignoreCase = true)
        }

    /**
     * Models that always produce reasoning tokens (effort cannot be `none`).
     * Uses OpenRouter's `reasoning.mandatory` when available, otherwise id heuristics.
     */
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

    /** Effort levels this model accepts for requests and UI selection. */
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

    /** Prompt price in USD per million tokens, or null if unknown. */
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

    /**
     * Returns the input kinds the user attached that this model cannot accept,
     * based on its reported [inputModalities]. Empty when everything is
     * allowed, or when the modalities are unknown — local/restored models are
     * treated permissively so we never block a model we don't have metadata for.
     *
     * OpenRouter reports three non-text input modalities (`image`, `audio`,
     * `file`); video attachments are folded into `file` since there is no
     * dedicated video-input modality.
     */
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

/**
 * Reasoning configuration returned by OpenRouter for a model (`reasoning` object).
 */
data class ModelReasoningConfig(
    /** Allowed effort values, or null when the gateway accepts all standard efforts. */
    val supportedEfforts: List<ReasoningEffort>? = null,
    val defaultEffort: ReasoningEffort? = null,
    val defaultEnabled: Boolean? = null,
    val mandatory: Boolean = false,
    val supportsMaxTokens: Boolean = false,
)

/**
 * Token pricing for a model, expressed as a per-token cost string in USD.
 */
data class ModelPricing(
    val prompt: String? = null,
    val completion: String? = null,
)

/**
 * Request body for OpenRouter text-to-speech (`POST /api/v1/audio/speech`).
 */
data class SpeechRequest(
    val model: String,
    /** Text to synthesize. */
    val input: String,
    /** Provider-specific voice id. Required by most providers; omitted when null. */
    val voice: String? = null,
    /** Output encoding: `mp3` or `pcm` (defaults to `pcm` server-side). */
    val responseFormat: String? = null,
    /** Playback speed multiplier (only some providers honor it). */
    val speed: Float? = null,
)

/**
 * Result of a TTS request: the raw audio bytes and the content type reported
 * by the server (e.g. `audio/mpeg`, `audio/pcm`).
 */
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

/**
 * Request body for OpenRouter image generation (`POST /api/v1/images`).
 */
data class ImageRequest(
    val model: String,
    /** Text description of the desired image. */
    val prompt: String,
    /** Number of images to generate (1-10). Defaults to 1. */
    val n: Int = 1,
    /** Resolution tier (`512`, `1K`, `2K`, `4K`) — optional. */
    val resolution: String? = null,
    /** Aspect ratio (`1:1`, `16:9`, …) — optional. */
    val aspectRatio: String? = null,
    /** Output encoding (`png`, `jpeg`, `webp`, `svg`) — optional. */
    val outputFormat: String? = null,
)

/** A single generated image, as raw decoded bytes plus its media type. */
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

/** Result of an image-generation request. */
data class ImageResult(
    val images: List<GeneratedImage>,
)

/**
 * Request body for OpenRouter video generation (`POST /api/v1/videos`).
 * Generation is asynchronous: submit, poll the returned job, then download.
 */
data class VideoRequest(
    val model: String,
    /** Text description of the desired video. Optional for image-to-video. */
    val prompt: String? = null,
    /** First / last frame images as data URLs (`data:image/...;base64,...`). */
    val frameImages: List<VideoFrameImage> = emptyList(),
    /** Extra reference images as data URLs (style / character guidance). */
    val inputReferences: List<String> = emptyList(),
)

/** An image used as the first or last frame of a generated video. */
data class VideoFrameImage(
    val dataUrl: String,
    /** `first_frame` or `last_frame`. */
    val frameType: String,
)

/** A single generated video, as raw decoded bytes plus its media type. */
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

/** Result of a video-generation request. */
data class VideoResult(
    val videos: List<GeneratedVideo>,
)

/**
 * Request body for OpenRouter speech-to-text (`POST /api/v1/audio/transcriptions`).
 * Audio is sent as raw base64 bytes (not a `data:` URI) plus a format hint.
 */
data class TranscriptionRequest(
    val model: String,
    /** Raw base64-encoded audio bytes (no `data:` prefix). */
    val audioBase64: String,
    /** Audio format: `wav`, `mp3`, `flac`, `m4a`, `ogg`, `webm`, `aac`. */
    val format: String,
    /** Optional ISO-639-1 language hint (auto-detected when null). */
    val language: String? = null,
)

/**
 * A single message exchanged with the chat completion endpoint.
 *
 * When [audioBase64] is set, the request is sent as multimodal content with an
 * `input_audio` part (plus optional accompanying [content] text).
 * When [imageDataUrls] is non-empty, each entry is sent as an `image_url` part
 * (`data:image/...;base64,...`).
 */
data class ChatMessageDto(
    /** Message role: `system`, `user` or `assistant`. */
    val role: String,
    val content: String,
    /** Base64-encoded audio payload, when sending voice. */
    val audioBase64: String? = null,
    /** Audio format for [audioBase64], e.g. `m4a`, `wav`, `mp3`. */
    val audioFormat: String? = null,
    /** Data-URL images for vision models. */
    val imageDataUrls: List<String> = emptyList(),
)

/**
 * Credit / usage info for the authenticated OpenRouter API key
 * (`GET /api/v1/key`) plus account balance from `GET /api/v1/credits`.
 */
data class OpenRouterKeyInfo(
    val label: String? = null,
    /** Lifetime USD usage on this key. */
    val usage: Double = 0.0,
    /** Optional spending cap for the key, or null if unlimited. */
    val limit: Double? = null,
    /** Remaining credits under the key's limit, or null if unlimited. */
    val limitRemaining: Double? = null,
    /** Total credits purchased on the account (USD), if available. */
    val totalCredits: Double? = null,
    /** Total credits used on the account (USD), if available. */
    val totalUsage: Double? = null,
) {
    /** Account credits left (`totalCredits - totalUsage`), or null if unknown. */
    val accountCreditsRemaining: Double?
        get() {
            val purchased = totalCredits ?: return null
            val used = totalUsage ?: return null
            return purchased - used
        }

    /**
     * Best available "credits left" figure: account balance when the credits
     * endpoint succeeds, otherwise the key's spending-cap remainder.
     */
    val creditsRemaining: Double?
        get() = accountCreditsRemaining ?: limitRemaining
}

/**
 * An input kind the user tried to send that the model's reported modalities
 * don't include. Carries the user-facing reason shown as an error turn.
 */
enum class UnsupportedInput(val reason: String) {
    Image("doesn't support image input. Pick a vision-capable model on OpenRouter."),
    Audio("doesn't support voice input. Pick an audio-capable model on OpenRouter."),
    File("doesn't support file input. Pick a file-capable model on OpenRouter."),
}

/**
 * Coarse routing category for an [OpenRouterModel], used to pick the correct
 * OpenRouter endpoint for a given model.
 */
enum class ModelKind {
    /** Text chat via `/api/v1/chat/completions` (incl. vision + audio-input). */
    Chat,

    /** Text-to-speech via `/api/v1/audio/speech`. */
    Tts,

    /** Image generation via `/api/v1/images`. */
    ImageGen,

    /** Video generation via async `/api/v1/videos`. */
    VideoGen,

    /** Embeddings via `/api/v1/embeddings` — not chat-capable. */
    Embedding,

    /** Reranking — not chat-capable. */
    Rerank,

    /** Audio transcription via `/api/v1/audio/transcriptions`. */
    Transcription,
}
