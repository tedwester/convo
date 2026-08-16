package tedwester.convo.features.chat.state

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tedwester.convo.core.network.OpenRouterApi
import tedwester.convo.core.network.model.OpenRouterModel
import tedwester.convo.features.chat.data.ChatCompletionController
import tedwester.convo.features.chat.data.ChatModelFilterStore
import tedwester.convo.features.chat.data.ChatRepository
import tedwester.convo.core.security.KeyStorage
import tedwester.convo.features.chat.data.ChatRunStatus
import tedwester.convo.features.chat.data.ApiPreferencesStore
import tedwester.convo.features.chat.data.ReasoningPreferencesStore
import tedwester.convo.features.chat.data.SearchPreferencesStore
import tedwester.convo.features.chat.data.TtsVoicePreferencesStore
import tedwester.convo.features.chat.data.VoicePreferencesStore
import tedwester.convo.features.chat.model.Chat
import tedwester.convo.features.chat.model.ChatAttachment
import tedwester.convo.features.chat.model.ChatMessage
import tedwester.convo.features.chat.model.ConversationFrame
import tedwester.convo.features.chat.model.Project
import tedwester.convo.features.chat.model.ReasoningEffort
import tedwester.convo.features.chat.model.ReasoningPreferences
import tedwester.convo.ui.chat.modals.ModelFilterState
import kotlin.math.min

/**
 * Holds all mutable UI state for the chat screen and drives the conversation
 * with OpenRouter, persisting chats locally via [ChatRepository].
 *
 * Streaming and special (TTS / image / video) jobs are owned by
 * [ChatCompletionController] so they survive leaving a chat, opening the
 * list, or locking the app.
 */
@Stable
class ChatState(
    internal val apiKey: String,
    internal val api: OpenRouterApi,
    internal val repository: ChatRepository,
    internal val chatModelFilterStore: ChatModelFilterStore,
    internal val completions: ChatCompletionController,
    internal val reasoningStore: ReasoningPreferencesStore,
    internal val voiceStore: VoicePreferencesStore,
    internal val ttsVoiceStore: TtsVoicePreferencesStore,
    internal val searchStore: SearchPreferencesStore,
    internal val apiPreferencesStore: ApiPreferencesStore,
    internal val keyStorage: KeyStorage,
    internal val scope: CoroutineScope,
    internal val context: Context,
    initialModel: OpenRouterModel? = null,
) {
    internal var currentId = 0L

    val messages = mutableStateListOf<ChatMessage>()

    var chats by mutableStateOf<List<Chat>>(emptyList())
        internal set

    var projects by mutableStateOf<List<Project>>(emptyList())
        internal set

    var currentChatId by mutableStateOf<String?>(null)
        internal set

    var currentTitle by mutableStateOf("New chat")
        internal set

    var selectedModel by mutableStateOf(initialModel?.let { enrichModel(it) })
        internal set

    var input by mutableStateOf("")
        internal set

    val pendingAttachments = mutableStateListOf<ChatAttachment>()

    var isRunning by mutableStateOf(false)
        internal set

    var isSearchEnabled by mutableStateOf(false)
        internal set

    var reasoningPreferences by mutableStateOf(
        loadReasoningPreferences(selectedModel?.id),
    )
        internal set

    /** Selected TTS voice id for the current model; null means model default. */
    var selectedTtsVoice by mutableStateOf(
        selectedModel?.let { loadTtsVoice(it) },
    )
        internal set

    var systemMessage by mutableStateOf("")
        internal set

    /** Model picker filter badges for the current chat (persisted per chat id). */
    var modelSelectorFilters by mutableStateOf(ModelFilterState())
        internal set

    var isReady by mutableStateOf(false)
        internal set

    /** Running / completed-unread markers for the chat list. */
    var runStatuses by mutableStateOf<Map<String, ChatRunStatus>>(emptyMap())
        internal set

    /**
     * Bumped whenever turns are removed from the live list (regenerate truncate,
     * chat clear, etc.). UI uses this to drop overlays and skip broken exit animations.
     */
    var messageListRevision by mutableStateOf(0)
        internal set

    /** Drives cross-fade transitions when opening, switching, or clearing chats. */
    var conversationSessionId by mutableStateOf(0)
        internal set

    internal val conversationFrames = mutableMapOf(
        0 to ConversationFrame(
            sessionId = 0,
            chatId = null,
            messages = emptyList(),
            messageListRevision = 0,
        ),
    )

    /** Matches [ChatCompletionController] session snapshots applied to [messages]. */
    internal var activeCompletionSessionId: Long = 0L

    internal fun bumpMessageListRevision() {
        messageListRevision++
    }

    fun frameFor(sessionId: Int): ConversationFrame =
        conversationFrames[sessionId] ?: ConversationFrame(
            sessionId = sessionId,
            chatId = null,
            messages = emptyList(),
            messageListRevision = 0,
        )

    fun pruneConversationFrames() {
        val keepFrom = (conversationSessionId - 1).coerceAtLeast(0)
        conversationFrames.keys.retainAll { it >= keepFrom }
    }

    internal fun snapshotConversationFrame(): ConversationFrame = ConversationFrame(
        sessionId = conversationSessionId,
        chatId = currentChatId,
        messages = messages.toList(),
        messageListRevision = messageListRevision,
    )

    internal fun freezeOutgoingConversationFrame() {
        val outgoing = snapshotConversationFrame()
        conversationFrames[outgoing.sessionId] = outgoing
    }

    internal fun finishConversationTransition(incoming: ConversationFrame) {
        conversationSessionId = incoming.sessionId
        conversationFrames[incoming.sessionId] = incoming
    }

    fun addAttachment(attachment: ChatAttachment) {
        if (pendingAttachments.any { it.id == attachment.id }) return
        pendingAttachments += attachment
    }

    fun removeAttachment(id: String) {
        pendingAttachments.removeAll { it.id == id }
    }

    internal fun clearPendingAttachments() {
        pendingAttachments.clear()
    }

    /** Load chat and project lists from disk without opening a conversation. */
    fun bootstrap() {
        completions.viewingChatId = currentChatId
        scope.launch {
            chats = withContext(Dispatchers.IO) { repository.listChats() }
            projects = withContext(Dispatchers.IO) { repository.listProjects() }
            runStatuses = completions.statuses.value
            isReady = true
            refreshSelectedModelMetadata()
        }
    }

    suspend fun observeCompletions() {
        coroutineScope {
            launch {
                completions.statuses.collect { statuses ->
                    runStatuses = statuses
                    syncRunningFlag()
                }
            }
            launch {
                completions.updates.collect { update ->
                    if (update.chatId != currentChatId) {
                        if (!update.isRunning) {
                            refreshChatList()
                        }
                        return@collect
                    }
                    if (update.sessionId != activeCompletionSessionId) {
                        return@collect
                    }
                    replaceMessages(update.messages)
                    currentId = update.messages.maxOfOrNull { it.id } ?: currentId
                    isRunning = update.isRunning
                    if (!update.isRunning) {
                        refreshChatList()
                    }
                }
            }
        }
    }

    fun refreshChatList() {
        scope.launch {
            val listed = withContext(Dispatchers.IO) { repository.listChats() }
            projects = withContext(Dispatchers.IO) { repository.listProjects() }
            chats = listed
            val id = currentChatId
            if (id != null) {
                listed.find { it.id == id }?.let { currentTitle = it.title }
            }
        }
    }

    internal suspend fun ensureCurrentChat() {
        if (currentChatId != null) return
        val created = withContext(Dispatchers.IO) {
            repository.createChat(
                modelId = selectedModel?.id,
                modelName = selectedModel?.name,
                systemMessage = systemMessage,
            )
        }
        currentChatId = created.id
        currentTitle = created.title
        completions.viewingChatId = created.id
        chatModelFilterStore.save(created.id, modelSelectorFilters)
        chats = withContext(Dispatchers.IO) { repository.listChats() }
    }

    internal fun clearCurrentChat() {
        val outgoingSession = conversationSessionId
        freezeOutgoingConversationFrame()
        completions.viewingChatId = null
        messages.clear()
        bumpMessageListRevision()
        activeCompletionSessionId = 0L
        currentId = 0L
        input = ""
        clearPendingAttachments()
        systemMessage = ""
        modelSelectorFilters = ModelFilterState()
        currentChatId = null
        currentTitle = "New chat"
        isRunning = false
        finishConversationTransition(
            ConversationFrame(
                sessionId = outgoingSession + 1,
                chatId = null,
                messages = emptyList(),
                messageListRevision = messageListRevision,
            ),
        )
        scope.launch(Dispatchers.IO) {
            repository.setActiveChatId(null)
        }
    }

    /**
     * Replace [messages] without creating a transient empty list when both sides
     * have rows. This keeps chat-switch transitions visually stable.
     */
    internal fun replaceMessages(next: List<ChatMessage>) {
        if (messages.isEmpty() || next.isEmpty()) {
            if (messages.isNotEmpty()) {
                bumpMessageListRevision()
            }
            messages.clear()
            messages.addAll(next)
            return
        }
        val overlap = min(messages.size, next.size)
        for (i in 0 until overlap) {
            if (messages[i] != next[i]) {
                messages[i] = next[i]
            }
        }
        if (messages.size > next.size) {
            messages.removeRange(next.size, messages.size)
            bumpMessageListRevision()
        } else if (next.size > messages.size) {
            messages.addAll(next.subList(messages.size, next.size))
        }
    }

    internal fun applyLoadedChat(
        listed: List<Chat>,
        chat: Chat,
        loadedMessages: List<ChatMessage>,
    ) {
        chats = listed
        currentChatId = chat.id
        currentTitle = chat.title
        systemMessage = chat.systemMessage
        modelSelectorFilters = chatModelFilterStore.load(chat.id)
        replaceMessages(loadedMessages)
        currentId = loadedMessages.maxOfOrNull { it.id } ?: 0L
        input = ""
        clearPendingAttachments()
        if (chat.modelId != null && selectedModel?.id != chat.modelId) {
            applySelectedModel(
                OpenRouterModel(
                    id = chat.modelId,
                    name = chat.modelName ?: chat.modelId,
                ),
            )
        }
    }

    internal fun syncRunningFlag() {
        isRunning = completions.isRunning(currentChatId)
    }

    /**
     * Re-sync [messages] with the controller's live session for [chatId] (or
     * disk if no session is active), then re-align [activeCompletionSessionId]
     * and [isRunning]. Called when re-entering a conversation so a reply that
     * finished while the user was away (whose final [CompletionUpdate] was
     * dropped by [observeCompletions] because the chat wasn't current) is not
     * left as a stale empty streaming placeholder. Applies uniformly to text,
     * image, video, and audio/speech generation because they all flow through
     * [ChatCompletionController].
     */
    internal fun reconcileWithController(chatId: String?) {
        if (chatId == null) return
        if (chatId != currentChatId) return
        scope.launch {
            val live = completions.liveMessages(chatId)
            val source = live ?: withContext(Dispatchers.IO) {
                repository.loadMessages(chatId)
            }
            val sessionId = completions.activeSessionId(chatId) ?: 0L
            withContext(Dispatchers.Main) {
                if (currentChatId != chatId) return@withContext
                replaceMessages(source)
                currentId = source.maxOfOrNull { it.id } ?: currentId
                activeCompletionSessionId = sessionId
                syncRunningFlag()
            }
        }
    }

    /** Reconcile the currently open chat with the controller (no-op if none). */
    fun reconcile() = reconcileWithController(currentChatId)

    internal fun persist(bumpRecency: Boolean = false) {
        val id = currentChatId ?: return
        val snapshot = messages.toList()
        val model = selectedModel
        val prompt = systemMessage
        val activityAt = if (bumpRecency) System.currentTimeMillis() else null
        scope.launch(Dispatchers.IO) {
            val updated = repository.saveConversation(
                chatId = id,
                messages = snapshot,
                modelId = model?.id,
                modelName = model?.name,
                systemMessage = prompt,
                activityAt = activityAt,
            )
            val listed = repository.listChats()
            withContext(Dispatchers.Main) {
                chats = listed
                if (updated != null) {
                    currentTitle = updated.title
                }
            }
        }
    }

    fun openChat(chatId: String) = openChatImpl(chatId)
    fun newChat() = newChatImpl()
    fun deleteChat(chatId: String) = deleteChatImpl(chatId)
    fun pinChat(chatId: String, pinned: Boolean = true) = pinChatImpl(chatId, pinned)
    fun archiveChat(chatId: String, archived: Boolean = true) = archiveChatImpl(chatId, archived)
    fun renameChat(chatId: String, title: String) = renameChatImpl(chatId, title)
    fun createProject(name: String, description: String) = createProjectImpl(name, description)
    fun deleteProject(projectId: String) = deleteProjectImpl(projectId)
    fun projectNameExists(name: String): Boolean = projectNameExistsImpl(name)
    fun assignChatToProject(chatId: String, projectId: String?) =
        assignChatToProjectImpl(chatId, projectId)
    fun onInputChange(value: String) = onInputChangeImpl(value)
    fun toggleSearch() = toggleSearchImpl()
    fun toggleReasoning() = toggleReasoningImpl()
    fun setReasoningEffort(effort: ReasoningEffort) =
        setReasoningEffortImpl(effort)
    fun setStreamThinking(enabled: Boolean) = setStreamThinkingImpl(enabled)
    fun updateReasoningPreferences(prefs: ReasoningPreferences) =
        updateReasoningPreferencesImpl(prefs)
    fun setSelectedTtsVoice(voiceId: String?) = setSelectedTtsVoiceImpl(voiceId)
    fun saveSystemMessage(value: String) = saveSystemMessageImpl(value)
    fun selectModel(model: OpenRouterModel) = selectModelImpl(model)
    fun updateModelSelectorFilters(state: ModelFilterState) =
        updateModelSelectorFiltersImpl(state)
    fun send() = sendImpl()
    suspend fun sendVoice(
        audioBytes: ByteArray,
        format: String,
        transcript: String?,
    ) = sendVoiceImpl(audioBytes, format, transcript)
    fun sendTranscriptionVoice(audioBytes: ByteArray, format: String) =
        sendTranscriptionVoiceImpl(audioBytes, format)
    suspend fun transcribeRecording(audioBytes: ByteArray, format: String): String? =
        transcribeRecordingImpl(audioBytes, format)
    fun interruptInFlight() = interruptInFlightImpl()
    fun markVoiceAutoPlayed(messageId: Long) = markVoiceAutoPlayedImpl(messageId)
    fun stop() = stopImpl()
    fun regenerate(messageId: Long) = regenerateImpl(messageId)
    fun selectVariant(messageId: Long, delta: Int) = selectVariantImpl(messageId, delta)
}
