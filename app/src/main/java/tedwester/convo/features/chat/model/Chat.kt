package tedwester.convo.features.chat.model

data class Chat(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,

    val preview: String = "",
    val modelId: String? = null,
    val modelName: String? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val projectId: String? = null,

    val systemMessage: String = "",
)
