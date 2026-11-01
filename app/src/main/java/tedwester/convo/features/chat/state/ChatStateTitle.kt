package tedwester.convo.features.chat.state

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import tedwester.convo.core.network.model.ChatMessageDto
import tedwester.convo.core.network.model.ReasoningRequest
import tedwester.convo.features.chat.model.MessageAuthor
import tedwester.convo.features.chat.model.isAssistantStatusContent

private val TitleModelChain = listOf(
    "liquid/lfm-2.5-2.6b:free",
    "nvidia/nemotron-3.5-lightning:free",
    "poolside/laguna-s-2.1:free",
)

private const val TitleRetriesPerModel = 5
private const val TitleRetryDelayMs = 1000L
private const val TitleMaxTokens = 256
private const val TitlePromptChars = 500

private const val TitleMaxChars = 40
private const val TitleMaxWords = 7

private val TitleEchoWords = listOf(
    "concise",
    "conversation",
    "json",
    "generate",
    "following",
    "reply",
    "quotation",
    "language",
    "emoji",
    "instruction",
    "summar",
    "title",
)

internal fun ChatState.maybeGenerateTitle() {
    val chatId = currentChatId ?: return
    val snapshot = messages.toList()
    val userText = snapshot
        .firstOrNull { it.author == MessageAuthor.User }
        ?.userDisplayText()
        ?.takeIf { it.isNotBlank() }
        ?: return
    val assistantRaw = snapshot
        .firstOrNull { it.author == MessageAuthor.Assistant && !it.isStreaming }
        ?.content
        ?.takeIf { it.isNotBlank() }
        ?: return
    val assistantText = assistantRaw.takeUnless { isAssistantStatusContent(it) }.orEmpty()
    val current = chats.firstOrNull { it.id == chatId } ?: return
    val automatic = current.title == "New chat" ||
        current.title == repository.deriveTitle(snapshot)
    if (!automatic) return
    if (!titlingChatIds.add(chatId)) return

    scope.launch {
        try {
            val title = generateTitleFor(userText, assistantText) ?: return@launch
            val latest = messages.toList()
            val stillAutomatic = chats.firstOrNull { it.id == chatId }?.title?.let { listed ->
                listed == "New chat" || listed == repository.deriveTitle(latest)
            } ?: false
            if (stillAutomatic) {
                renameChatImpl(chatId, title)
            }
        } finally {
            titlingChatIds.remove(chatId)
        }
    }
}

private suspend fun ChatState.generateTitleFor(
    userText: String,
    assistantText: String,
): String? {
    val key = apiKey
    if (key.isBlank()) return null
    val prompt = buildTitlePrompt(userText, assistantText)
    for (model in TitleModelChain) {
        repeat(TitleRetriesPerModel) { attempt ->
            if (attempt > 0) delay(TitleRetryDelayMs)
            val raw = runCatching {
                api.chatCompletion(
                    apiKey = key,
                    model = model,
                    messages = listOf(ChatMessageDto(role = "user", content = prompt)),
                    reasoning = ReasoningRequest(effort = "none", exclude = true),
                    maxTokens = TitleMaxTokens,
                )
            }.getOrNull()?.trim().orEmpty()
            parseTitleResponse(raw)?.let { return it }
        }
    }
    return null
}

internal fun buildTitlePrompt(userText: String, assistantText: String): String {
    val conversation = buildString {
        append("User: ${userText.take(TitlePromptChars)}\n")
        if (assistantText.isNotBlank()) {
            append("Assistant: ${assistantText.take(TitlePromptChars)}")
        }
    }.trimEnd()
    return "Reply with ONLY a JSON object like {\"title\": \"...\"}. " +
        "Do not explain. Do not repeat these instructions.\n\n" +
        "The title must be 2-4 words, no emojis, no quotation marks, " +
        "capitalized first letter, same language as the conversation.\n\n" +
        "Conversation:\n" +
        conversation
}

internal fun parseTitleResponse(raw: String): String? {
    if (raw.isBlank()) return null
    val jsonTitle = runCatching {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return@runCatching null
        JSONObject(raw.substring(start, end + 1)).optString("title").trim()
    }.getOrNull()
    jsonTitle?.takeIf { it.isNotBlank() }?.let { cleaned ->
        return cleaned.cleanTitleCandidate() ?: return null
    }
    val fallback = raw.lines().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    return fallback.cleanTitleCandidate()
}

internal fun String.cleanTitleCandidate(): String? {
    val cleaned = trim()
        .trim('"', '“', '”', '‘', '’', '«', '»', '「', '」')
        .trim()
        .trimEnd('.', '!', '?', ':', ';', ',')
        .trim()
        .take(TitleMaxChars)
        .trim()
    if (cleaned.length < 2) return null
    if (cleaned.split(Regex("\\s+")).size > TitleMaxWords) return null
    if (cleaned.contains('{') || cleaned.contains('}')) return null
    val lower = cleaned.lowercase()
    if (TitleEchoWords.any { lower.contains(it) }) return null
    return cleaned.replaceFirstChar { it.uppercase() }
}
