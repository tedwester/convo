package tedwester.convo.features.chat.model

enum class MessageAuthor {
    User,
    Assistant,
}

data class ChatMessage(
    val id: Long,
    val author: MessageAuthor,
    val content: String,
    val timestamp: Long = 0L,

    val isVoice: Boolean = false,

    val attachments: List<ChatAttachment> = emptyList(),

    val attachmentVariants: List<List<ChatAttachment>> = emptyList(),

    val variants: List<String> = emptyList(),

    val variantIndex: Int = 0,

    val isStreaming: Boolean = false,

    val reasoning: String = "",

    val reasoningVariants: List<String> = emptyList(),

    val thoughtDurationMs: Long? = null,

    val thoughtDurationVariants: List<Long?> = emptyList(),

    val stoppedWhileThinking: Boolean = false,

    val expectStreamedThinking: Boolean = false,

    val thinkingStartedAtElapsed: Long? = null,

    val statusLabel: String? = null,

    val webSearchSteps: List<WebSearchStep> = emptyList(),

    val webSearchStepVariants: List<List<WebSearchStep>> = emptyList(),

    val expectWebSearch: Boolean = false,

    val showVoiceAsTextFirst: Boolean = false,

    val voiceAutoPlayed: Boolean = false,

    val variantContinuations: List<List<ChatMessage>> = emptyList(),
) {

    fun savedVariants(): List<String> {
        if (variants.isNotEmpty()) return variants
        return content.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
    }

    fun savedAttachmentVariants(): List<List<ChatAttachment>> {
        if (attachmentVariants.isNotEmpty()) return attachmentVariants
        return attachments.takeIf { it.isNotEmpty() }?.let { listOf(it) } ?: emptyList()
    }

    fun activeAttachments(): List<ChatAttachment> = attachmentsAt(variantIndex)

    fun attachmentsAt(index: Int): List<ChatAttachment> {
        if (isStreaming) return attachments
        val saved = savedAttachmentVariants()
        if (saved.isNotEmpty()) return saved.getOrNull(index) ?: emptyList()
        return attachments.takeIf { index == variantIndex } ?: emptyList()
    }

    fun copyableText(): String = copyableTextAt(variantIndex)

    fun copyableTextAt(index: Int): String {
        if (isStreaming) return ""
        val text = bodyTextAt(index)
        if (isAssistantStatusContent(text)) return ""
        return text.trim()
    }

    fun activeBodyText(): String = bodyTextAt(variantIndex)

    fun bodyTextAt(index: Int): String {
        if (isStreaming) return content
        return savedVariants().getOrNull(index) ?: content
    }

    fun hasAudioAttachment(): Boolean =
        activeAttachments().any { it.mimeType?.startsWith("audio/", ignoreCase = true) == true }

    fun hasImageAttachment(): Boolean = activeAttachments().any { it.isImage }

    fun hasVideoAttachment(): Boolean = activeAttachments().any { it.isVideo }

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

    val pagerIndex: Int
        get() = when {
            isStreaming -> variants.size + 1
            variants.isNotEmpty() -> variantIndex.coerceIn(0, variants.lastIndex) + 1
            content.isNotBlank() -> 1
            else -> 0
        }

    fun activeReasoning(): String {
        if (isStreaming) return reasoning
        if (reasoningVariants.isNotEmpty()) {
            return reasoningVariants.getOrNull(variantIndex)
                ?: reasoningVariants.lastOrNull()
                ?: reasoning
        }
        return reasoning
    }

    fun activeThoughtDuration(): Long? {
        if (isStreaming) return thoughtDurationMs
        if (thoughtDurationVariants.isNotEmpty()) {
            return thoughtDurationVariants.getOrNull(variantIndex)
                ?: thoughtDurationVariants.lastOrNull()
                ?: thoughtDurationMs
        }
        return thoughtDurationMs
    }

    fun activeWebSearchSteps(): List<WebSearchStep> {
        if (isStreaming) return webSearchSteps
        if (webSearchStepVariants.isNotEmpty()) {
            return webSearchStepVariants.getOrNull(variantIndex)
                ?: webSearchStepVariants.lastOrNull()
                ?: webSearchSteps
        }
        return webSearchSteps
    }

    fun apiContent(): String {
        val active = variants.getOrNull(variantIndex) ?: content
        if (!isAssistantStatusContent(active)) return active
        return variants.lastOrNull { !isAssistantStatusContent(it) } ?: active
    }

    fun userDisplayText(): String = userDisplayTextOf(content)

    fun withUserDisplayText(text: String): ChatMessage {
        val next = mergeUserDisplayText(content, text)
        return if (next == content) this else copy(content = next)
    }
}

internal const val STOPPED_RESPONSE_TEXT = "You stopped the response."
internal const val EMPTY_RESPONSE_TEXT = "The model returned an empty response."

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

internal fun userDisplayTextOf(content: String): String =
    content.lineSequence()
        .filterNot { line -> line.trimStart().startsWith("📎") }
        .joinToString("\n")
        .trim()

internal fun mergeUserDisplayText(content: String, text: String): String {
    val fileNote = content.lineSequence()
        .filter { line -> line.trimStart().startsWith("📎") }
        .joinToString("\n")
        .trim()
    return listOf(text.trim(), fileNote)
        .filter { it.isNotBlank() }
        .joinToString("\n")
}
