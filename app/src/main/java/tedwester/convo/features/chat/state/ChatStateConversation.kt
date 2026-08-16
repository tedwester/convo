package tedwester.convo.features.chat.state

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tedwester.convo.core.network.model.OpenRouterModel
import tedwester.convo.features.chat.model.ConversationFrame
import tedwester.convo.ui.chat.modals.ModelFilterState

/** Apply persisted defaults when starting a new chat (default model from KeyStorage). */
internal fun ChatState.applyNewChatDefaultsImpl() {
    isSearchEnabled = false
    val stored = keyStorage.getModel() ?: return
    val cached = api.findCachedModel(stored.id)
    if (cached != null) {
        bindSelectedModel(cached)
        return
    }
    bindSelectedModel(
        OpenRouterModel(
            id = stored.id,
            name = stored.name,
        ),
    )
    scope.launch {
        refreshSelectedModelMetadata()
    }  
}

internal fun ChatState.openChatImpl(chatId: String) {
    completions.markChatOpened(chatId)
    if (chatId == currentChatId) {
        reconcileWithController(chatId)
        return
    }
    completions.viewingChatId = chatId
    scope.launch {
        val live = completions.liveMessages(chatId)
        val loaded = withContext(Dispatchers.IO) {
            val listed = repository.listChats()
            val chat = listed.find { it.id == chatId } ?: return@withContext null
            repository.setActiveChatId(chat.id)
            val msgs = live ?: repository.loadMessages(chat.id)
            Triple(listed, chat, msgs)
        } ?: return@launch
        val outgoingSession = conversationSessionId
        freezeOutgoingConversationFrame()
        applyLoadedChat(loaded.first, loaded.second, loaded.third)
        finishConversationTransition(
            ConversationFrame(
                sessionId = outgoingSession + 1,
                chatId = currentChatId,
                messages = messages.toList(),
                messageListRevision = messageListRevision,
            ),
        )
        activeCompletionSessionId = completions.activeSessionId(chatId) ?: 0L
        syncRunningFlag()
    }
}

/**
 * Start a fresh conversation in memory only. Nothing is written to history
 * until the first real turn ([ensureCurrentChat] on send).
 * In-flight completions on other chats keep running.
 */
internal fun ChatState.newChatImpl() {
    completions.viewingChatId = null
    if (messages.isEmpty() && currentChatId == null) {
        applyNewChatDefaultsImpl()
        input = ""
        clearPendingAttachments()
        systemMessage = ""
        modelSelectorFilters = ModelFilterState()
        isRunning = false
        return
    }
    val orphanEmptyId = currentChatId.takeIf { messages.isEmpty() }
    val outgoingSession = conversationSessionId
    freezeOutgoingConversationFrame()
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
    applyNewChatDefaultsImpl()
    finishConversationTransition(
        ConversationFrame(
            sessionId = outgoingSession + 1,
            chatId = null,
            messages = emptyList(),
            messageListRevision = messageListRevision,
        ),
    )
    scope.launch {
        withContext(Dispatchers.IO) {
            orphanEmptyId?.let { repository.deleteChat(it) }
            repository.setActiveChatId(null)
        }
        chats = withContext(Dispatchers.IO) { repository.listChats() }
    }
}

internal fun ChatState.deleteChatImpl(chatId: String) {
    completions.discard(chatId)
    scope.launch {
        withContext(Dispatchers.IO) {
            repository.deleteChat(chatId)
            chatModelFilterStore.delete(chatId)
        }
        val listed = withContext(Dispatchers.IO) { repository.listChats() }
        chats = listed
        if (chatId == currentChatId) {
            clearCurrentChat()
        }
    }
}

internal fun ChatState.pinChatImpl(chatId: String, pinned: Boolean = true) {
    scope.launch {
        withContext(Dispatchers.IO) { repository.setChatPinned(chatId, pinned) }
        chats = withContext(Dispatchers.IO) { repository.listChats() }
    }
}

internal fun ChatState.archiveChatImpl(chatId: String, archived: Boolean = true) {
    scope.launch {
        withContext(Dispatchers.IO) { repository.setChatArchived(chatId, archived) }
        val listed = withContext(Dispatchers.IO) { repository.listChats() }
        chats = listed
        if (archived && chatId == currentChatId) {
            clearCurrentChat()
        }
    }
}

internal fun ChatState.renameChatImpl(chatId: String, title: String) {
    val trimmed = title.trim().ifBlank { "New chat" }
    scope.launch {
        withContext(Dispatchers.IO) { repository.renameChat(chatId, trimmed) }
        chats = withContext(Dispatchers.IO) { repository.listChats() }
        if (chatId == currentChatId) {
            currentTitle = trimmed
        }
    }
}
