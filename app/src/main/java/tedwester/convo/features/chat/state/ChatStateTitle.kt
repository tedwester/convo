package tedwester.convo.features.chat.state

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import tedwester.convo.core.network.model.ChatMessageDto
import tedwester.convo.features.chat.model.MessageAuthor

/**
 * AI chat titles, generated the way the industry does it: after the first exchange lands, a
 * second, hidden, throwaway completion summarizes the conversation into a few words.
 *
 * The call is tiny (a short prompt in, a few words out) and runs on a fixed chain of the
 * cheapest models — free routing first, then a near-free fallback — each retried, so the
 * feature costs effectively nothing and never bothers the user when it fails. A failed title
 * simply leaves whatever automatic title is there.
 *
 * Titles only ever replace automatic ones: the untouched default or the truncation the
 * repository derived from the first message. A manual rename always wins and is never
 * overwritten.
 */
private val TitleModelChain = listOf(
    "openrouter/free",
    "openai/gpt-oss-20b",
)

private const val TitleRetriesPerModel = 2
private const val TitleRetryDelayMs = 1000L
private const val TitleMaxTokens = 64
private const val TitlePromptChars = 500

/** A title is a few words, never a sentence and never our own prompt echoed back. */
private const val TitleMaxChars = 40
private const val TitleMaxWords = 7

/** Vocabulary from the title prompt itself — any candidate containing these is the model
 * parroting instructions instead of answering, and must be rejected, not applied. */
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
    val assistantText = snapshot
        .firstOrNull { it.author == MessageAuthor.Assistant && !it.isStreaming }
        ?.content
        ?.takeIf { it.isNotBlank() }
        ?: return
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
                    maxTokens = TitleMaxTokens,
                )
            }.getOrNull()?.trim().orEmpty()
            parseTitleResponse(raw)?.let { return it }
        }
    }
    return null
}

internal fun buildTitlePrompt(userText: String, assistantText: String): String =
    "Reply with ONLY a JSON object like {\"title\": \"...\"}. " +
        "Do not explain. Do not repeat these instructions.\n\n" +
        "The title must be 2-4 words, no emojis, no quotation marks, " +
        "same language as the conversation.\n\n" +
        "Conversation:\n" +
        "User: ${userText.take(TitlePromptChars)}\n" +
        "Assistant: ${assistantText.take(TitlePromptChars)}"

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

/**
 * A candidate only counts if it reads like a title: short, a few words, and nothing from
 * the prompt's own vocabulary. Anything else is the model thinking out loud, and applying
 * it would put instructions where the chat's name goes.
 */
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
    return cleaned
}
