package tedwester.convo.features.chat.model

/**
 * Who produced a given [ChatMessage].
 */
enum class MessageAuthor {
    User,
    Assistant,
}

/**
 * A single message within a chat conversation.
 *
 * Assistant turns may hold multiple [variants] (redo / swipe). [content] mirrors
 * the active variant while idle; while streaming it holds in-flight text.
 * [reasoning] / [reasoningVariants] hold the thinking trace for the same index.
 * [thoughtDurationMs] / [thoughtDurationVariants] hold elapsed thinking time likewise.
 * [webSearchSteps] / [webSearchStepVariants] hold web search timelines per variant.
 */
data class ChatMessage(
    val id: Long,
    val author: MessageAuthor,
    val content: String,
    val timestamp: Long = 0L,
    /** True when this bubble represents a recorded voice turn. */
    val isVoice: Boolean = false,
    /** Images / files sent with this turn. */
    val attachments: List<ChatAttachment> = emptyList(),
    /** Attachments aligned with [variants] (e.g. per-redo TTS audio). */
    val attachmentVariants: List<List<ChatAttachment>> = emptyList(),
    /** All response texts for this turn (including stopped / error attempts). */
    val variants: List<String> = emptyList(),
    /** Which [variants] entry is active (ignored while [isStreaming]). */
    val variantIndex: Int = 0,
    /** True while tokens are still arriving from the model. Not persisted. */
    val isStreaming: Boolean = false,
    /** Live / active thinking trace for this assistant turn. */
    val reasoning: String = "",
    /** Thinking traces aligned with [variants] (same length when present). */
    val reasoningVariants: List<String> = emptyList(),
    /** Elapsed thinking time in milliseconds for the active variant, when known. */
    val thoughtDurationMs: Long? = null,
    /** Thinking durations aligned with [variants] (same length when present). */
    val thoughtDurationVariants: List<Long?> = emptyList(),
    /** True when generation was cancelled while the model was still thinking. */
    val stoppedWhileThinking: Boolean = false,
    /**
     * Whether this turn was started with streamed thinking enabled.
     * Snapshotted at request start; not affected by later preference changes.
     */
    val expectStreamedThinking: Boolean = false,
    /**
     * [android.os.SystemClock.elapsedRealtime] when thinking began for the in-flight
     * variant. Live streaming only; not persisted.
     */
    val thinkingStartedAtElapsed: Long? = null,
    /**
     * In-flight shimmer label (e.g. "Generating audio…"). Live only; not persisted.
     * [content] stays empty until the real reply (or spoken text) is ready.
     */
    val statusLabel: String? = null,
    /** Live / active web search steps for this assistant turn. */
    val webSearchSteps: List<WebSearchStep> = emptyList(),
    /** Web search timelines aligned with [variants]. */
    val webSearchStepVariants: List<List<WebSearchStep>> = emptyList(),
    /** Whether this turn was started with web search enabled. */
    val expectWebSearch: Boolean = false,
    /**
     * Snapshotted when this assistant turn started: open the voice script
     * instead of the audio player. Later setting changes don't rewrite older turns.
     */
    val showVoiceAsTextFirst: Boolean = false,
    /**
     * True after this turn's audio has autoplayed (or started autoplaying).
     * Persisted so leaving and re-entering the chat cannot replay it.
     */
    val voiceAutoPlayed: Boolean = false,
) {
    /** Variants to page through; falls back to [content] for legacy rows. */
    fun savedVariants(): List<String> {
        if (variants.isNotEmpty()) return variants
        return content.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
    }

    /** Attachment sets aligned with [savedVariants]; falls back to [attachments]. */
    fun savedAttachmentVariants(): List<List<ChatAttachment>> {
        if (attachmentVariants.isNotEmpty()) return attachmentVariants
        return attachments.takeIf { it.isNotEmpty() }?.let { listOf(it) } ?: emptyList()
    }

    /** Attachments for the active variant (or the in-flight generation). */
    fun activeAttachments(): List<ChatAttachment> = attachmentsAt(variantIndex)

    /** Attachments saved for [index] (idle multi-variant paging). */
    fun attachmentsAt(index: Int): List<ChatAttachment> {
        if (isStreaming) return attachments
        val saved = savedAttachmentVariants()
        if (saved.isNotEmpty()) return saved.getOrNull(index) ?: emptyList()
        return attachments.takeIf { index == variantIndex } ?: emptyList()
    }

    /** Spoken / visible text that Copy should use. */
    fun copyableText(): String = copyableTextAt(variantIndex)

    /** Copyable text for a saved variant index. */
    fun copyableTextAt(index: Int): String {
        if (isStreaming) return ""
        val text = bodyTextAt(index)
        if (isAssistantStatusContent(text)) return ""
        return text.trim()
    }

    /** Active variant text (or in-flight stream), including errors / stopped status. */
    fun activeBodyText(): String = bodyTextAt(variantIndex)

    /** Body text for a saved variant index. */
    fun bodyTextAt(index: Int): String {
        if (isStreaming) return content
        return savedVariants().getOrNull(index) ?: content
    }

    fun hasAudioAttachment(): Boolean =
        activeAttachments().any { it.mimeType?.startsWith("audio/", ignoreCase = true) == true }

    fun hasImageAttachment(): Boolean = activeAttachments().any { it.isImage }

    fun hasVideoAttachment(): Boolean = activeAttachments().any { it.isVideo }

    /**
     * True when the bubble should show media only (audio player / images / video),
     * not body text. Errors, stopped, and empty-response status always stay visible.
     */
    fun hidesGeneratedBody(): Boolean = hidesGeneratedBodyAt(variantIndex)

    fun hidesGeneratedBodyAt(index: Int): Boolean {
        if (isStreaming) return false
        val text = bodyTextAt(index)
        if (isAssistantStatusContent(text)) return false
        val attachments = attachmentsAt(index)
        return attachments.any { it.mimeType?.startsWith("audio/", ignoreCase = true) == true } ||
            attachments.any { it.isImage } ||
            attachments.any { it.isVideo }
    }

    val variantCount: Int
        get() = when {
            isStreaming -> variants.size + 1
            variants.isNotEmpty() -> variants.size
            content.isNotBlank() -> 1
            else -> 0
        }

    /** 1-based index shown in the "2/3" pager label. */
    val pagerIndex: Int
        get() = when {
            isStreaming -> variants.size + 1
            variants.isNotEmpty() -> variantIndex.coerceIn(0, variants.lastIndex) + 1
            content.isNotBlank() -> 1
            else -> 0
        }

    /** Thinking text for the active variant (or live stream). */
    fun activeReasoning(): String {
        if (isStreaming) return reasoning
        if (reasoningVariants.isNotEmpty()) {
            return reasoningVariants.getOrNull(variantIndex)
                ?: reasoningVariants.lastOrNull()
                ?: reasoning
        }
        return reasoning
    }

    /** Elapsed thinking time for the active variant (or live stream). */
    fun activeThoughtDuration(): Long? {
        if (isStreaming) return thoughtDurationMs
        if (thoughtDurationVariants.isNotEmpty()) {
            return thoughtDurationVariants.getOrNull(variantIndex)
                ?: thoughtDurationVariants.lastOrNull()
                ?: thoughtDurationMs
        }
        return thoughtDurationMs
    }

    /** Web search timeline for the active variant (or live stream). */
    fun activeWebSearchSteps(): List<WebSearchStep> {
        if (isStreaming) return webSearchSteps
        if (webSearchStepVariants.isNotEmpty()) {
            return webSearchStepVariants.getOrNull(variantIndex)
                ?: webSearchStepVariants.lastOrNull()
                ?: webSearchSteps
        }
        return webSearchSteps
    }

    /** Text sent to the API for this assistant turn. */
    fun apiContent(): String {
        val active = variants.getOrNull(variantIndex) ?: content
        if (!isAssistantStatusContent(active)) return active
        return variants.lastOrNull { !isAssistantStatusContent(it) } ?: active
    }
}

internal const val STOPPED_RESPONSE_TEXT = "You stopped the response."
internal const val EMPTY_RESPONSE_TEXT = "The model returned an empty response."

/** Settle a streaming assistant turn the moment the user hits stop. */
internal fun ChatMessage.asStopped(): ChatMessage {
    if (!isStreaming) return this
    val isSpecial = statusLabel != null ||
        attachments.isNotEmpty() ||
        attachmentVariants.isNotEmpty()
    val appending = variants.isNotEmpty()
    if (isSpecial) {
        val resolved = STOPPED_RESPONSE_TEXT
        val prior = savedVariants()
        val priorAttachments = savedAttachmentVariants()
        return copy(
            content = resolved,
            attachments = emptyList(),
            isStreaming = false,
            statusLabel = null,
            variants = if (appending) prior + resolved else listOf(resolved),
            attachmentVariants = if (appending) {
                priorAttachments + listOf(emptyList())
            } else {
                listOf(emptyList())
            },
            variantIndex = if (appending) prior.size else 0,
            thinkingStartedAtElapsed = null,
        )
    }
    val text = content.ifBlank { STOPPED_RESPONSE_TEXT }
    val nextVariants = if (appending) variants + text else listOf(text)
    val nextReasoning = if (appending) {
        val aligned = if (reasoningVariants.size == variants.size) {
            reasoningVariants
        } else {
            variants.mapIndexed { i, _ -> reasoningVariants.getOrElse(i) { "" } }
        }
        aligned + reasoning
    } else {
        listOf(reasoning)
    }
    val nextDurations = if (appending) {
        val aligned = if (thoughtDurationVariants.size == variants.size) {
            thoughtDurationVariants
        } else {
            variants.mapIndexed { i, _ -> thoughtDurationVariants.getOrNull(i) }
        }
        aligned + thoughtDurationMs
    } else {
        listOf(thoughtDurationMs)
    }
    val finalizedSearch = webSearchSteps.map { it.copy(isSearching = false) }
    val nextSearch = if (appending) {
        val aligned = if (webSearchStepVariants.size == variants.size) {
            webSearchStepVariants
        } else {
            variants.mapIndexed { i, _ -> webSearchStepVariants.getOrElse(i) { emptyList() } }
        }
        aligned + listOf(finalizedSearch)
    } else {
        listOf(finalizedSearch)
    }
    return copy(
        content = text,
        isStreaming = false,
        statusLabel = null,
        variants = nextVariants,
        variantIndex = nextVariants.lastIndex,
        reasoningVariants = nextReasoning,
        thoughtDurationVariants = nextDurations,
        webSearchStepVariants = nextSearch,
        stoppedWhileThinking = text == STOPPED_RESPONSE_TEXT && reasoning.isNotBlank(),
        thinkingStartedAtElapsed = null,
        webSearchSteps = finalizedSearch,
    )
}

internal fun isAssistantStatusContent(content: String): Boolean =
    content == STOPPED_RESPONSE_TEXT ||
        content == EMPTY_RESPONSE_TEXT ||
        content == "Stopped." ||
        content.startsWith("⚠")
