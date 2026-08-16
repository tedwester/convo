package tedwester.convo.features.chat.data

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tedwester.convo.ConvoApp
import tedwester.convo.core.network.OpenRouterApi
import tedwester.convo.core.network.model.ChatMessageDto
import tedwester.convo.core.network.model.OpenRouterModel
import tedwester.convo.core.network.model.ReasoningRequest
import tedwester.convo.features.chat.model.ChatAttachment
import tedwester.convo.features.chat.model.ChatMessage
import tedwester.convo.features.chat.model.EMPTY_RESPONSE_TEXT
import tedwester.convo.features.chat.model.STOPPED_RESPONSE_TEXT
import tedwester.convo.features.chat.model.WebSearchStep
import tedwester.convo.features.chat.model.asStopped
import java.util.concurrent.ConcurrentHashMap

enum class ChatRunStatus {
    Running,
    CompletedUnread,
}

data class CompletionUpdate(
    val chatId: String,
    val messages: List<ChatMessage>,
    val isRunning: Boolean,
    val sessionId: Long,
)

data class CompletionRequest(
    val chatId: String,
    val apiKey: String,
    val model: OpenRouterModel,
    val history: List<ChatMessageDto>,
    val messages: List<ChatMessage>,
    val assistantId: Long,
    val appendVariant: Boolean,
    val enableWebSearch: Boolean,
    val systemMessage: String,
    val reasoning: ReasoningRequest? = null,
    val maxTokens: Int? = null,
)

data class SpecialGenerationRequest(
    val chatId: String,
    val model: OpenRouterModel,
    val messages: List<ChatMessage>,
    val assistantId: Long,
    val appendVariant: Boolean,
    val systemMessage: String,
)

data class SpecialGenerationOutcome(
    val content: String,
    val attachments: List<ChatAttachment> = emptyList(),
    val success: Boolean = true,
)

class ChatCompletionController(
    private val app: ConvoApp,
    private val api: OpenRouterApi,
    private val repository: ChatRepository,
    private val scope: CoroutineScope,
) {
    private val jobs = ConcurrentHashMap<String, Job>()
    private val sessions = ConcurrentHashMap<String, Session>()
    private val sessionMutex = Mutex()

    private val _statuses = MutableStateFlow<Map<String, ChatRunStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, ChatRunStatus>> = _statuses.asStateFlow()

    private val _updates = MutableSharedFlow<CompletionUpdate>(
        extraBufferCapacity = 64,
        replay = 0,
    )
    val updates: SharedFlow<CompletionUpdate> = _updates.asSharedFlow()

    @Volatile
    var viewingChatId: String? = null

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START, Lifecycle.Event.ON_STOP -> refreshForegroundService()
                    else -> Unit
                }
            },
        )
    }

    fun isRunning(chatId: String?): Boolean =
        chatId != null && _statuses.value[chatId] == ChatRunStatus.Running

    fun liveMessages(chatId: String): List<ChatMessage>? =
        sessions[chatId]?.messages?.toList()

    fun activeSessionId(chatId: String): Long? = sessions[chatId]?.sessionId

    fun markChatOpened(chatId: String) {
        _statuses.update { current ->
            if (current[chatId] == ChatRunStatus.CompletedUnread) {
                current - chatId
            } else {
                current
            }
        }
        ChatNotifications.cancelReplyReady(app, chatId)
    }

    fun markRunFinished(chatId: String, userStopped: Boolean = false, success: Boolean = true) {
        if (_statuses.value[chatId] != ChatRunStatus.Running) return
        val viewing = viewingChatId == chatId
        val appForeground = isAppForeground()
        if (userStopped || (viewing && appForeground)) {
            clearRunningStatus(chatId)
        } else {
            setStatus(chatId, ChatRunStatus.CompletedUnread)
            if (!appForeground && success) {
                scope.launch {
                    val title = withContext(Dispatchers.IO) {
                        repository.listChats().find { it.id == chatId }?.title
                    }.orEmpty()
                    ChatNotifications.notifyReplyReady(app, chatId, title)
                }
            }
        }
        refreshForegroundService()
    }

    fun start(request: CompletionRequest): Long {
        val session = replaceSession(
            chatId = request.chatId,
            assistantId = request.assistantId,
            appendVariant = request.appendVariant,
            model = request.model,
            systemMessage = request.systemMessage,
            messages = request.messages,
        )
        trackJob(request.chatId, scope.launch { runStream(request, session) })
        return session.sessionId
    }

    fun startSpecial(
        request: SpecialGenerationRequest,
        work: suspend (updateStatus: suspend (String) -> Unit) -> SpecialGenerationOutcome,
    ): Long {
        val session = replaceSession(
            chatId = request.chatId,
            assistantId = request.assistantId,
            appendVariant = request.appendVariant,
            model = request.model,
            systemMessage = request.systemMessage,
            messages = request.messages,
        )
        trackJob(request.chatId, scope.launch { runSpecial(session, work) })
        return session.sessionId
    }

    private fun replaceSession(
        chatId: String,
        assistantId: Long,
        appendVariant: Boolean,
        model: OpenRouterModel,
        systemMessage: String,
        messages: List<ChatMessage>,
    ): Session {
        val prior = jobs.remove(chatId)
        prior?.cancel()
        val session = Session(
            sessionId = NEXT_SESSION_ID.incrementAndGet(),
            chatId = chatId,
            assistantId = assistantId,
            appendVariant = appendVariant,
            modelId = model.id,
            modelName = model.name,
            systemMessage = systemMessage,
            messages = messages.toMutableList(),
            cancelFinalizes = true,
        )
        sessions[chatId] = session
        setStatus(chatId, ChatRunStatus.Running)
        emitUpdate(session, isRunning = true)
        refreshForegroundService()
        return session
    }

    private fun trackJob(chatId: String, job: Job) {
        jobs[chatId] = job
        job.invokeOnCompletion {
            jobs.remove(chatId, job)
        }
    }

    fun stop(chatId: String) {
        val session = sessions[chatId]
        if (session != null) {
            session.userStopped = true
            session.cancelFinalizes = false
        }
        jobs.remove(chatId)?.cancel()
        if (session == null) {
            clearRunningStatus(chatId)
            refreshForegroundService()
            return
        }
        scope.launch {
            sessionMutex.withLock {
                val index = session.messages.indexOfFirst { it.id == session.assistantId }
                if (index >= 0) {
                    session.messages[index] = session.messages[index].asStopped()
                }
            }
            if (sessions[chatId] === session) {
                onCompleted(session, success = true)
            }
        }
    }

    fun cancelSilent(chatId: String) {
        val session = sessions[chatId]
        if (session != null) {
            session.cancelFinalizes = false
        }
        jobs.remove(chatId)?.cancel()
        sessions.remove(chatId)
        clearRunningStatus(chatId)
        refreshForegroundService()
    }

    fun discard(chatId: String) {
        cancelSilent(chatId)
        _statuses.update { it - chatId }
        ChatNotifications.cancelReplyReady(app, chatId)
        refreshForegroundService()
    }

    private suspend fun runStream(request: CompletionRequest, session: Session) {
        val contentBuffer = StringBuilder()
        val reasoningBuffer = StringBuilder()
        val startedAt = SystemClock.elapsedRealtime()
        var thoughtDurationMs: Long? = null
        var lastUiEmitMs = 0L
        var lastPersistMs = 0L

        fun captureThoughtDurationIfNeeded() {
            if (thoughtDurationMs == null) {
                thoughtDurationMs = SystemClock.elapsedRealtime() - startedAt
            }
        }

        fun resolveThoughtDuration(): Long? =
            thoughtDurationMs ?: if (reasoningBuffer.isNotBlank()) {
                SystemClock.elapsedRealtime() - startedAt
            } else {
                null
            }

        sessionMutex.withLock {
            markThinkingStarted(session, startedAt)
        }
        emitUpdate(session, isRunning = true)

        try {
            api.chatCompletionStream(
                apiKey = request.apiKey,
                model = request.model.id,
                messages = request.history,
                enableWebSearch = request.enableWebSearch,
                reasoning = request.reasoning,
                maxTokens = request.maxTokens,
            ).collect { delta ->
                delta.content?.let { chunk ->
                    if (contentBuffer.isEmpty() && chunk.isNotBlank()) {
                        captureThoughtDurationIfNeeded()
                    }
                    contentBuffer.append(chunk)
                }
                delta.reasoning?.let { reasoningBuffer.append(it) }
                val now = SystemClock.elapsedRealtime()
                if (now - lastUiEmitMs >= 48L) {
                    lastUiEmitMs = now
                    sessionMutex.withLock {
                        updateAssistant(
                            session = session,
                            content = contentBuffer.toString(),
                            reasoning = reasoningBuffer.toString(),
                            isStreaming = true,
                            thoughtDurationMs = thoughtDurationMs,
                            webSearchSteps = delta.webSearchSteps,
                        )
                    }
                    emitUpdate(session, isRunning = true)
                }
                if (now - lastPersistMs >= 2_000L) {
                    lastPersistMs = now
                    persistSession(session)
                }
                delta.webSearchSteps?.let { steps ->
                    sessionMutex.withLock {
                        updateAssistant(
                            session = session,
                            content = contentBuffer.toString(),
                            reasoning = reasoningBuffer.toString(),
                            isStreaming = true,
                            thoughtDurationMs = thoughtDurationMs,
                            webSearchSteps = steps,
                        )
                    }
                    emitUpdate(session, isRunning = true)
                }
            }
            currentCoroutineContext().ensureActive()
            if (session.userStopped) return
            val finalText = contentBuffer.toString().ifBlank { EMPTY_RESPONSE_TEXT }
            val finalReasoning = reasoningBuffer.toString()
            sessionMutex.withLock {
                if (session.userStopped) return@withLock
                finalizeAssistant(
                    session = session,
                    text = finalText,
                    reasoning = finalReasoning,
                    thoughtDurationMs = resolveThoughtDuration(),
                )
            }
            if (!session.userStopped) {
                onCompleted(session, success = true)
            }
        } catch (e: CancellationException) {
            if (session.userStopped) {
                throw e
            }
            if (session.cancelFinalizes) {
                withContext(NonCancellable) {
                    val finalText = contentBuffer.toString().ifBlank { STOPPED_RESPONSE_TEXT }
                    val finalReasoning = reasoningBuffer.toString()
                    sessionMutex.withLock {
                        finalizeAssistant(
                            session = session,
                            text = finalText,
                            reasoning = finalReasoning,
                            thoughtDurationMs = resolveThoughtDuration(),
                            stoppedWhileThinking = finalText == STOPPED_RESPONSE_TEXT &&
                                request.reasoning?.exclude == false,
                        )
                    }
                    onCompleted(session, success = true)
                }
            } else {
                withContext(NonCancellable) {
                    sessions.remove(session.chatId)
                    clearRunningStatus(session.chatId)
                    refreshForegroundService()
                }
            }
            throw e
        } catch (e: Exception) {
            if (session.userStopped) return
            val finalText = contentBuffer.toString().ifBlank {
                "⚠ ${e.message ?: "Something went wrong"}"
            }
            val finalReasoning = reasoningBuffer.toString()
            sessionMutex.withLock {
                if (session.userStopped) return@withLock
                finalizeAssistant(
                    session = session,
                    text = finalText,
                    reasoning = finalReasoning,
                    thoughtDurationMs = resolveThoughtDuration(),
                )
            }
            if (!session.userStopped) {
                onCompleted(session, success = false)
            }
        }
    }

    private suspend fun onCompleted(session: Session, success: Boolean) {
        persistSession(session)
        sessions.remove(session.chatId)
        emitUpdate(session, isRunning = false)
        markRunFinished(session.chatId, userStopped = session.userStopped, success = success)
    }

    private suspend fun runSpecial(
        session: Session,
        work: suspend (updateStatus: suspend (String) -> Unit) -> SpecialGenerationOutcome,
    ) {
        val updateStatus: suspend (String) -> Unit = { label ->
            if (!session.userStopped) {
                sessionMutex.withLock {
                    if (session.userStopped) return@withLock
                    val index = session.messages.indexOfFirst { it.id == session.assistantId }
                    if (index >= 0) {
                        session.messages[index] = session.messages[index].copy(statusLabel = label)
                    }
                }
                if (!session.userStopped) {
                    emitUpdate(session, isRunning = true)
                }
            }
        }
        try {
            val outcome = work(updateStatus)
            currentCoroutineContext().ensureActive()
            if (session.userStopped) return
            sessionMutex.withLock {
                if (session.userStopped) return@withLock
                finalizeSpecialAssistant(
                    session = session,
                    content = outcome.content,
                    attachments = outcome.attachments,
                )
            }
            if (!session.userStopped) {
                onCompleted(session, success = outcome.success)
            }
        } catch (e: CancellationException) {
            if (session.userStopped) {
                throw e
            }
            if (session.cancelFinalizes) {
                withContext(NonCancellable) {
                    sessionMutex.withLock {
                        session.messages.indexOfFirst { it.id == session.assistantId }
                            .takeIf { it >= 0 }
                            ?.let { index ->
                                session.messages[index] = session.messages[index].asStopped()
                            }
                    }
                    onCompleted(session, success = true)
                }
            } else {
                withContext(NonCancellable) {
                    sessions.remove(session.chatId)
                    clearRunningStatus(session.chatId)
                    refreshForegroundService()
                }
            }
            throw e
        } catch (e: Exception) {
            if (session.userStopped) return
            sessionMutex.withLock {
                if (session.userStopped) return@withLock
                finalizeSpecialAssistant(
                    session = session,
                    content = "⚠ ${e.message ?: "Generation failed"}",
                    attachments = emptyList(),
                )
            }
            if (!session.userStopped) {
                onCompleted(session, success = false)
            }
        }
    }

    private fun finalizeSpecialAssistant(
        session: Session,
        content: String,
        attachments: List<ChatAttachment>,
    ) {
        val index = session.messages.indexOfFirst { it.id == session.assistantId }
        if (index < 0) return
        val existing = session.messages[index]
        val resolvedContent = if (content.isBlank() && attachments.isEmpty()) {
            EMPTY_RESPONSE_TEXT
        } else {
            content
        }
        val priorVariants = existing.savedVariants()
        val priorAttachmentVariants = existing.savedAttachmentVariants()
        val (nextVariants, nextAttachmentVariants, nextIndex) =
            if (session.appendVariant && priorVariants.isNotEmpty()) {
                Triple(
                    priorVariants + resolvedContent,
                    priorAttachmentVariants + listOf(attachments),
                    priorVariants.size,
                )
            } else {
                Triple(listOf(resolvedContent), listOf(attachments), 0)
            }
        session.messages[index] = existing.copy(
            content = resolvedContent,
            attachments = attachments,
            isStreaming = false,
            statusLabel = null,
            variants = nextVariants,
            attachmentVariants = nextAttachmentVariants,
            reasoning = "",
            reasoningVariants = emptyList(),
            variantIndex = nextIndex,
        )
    }

    private fun markThinkingStarted(session: Session, startedAt: Long) {
        val index = session.messages.indexOfFirst { it.id == session.assistantId }
        if (index < 0) return
        val existing = session.messages[index]
        session.messages[index] = existing.copy(thinkingStartedAtElapsed = startedAt)
    }

    private fun updateAssistant(
        session: Session,
        content: String,
        reasoning: String,
        isStreaming: Boolean,
        thoughtDurationMs: Long? = null,
        webSearchSteps: List<WebSearchStep>? = null,
    ) {
        val index = session.messages.indexOfFirst { it.id == session.assistantId }
        if (index < 0) return
        val existing = session.messages[index]
        session.messages[index] = existing.copy(
            content = content,
            reasoning = reasoning,
            isStreaming = isStreaming,
            thoughtDurationMs = thoughtDurationMs ?: existing.thoughtDurationMs,
            webSearchSteps = webSearchSteps ?: existing.webSearchSteps,
        )
    }

    private fun finalizeAssistant(
        session: Session,
        text: String,
        reasoning: String,
        thoughtDurationMs: Long? = null,
        stoppedWhileThinking: Boolean = false,
    ) {
        val index = session.messages.indexOfFirst { it.id == session.assistantId }
        if (index < 0) return
        val existing = session.messages[index]
        val prior = existing.variants
        val priorReasoning = existing.reasoningVariants
        val priorThoughtDurations = existing.thoughtDurationVariants
        val priorWebSearch = existing.webSearchStepVariants
        val nextVariants = if (session.appendVariant && prior.isNotEmpty()) {
            prior + text
        } else {
            listOf(text)
        }
        val nextReasoning = when {
            session.appendVariant && prior.isNotEmpty() -> {
                val aligned = if (priorReasoning.size == prior.size) {
                    priorReasoning
                } else {
                    prior.mapIndexed { i, _ -> priorReasoning.getOrElse(i) { "" } }
                }
                aligned + reasoning
            }
            else -> listOf(reasoning)
        }
        val nextThoughtDurations = when {
            session.appendVariant && prior.isNotEmpty() -> {
                val aligned = if (priorThoughtDurations.size == prior.size) {
                    priorThoughtDurations
                } else {
                    prior.mapIndexed { i, _ -> priorThoughtDurations.getOrNull(i) }
                }
                aligned + thoughtDurationMs
            }
            else -> listOf(thoughtDurationMs)
        }
        val finalizedWebSearch = existing.webSearchSteps.map { step ->
            step.copy(isSearching = false)
        }
        val nextWebSearch = when {
            session.appendVariant && prior.isNotEmpty() -> {
                val aligned = if (priorWebSearch.size == prior.size) {
                    priorWebSearch
                } else {
                    prior.mapIndexed { i, _ -> priorWebSearch.getOrElse(i) { emptyList() } }
                }
                aligned + listOf(finalizedWebSearch)
            }
            else -> listOf(finalizedWebSearch)
        }
        session.messages[index] = existing.copy(
            content = text,
            reasoning = reasoning,
            variants = nextVariants,
            reasoningVariants = nextReasoning,
            thoughtDurationMs = thoughtDurationMs ?: existing.thoughtDurationMs,
            thoughtDurationVariants = nextThoughtDurations,
            webSearchSteps = finalizedWebSearch,
            webSearchStepVariants = nextWebSearch,
            stoppedWhileThinking = stoppedWhileThinking,
            variantIndex = nextVariants.lastIndex,
            isStreaming = false,
            statusLabel = null,
        )
    }

    private suspend fun persistSession(session: Session) {
        val snapshot = session.messages.toList()
        withContext(Dispatchers.IO) {
            repository.saveConversation(
                chatId = session.chatId,
                messages = snapshot,
                modelId = session.modelId,
                modelName = session.modelName,
                systemMessage = session.systemMessage,
                setActive = false,
            )
        }
    }

    private fun emitUpdate(session: Session, isRunning: Boolean) {
        _updates.tryEmit(
            CompletionUpdate(
                chatId = session.chatId,
                messages = session.messages.toList(),
                isRunning = isRunning,
                sessionId = session.sessionId,
            ),
        )
    }

    companion object {
        private val NEXT_SESSION_ID = java.util.concurrent.atomic.AtomicLong(0L)
    }

    private fun setStatus(chatId: String, status: ChatRunStatus) {
        _statuses.update { it + (chatId to status) }
    }

    private fun clearRunningStatus(chatId: String) {
        _statuses.update { current ->
            if (current[chatId] == ChatRunStatus.Running) current - chatId else current
        }
    }

    private fun runningCount(): Int =
        _statuses.value.values.count { it == ChatRunStatus.Running }

    private fun refreshForegroundService() {
        val count = runningCount()
        if (count > 0 && !isAppForeground()) {
            ChatCompletionService.update(app, count)
        } else {
            ChatCompletionService.stop(app)
        }
    }

    private fun isAppForeground(): Boolean =
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private class Session(
        val sessionId: Long,
        val chatId: String,
        val assistantId: Long,
        val appendVariant: Boolean,
        val modelId: String,
        val modelName: String?,
        val systemMessage: String,
        val messages: MutableList<ChatMessage>,
        @Volatile var cancelFinalizes: Boolean,
        @Volatile var userStopped: Boolean = false,
    )
}
