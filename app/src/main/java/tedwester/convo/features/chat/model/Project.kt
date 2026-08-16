package tedwester.convo.features.chat.model

data class Project(
    val id: String,
    val name: String,
    val description: String = "",
    val createdAt: Long,
)
