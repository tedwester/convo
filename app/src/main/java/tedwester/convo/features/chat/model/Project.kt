package tedwester.convo.features.chat.model

/** A user-created collection of chats. */
data class Project(
    val id: String,
    val name: String,
    val description: String = "",
    val createdAt: Long,
)
