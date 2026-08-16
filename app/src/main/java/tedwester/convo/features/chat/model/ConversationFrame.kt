package tedwester.convo.features.chat.model

import androidx.compose.runtime.Immutable

@Immutable
data class ConversationFrame(
    val sessionId: Int,
    val chatId: String?,
    val messages: List<ChatMessage>,
    val messageListRevision: Int,
) {
    val hasMessages: Boolean get() = messages.isNotEmpty()
}
