package tedwester.convo.features.chat.state

import android.os.SystemClock
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tedwester.convo.core.network.model.ChatMessageDto
import tedwester.convo.core.network.model.OpenRouterModel
import tedwester.convo.core.network.model.ReasoningRequest
import tedwester.convo.features.chat.data.CompletionRequest
import tedwester.convo.features.chat.model.ChatAttachment
import tedwester.convo.features.chat.model.ChatMessage
import tedwester.convo.features.chat.model.MessageAuthor
import java.io.File

internal fun ChatState.withSystemMessage(
    history: List<ChatMessageDto>,
    additionalSystem: String = "",
): List<ChatMessageDto> {
    val system = listOf(systemMessage.trim(), additionalSystem.trim())
        .filter { it.isNotEmpty() }
        .joinToString("\n\n")
    if (system.isEmpty()) return history
    return listOf(ChatMessageDto(role = "system", content = system)) + history
}

internal fun ChatState.textHistory(): List<ChatMessageDto> =
    messages.map {
        ChatMessageDto(
            role = if (it.author == MessageAuthor.User) "user" else "assistant",
            content = if (it.author == MessageAuthor.Assistant) it.apiContent() else it.content,
        )
    }

/**
 * Build OpenRouter history from local turns, re-encoding any image attachments
 * so regenerate / multi-turn vision keeps the original files.
 */
internal suspend fun ChatState.buildApiHistory(beforeAssistantIndex: Int? = null): List<ChatMessageDto> =
    buildApiHistoryFrom(messages.toList(), beforeAssistantIndex)

internal suspend fun ChatState.buildApiHistoryFrom(
    source: List<ChatMessage>,
    beforeAssistantIndex: Int? = null,
    additionalSystemMessage: String = "",
): List<ChatMessageDto> =
    withContext(Dispatchers.IO) {
        val slice = beforeAssistantIndex?.let { source.take(it) } ?: source
        val turns = slice.map { message ->
            if (message.author == MessageAuthor.Assistant) {
                ChatMessageDto(role = "assistant", content = message.apiContent())
            } else {
                val images = message.attachments.filter { it.isImage }
                val files = message.attachments.filterNot { it.isImage }
                ChatMessageDto(
                    role = "user",
                    content = apiPromptForUserMessage(message.content, files, images.isNotEmpty()),
                    imageDataUrls = images.mapNotNull { encodeImageDataUrl(it) },
                )
            }
        }
        withSystemMessage(turns, additionalSystemMessage)
    }

internal fun ChatState.apiPromptForUserMessage(
    displayContent: String,
    files: List<ChatAttachment>,
    hasImages: Boolean,
): String {
    val text = displayContent
        .lineSequence()
        .filterNot { line -> line.trimStart().startsWith("📎") }
        .joinToString("\n")
        .trim()
    val fileNote = if (files.isNotEmpty()) {
        files.joinToString(separator = "\n") { "📎 ${it.displayName}" }
    } else {
        ""
    }
    return when {
        text.isNotBlank() && fileNote.isNotBlank() -> "$text\n\nAttached files:\n$fileNote"
        text.isNotBlank() -> text
        fileNote.isNotBlank() -> "The user attached these files:\n$fileNote"
        hasImages -> "Describe or respond to the attached image(s)."
        else -> displayContent
    }
}

internal fun ChatState.encodeImageDataUrl(attachment: ChatAttachment): String? {
    val file = File(attachment.path)
    if (!file.exists() || !file.isFile) return null
    val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
    if (bytes.isEmpty()) return null
    val mime = attachment.mimeType?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
    return "data:$mime;base64,$b64"
}

internal fun ChatState.runCompletion(
    model: OpenRouterModel,
    history: List<ChatMessageDto>,
    assistantId: Long? = null,
    appendVariant: Boolean = false,
) {
    val chatId = currentChatId ?: return
    val id = assistantId ?: ++currentId
    val enableWebSearch = isSearchEnabled && model.supportsWebSearch
    if (enableWebSearch && !searchStore.load().persistAfterPrompt) {
        isSearchEnabled = false
    }
    val expectThinking = snapshotExpectStreamedThinking(model)
    if (assistantId == null) {
        messages += ChatMessage(
            id = id,
            author = MessageAuthor.Assistant,
            content = "",
            timestamp = System.currentTimeMillis(),
            isStreaming = true,
            expectStreamedThinking = expectThinking,
            expectWebSearch = enableWebSearch,
            thinkingStartedAtElapsed = SystemClock.elapsedRealtime(),
        )
    } else {
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) {
            messages[index] = messages[index].copy(
                expectStreamedThinking = expectThinking,
                expectWebSearch = enableWebSearch,
            )
        }
    }
    isRunning = true
    completions.viewingChatId = chatId
    val reasoning = ReasoningRequest.from(model, reasoningPreferences)
    activeCompletionSessionId = completions.start(
        CompletionRequest(
            chatId = chatId,
            apiKey = apiKey,
            model = model,
            history = history,
            messages = messages.toList(),
            assistantId = id,
            appendVariant = appendVariant,
            enableWebSearch = enableWebSearch,
            systemMessage = systemMessage,
            reasoning = reasoning,
            maxTokens = apiPreferencesStore.load().maxTokens,
        ),
    )
}
