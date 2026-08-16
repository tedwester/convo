package tedwester.convo.features.chat.model

/**
 * A saved conversation (metadata only — messages live separately).
 */
data class Chat(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** Short preview shown in the chat list (usually last message). */
    val preview: String = "",
    val modelId: String? = null,
    val modelName: String? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val projectId: String? = null,
    /** Custom system prompt prepended to API history for this chat. */
    val systemMessage: String = "",
)
