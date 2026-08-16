package tedwester.convo.features.chat.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import tedwester.convo.features.chat.model.Chat
import tedwester.convo.features.chat.model.ChatAttachment
import tedwester.convo.features.chat.model.ChatMessage
import tedwester.convo.features.chat.model.MessageAuthor
import tedwester.convo.features.chat.model.Project
import tedwester.convo.features.chat.model.WebSearchCitation
import tedwester.convo.features.chat.model.WebSearchStep
import java.io.File
import java.util.UUID

class ChatRepository(context: Context) {

    private val appContext = context.applicationContext
    private val root: File = File(appContext.filesDir, "chats").also { it.mkdirs() }
    private val indexFile = File(root, "index.json")
    private val projectsFile = File(root, "projects.json")
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getActiveChatId(): String? = prefs.getString(KEY_ACTIVE, null)

    fun setActiveChatId(id: String?) {
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    @Synchronized
    fun listChats(): List<Chat> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(indexFile.readText())
            buildList {
                for (i in 0 until array.length()) {
                    add(chatFromJson(array.getJSONObject(i)))
                }
            }.sortedWith(
                compareByDescending<Chat> { it.pinned }
                    .thenByDescending { it.updatedAt },
            )
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun loadMessages(chatId: String): List<ChatMessage> {
        val file = messageFile(chatId)
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val variants = parseVariants(obj.optJSONArray("variants"))
                    val variantIndex = obj.optInt("variantIndex", 0)
                    val thoughtDurationMs = if (obj.has("thoughtDurationMs") && !obj.isNull("thoughtDurationMs")) {
                        obj.optLong("thoughtDurationMs")
                    } else {
                        null
                    }
                    val thoughtDurationVariants = parseLongNullableList(
                        obj.optJSONArray("thoughtDurationVariants"),
                    ).ifEmpty {
                        if (thoughtDurationMs != null && variants.isNotEmpty()) {
                            List(variants.size) { index ->
                                if (index == variantIndex) thoughtDurationMs else null
                            }
                        } else {
                            emptyList()
                        }
                    }
                    val attachments = parseAttachments(obj.optJSONArray("attachments"))
                    val attachmentVariants = parseAttachmentVariants(
                        obj.optJSONArray("attachmentVariants"),
                    ).ifEmpty {
                        if (attachments.isNotEmpty() && variants.isNotEmpty()) {
                            List(variants.size) { index ->
                                if (index == variantIndex) attachments else emptyList()
                            }
                        } else {
                            emptyList()
                        }
                    }
                    add(
                        ChatMessage(
                            id = obj.optLong("id"),
                            author = if (obj.optString("author") == "assistant") {
                                MessageAuthor.Assistant
                            } else {
                                MessageAuthor.User
                            },
                            content = obj.optString("content"),
                            timestamp = obj.optLong("timestamp"),
                            isVoice = obj.optBoolean("isVoice", false),
                            attachments = attachments,
                            attachmentVariants = attachmentVariants,
                            variants = variants,
                            variantIndex = variantIndex,
                            reasoning = obj.optString("reasoning"),
                            reasoningVariants = parseStringList(obj.optJSONArray("reasoningVariants")),
                            thoughtDurationMs = thoughtDurationMs,
                            thoughtDurationVariants = thoughtDurationVariants,
                            webSearchSteps = parseWebSearchSteps(obj.optJSONArray("webSearchSteps")),
                            webSearchStepVariants = parseWebSearchStepVariants(
                                obj.optJSONArray("webSearchStepVariants"),
                            ),
                            stoppedWhileThinking = obj.optBoolean("stoppedWhileThinking", false),
                            showVoiceAsTextFirst = obj.optBoolean("showVoiceAsTextFirst", false),
                            voiceAutoPlayed = obj.optBoolean("voiceAutoPlayed", false),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun createChat(
        modelId: String? = null,
        modelName: String? = null,
        systemMessage: String = "",
    ): Chat {
        val now = System.currentTimeMillis()
        val chat = Chat(
            id = UUID.randomUUID().toString(),
            title = "New chat",
            createdAt = now,
            updatedAt = now,
            preview = "",
            modelId = modelId,
            modelName = modelName,
            systemMessage = systemMessage,
        )
        val chats = listChats().toMutableList()
        chats.add(0, chat)
        writeIndex(chats)
        writeMessages(chat.id, emptyList())
        setActiveChatId(chat.id)
        return chat
    }

    @Synchronized
    fun saveConversation(
        chatId: String,
        messages: List<ChatMessage>,
        title: String? = null,
        modelId: String? = null,
        modelName: String? = null,
        systemMessage: String? = null,

        setActive: Boolean = true,

        activityAt: Long? = null,
    ): Chat? {
        val chats = listChats().toMutableList()
        val index = chats.indexOfFirst { it.id == chatId }
        if (index < 0) return null

        val existing = chats[index]
        val preview = messages.lastOrNull()?.content?.take(96).orEmpty()
        val resolvedTitle = when {
            !title.isNullOrBlank() -> title
            existing.title != "New chat" -> existing.title
            else -> deriveTitle(messages) ?: existing.title
        }
        val messageActivityAt = messages.maxOfOrNull { it.timestamp }
        val updatedAt = listOfNotNull(existing.updatedAt, messageActivityAt, activityAt).max()
        val updated = existing.copy(
            title = resolvedTitle,
            updatedAt = updatedAt,
            preview = preview,
            modelId = modelId ?: existing.modelId,
            modelName = modelName ?: existing.modelName,
            systemMessage = systemMessage ?: existing.systemMessage,
        )
        chats[index] = updated
        writeIndex(chats)
        writeMessages(chatId, messages)
        if (setActive) {
            setActiveChatId(chatId)
        }
        return updated
    }

    @Synchronized
    fun deleteChat(chatId: String) {
        val chats = listChats().filterNot { it.id == chatId }
        writeIndex(chats)
        messageFile(chatId).delete()
        if (getActiveChatId() == chatId) {
            setActiveChatId(chats.firstOrNull()?.id)
        }
    }

    @Synchronized
    fun renameChat(chatId: String, title: String) {
        val chats = listChats().toMutableList()
        val index = chats.indexOfFirst { it.id == chatId }
        if (index < 0) return
        chats[index] = chats[index].copy(
            title = title.trim().ifBlank { "New chat" },
        )
        writeIndex(chats)
    }

    @Synchronized
    fun setChatPinned(chatId: String, pinned: Boolean) {
        updateChat(chatId) { it.copy(pinned = pinned) }
    }

    @Synchronized
    fun setChatArchived(chatId: String, archived: Boolean) {
        updateChat(chatId) { it.copy(archived = archived) }
    }

    @Synchronized
    fun setChatProject(chatId: String, projectId: String?) {
        val validProjectId = projectId?.takeIf { id ->
            listProjects().any { it.id == id }
        }
        updateChat(chatId) { it.copy(projectId = validProjectId) }
    }

    @Synchronized
    fun setChatSystemMessage(chatId: String, systemMessage: String) {
        updateChat(chatId) { it.copy(systemMessage = systemMessage) }
    }

    @Synchronized
    fun listProjects(): List<Project> {
        if (!projectsFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(projectsFile.readText())
            buildList {
                for (i in 0 until array.length()) {
                    add(projectFromJson(array.getJSONObject(i)))
                }
            }.sortedBy { it.createdAt }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun createProject(name: String, description: String): Project? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        if (listProjects().any { it.name.equals(trimmed, ignoreCase = true) }) return null
        val project = Project(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            description = description.trim(),
            createdAt = System.currentTimeMillis(),
        )
        val projects = listProjects().toMutableList().apply { add(project) }
        writeProjects(projects)
        return project
    }

    @Synchronized
    fun deleteProject(projectId: String) {
        val projects = listProjects().filterNot { it.id == projectId }
        writeProjects(projects)
        val chats = listChats().map { chat ->
            if (chat.projectId == projectId) chat.copy(projectId = null) else chat
        }
        writeIndex(chats)
    }

    @Synchronized
    fun projectNameExists(name: String, excludingId: String? = null): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        return listProjects().any {
            it.id != excludingId && it.name.equals(trimmed, ignoreCase = true)
        }
    }

    private fun updateChat(chatId: String, transform: (Chat) -> Chat) {
        val chats = listChats().toMutableList()
        val index = chats.indexOfFirst { it.id == chatId }
        if (index < 0) return
        chats[index] = transform(chats[index])
        writeIndex(chats)
    }

    private fun deriveTitle(messages: List<ChatMessage>): String? {
        val first = messages.firstOrNull {
            it.author == MessageAuthor.User && !it.isVoice && it.content.isNotBlank()
        } ?: return null
        val cleaned = first.content.trim().replace('\n', ' ')
        return if (cleaned.length <= 42) cleaned else cleaned.take(42).trimEnd() + "…"
    }

    private fun writeIndex(chats: List<Chat>) {
        val array = JSONArray()
        chats.forEach { array.put(chatToJson(it)) }
        indexFile.writeText(array.toString())
    }

    private fun writeProjects(projects: List<Project>) {
        val array = JSONArray()
        projects.forEach { array.put(projectToJson(it)) }
        projectsFile.writeText(array.toString())
    }

    private fun writeMessages(chatId: String, messages: List<ChatMessage>) {
        val array = JSONArray()
        messages.forEach { message ->
            array.put(
                JSONObject().apply {
                    put("id", message.id)
                    put(
                        "author",
                        if (message.author == MessageAuthor.Assistant) "assistant" else "user",
                    )
                    put("content", message.content)
                    put("timestamp", message.timestamp)
                    put("isVoice", message.isVoice)
                    if (message.reasoning.isNotBlank()) {
                        put("reasoning", message.reasoning)
                    }
                    if (message.reasoningVariants.isNotEmpty()) {
                        put(
                            "reasoningVariants",
                            JSONArray().apply { message.reasoningVariants.forEach { put(it) } },
                        )
                    }
                    message.thoughtDurationMs?.let { put("thoughtDurationMs", it) }
                    if (message.thoughtDurationVariants.isNotEmpty()) {
                        put(
                            "thoughtDurationVariants",
                            JSONArray().apply {
                                message.thoughtDurationVariants.forEach { duration ->
                                    if (duration == null) {
                                        put(JSONObject.NULL)
                                    } else {
                                        put(duration)
                                    }
                                }
                            },
                        )
                    }
                    if (message.stoppedWhileThinking) {
                        put("stoppedWhileThinking", true)
                    }
                    if (message.showVoiceAsTextFirst) {
                        put("showVoiceAsTextFirst", true)
                    }
                    if (message.voiceAutoPlayed) {
                        put("voiceAutoPlayed", true)
                    }
                    if (message.webSearchSteps.isNotEmpty()) {
                        put("webSearchSteps", webSearchStepsToJson(message.webSearchSteps))
                    }
                    if (message.webSearchStepVariants.isNotEmpty()) {
                        put(
                            "webSearchStepVariants",
                            JSONArray().apply {
                                message.webSearchStepVariants.forEach { steps ->
                                    put(webSearchStepsToJson(steps))
                                }
                            },
                        )
                    }
                    if (message.variants.isNotEmpty()) {
                        put(
                            "variants",
                            JSONArray().apply { message.variants.forEach { put(it) } },
                        )
                        put("variantIndex", message.variantIndex)
                    }
                    if (message.attachments.isNotEmpty()) {
                        put(
                            "attachments",
                            attachmentsToJson(message.attachments),
                        )
                    }
                    if (message.attachmentVariants.isNotEmpty()) {
                        put(
                            "attachmentVariants",
                            JSONArray().apply {
                                message.attachmentVariants.forEach { variant ->
                                    put(attachmentsToJson(variant))
                                }
                            },
                        )
                    }
                },
            )
        }
        messageFile(chatId).writeText(array.toString())
    }

    private fun messageFile(chatId: String): File = File(root, "$chatId.json")

    private fun chatToJson(chat: Chat): JSONObject =
        JSONObject().apply {
            put("id", chat.id)
            put("title", chat.title)
            put("createdAt", chat.createdAt)
            put("updatedAt", chat.updatedAt)
            put("preview", chat.preview)
            put("modelId", chat.modelId)
            put("modelName", chat.modelName)
            put("pinned", chat.pinned)
            put("archived", chat.archived)
            put("projectId", chat.projectId)
            put("systemMessage", chat.systemMessage)
        }

    private fun chatFromJson(obj: JSONObject): Chat =
        Chat(
            id = obj.optString("id"),
            title = obj.optString("title").ifBlank { "New chat" },
            createdAt = obj.optLong("createdAt"),
            updatedAt = obj.optLong("updatedAt"),
            preview = obj.optString("preview"),
            modelId = obj.optString("modelId").ifBlank { null },
            modelName = obj.optString("modelName").ifBlank { null },
            pinned = obj.optBoolean("pinned", false),
            archived = obj.optBoolean("archived", false),
            projectId = obj.optString("projectId").ifBlank { null },
            systemMessage = obj.optString("systemMessage"),
        )

    private fun projectToJson(project: Project): JSONObject =
        JSONObject().apply {
            put("id", project.id)
            put("name", project.name)
            put("description", project.description)
            put("createdAt", project.createdAt)
        }

    private fun projectFromJson(obj: JSONObject): Project =
        Project(
            id = obj.optString("id"),
            name = obj.optString("name"),
            description = obj.optString("description"),
            createdAt = obj.optLong("createdAt"),
        )

    private fun parseVariants(array: JSONArray?): List<String> {
        if (array == null || array.length() == 0) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val text = array.optString(i)
                if (text.isNotBlank()) add(text)
            }
        }
    }

    private fun parseStringList(array: JSONArray?): List<String> {
        if (array == null || array.length() == 0) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                add(array.optString(i))
            }
        }
    }

    private fun parseLongNullableList(array: JSONArray?): List<Long?> {
        if (array == null || array.length() == 0) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                add(
                    if (array.isNull(i)) {
                        null
                    } else {
                        array.optLong(i)
                    },
                )
            }
        }
    }

    private fun parseAttachments(array: JSONArray?): List<ChatAttachment> {
        if (array == null || array.length() == 0) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val path = obj.optString("path")
                if (path.isBlank()) continue
                add(
                    ChatAttachment(
                        id = obj.optString("id").ifBlank { path },
                        path = path,
                        mimeType = obj.optString("mimeType").ifBlank { null },
                        displayName = obj.optString("displayName").ifBlank { "file" },
                    ),
                )
            }
        }
    }

    private fun parseAttachmentVariants(array: JSONArray?): List<List<ChatAttachment>> {
        if (array == null || array.length() == 0) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                add(parseAttachments(array.optJSONArray(i)))
            }
        }
    }

    private fun attachmentsToJson(attachments: List<ChatAttachment>): JSONArray =
        JSONArray().apply {
            attachments.forEach { attachment ->
                put(
                    JSONObject().apply {
                        put("id", attachment.id)
                        put("path", attachment.path)
                        put("mimeType", attachment.mimeType)
                        put("displayName", attachment.displayName)
                    },
                )
            }
        }

    private fun webSearchStepsToJson(steps: List<WebSearchStep>): JSONArray =
        JSONArray().apply {
            steps.forEach { step ->
                put(
                    JSONObject().apply {
                        put("id", step.id)
                        put("query", step.query)
                        put("isSearching", step.isSearching)
                        if (step.citations.isNotEmpty()) {
                            put(
                                "citations",
                                JSONArray().apply {
                                    step.citations.forEach { citation ->
                                        put(
                                            JSONObject().apply {
                                                put("url", citation.url)
                                                put("title", citation.title)
                                                if (citation.description.isNotBlank()) {
                                                    put("description", citation.description)
                                                }
                                                if (citation.publishedDate.isNotBlank()) {
                                                    put("publishedDate", citation.publishedDate)
                                                }
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    },
                )
            }
        }

    private fun parseWebSearchSteps(array: JSONArray?): List<WebSearchStep> {
        if (array == null || array.length() == 0) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                add(parseWebSearchStep(obj))
            }
        }
    }

    private fun parseWebSearchStepVariants(
        array: JSONArray?,
    ): List<List<WebSearchStep>> {
        if (array == null || array.length() == 0) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                add(parseWebSearchSteps(array.optJSONArray(i)))
            }
        }
    }

    private fun parseWebSearchStep(obj: JSONObject): WebSearchStep {
        val citationsArray = obj.optJSONArray("citations")
        val citations = buildList {
            if (citationsArray != null) {
                for (i in 0 until citationsArray.length()) {
                    val citation = citationsArray.optJSONObject(i) ?: continue
                    val url = citation.optString("url")
                    if (url.isBlank()) continue
                    add(
                        WebSearchCitation(
                            url = url,
                            title = citation.optString("title").ifBlank { url },
                            description = citation.optString("description")
                                .ifBlank { citation.optString("content") },
                            publishedDate = citation.optString("publishedDate")
                                .ifBlank { citation.optString("published_date") },
                        ),
                    )
                }
            }
        }
        return WebSearchStep(
            id = obj.optString("id").ifBlank { "search_legacy" },
            query = obj.optString("query"),
            citations = citations,
            isSearching = obj.optBoolean("isSearching", false),
        )
    }

    private companion object {
        const val PREFS = "convo_chat_meta"
        const val KEY_ACTIVE = "active_chat_id"
    }
}
