package tedwester.convo.ui.chat.message

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import tedwester.convo.features.chat.model.ChatAttachment
import tedwester.convo.features.chat.model.ChatMessage
import tedwester.convo.features.chat.model.MessageAuthor
import kotlin.math.abs

private const val EndSpacerKey = "end-spacer"

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    listState: LazyListState,
    conversationKey: Any? = null,
    scrollEnabled: Boolean = true,
    isRunning: Boolean = false,
    scrollToEndToken: Int = 0,
    userBubbleAnimOnMountToken: Int = 0,
    onRegenerate: (Long) -> Unit = {},
    onResendUserMessage: (messageId: Long, editedText: String?) -> Unit = { _, _ -> },
    onStartEditUserMessage: (messageId: Long) -> Unit = {},
    onVariantSwipe: (messageId: Long, delta: Int) -> Unit = { _, _ -> },
    onViewAttachment: (ChatAttachment) -> Unit = {},
    onDismissKeyboard: () -> Unit = {},
    autoPlayVoiceReplies: Boolean = false,
    forceAutoPlayVoiceReplies: Boolean = false,
    suppressAutoPlay: Boolean = false,
    playbackStopToken: Int = 0,
    onVoiceAutoPlayStarted: (Long) -> Unit = {},
    onVoicePlaybackFinished: () -> Unit = {},
    onVoicePlaybackPaused: () -> Unit = {},
) {
    val autoPlayMessageId = if (suppressAutoPlay ||
        (!autoPlayVoiceReplies && !forceAutoPlayVoiceReplies)
    ) {
        -1L
    } else {
        val latest = messages.lastOrNull { message ->
            message.author == MessageAuthor.Assistant &&
                !message.isStreaming &&
                message.hasAudioAttachment()
        }
        if (latest == null || latest.voiceAutoPlayed) -1L else latest.id
    }

    val trailingUser = messages.lastOrNull { it.author == MessageAuthor.User }
    val trailingUserIndex = messages.indexOfLast { it.author == MessageAuthor.User }
    val trailingAssistant = messages.getOrNull(trailingUserIndex + 1)
        ?.takeIf { it.author == MessageAuthor.Assistant }
    val trailingUserId = trailingUser?.id ?: -1L
    val trailingAssistantId = trailingAssistant?.id ?: -1L
    val streamingTailId = messages.lastOrNull()
        ?.takeIf { it.author == MessageAuthor.Assistant && it.isStreaming }
        ?.id

    var lastPulsedCount by remember { mutableIntStateOf(messages.size) }
    var following by remember { mutableStateOf(false) }
    var settledToEnd by remember { mutableStateOf(false) }
    val followingRef = remember { object { var value = false } }
    val userInterruptedRef = remember { object { var value = false } }
    var userBubbleAnimId by remember { mutableLongStateOf(-1L) }
    var userBubbleAnimToken by remember { mutableIntStateOf(0) }
    var lastHandledScrollToEndToken by remember { mutableIntStateOf(scrollToEndToken) }
    var lastHandledMountAnimToken by remember { mutableIntStateOf(0) }
    var selectedPromptMessageId by remember { mutableLongStateOf(-1L) }
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var maintainingEnd by remember { mutableStateOf(false) }
    var lastUserHeightPx by remember { mutableIntStateOf(0) }
    var lastAssistantHeightPx by remember { mutableIntStateOf(0) }
    var measuredUserId by remember { mutableLongStateOf(-1L) }
    var measuredAssistantId by remember { mutableLongStateOf(-1L) }

    val density = LocalDensity.current
    val topPaddingPx = with(density) { contentPadding.calculateTopPadding().toPx() }
    val bottomPaddingPx = with(density) { contentPadding.calculateBottomPadding().toPx() }
    val spacerPx = endSpacerPx(
        viewportHeightPx = viewportHeightPx,
        topPaddingPx = topPaddingPx,
        bottomPaddingPx = bottomPaddingPx,
        lastUserHeightPx = lastUserHeightPx,
        lastAssistantHeightPx = lastAssistantHeightPx,
    ).coerceAtLeast(1f)
    val spacerDp = with(density) { spacerPx.toDp() }

    val nowStreaming = isRunning || streamingTailId != null
    val activelyFollowing = following && nowStreaming
    followingRef.value = activelyFollowing

    val detachFollow = rememberUpdatedState {
        following = false
        followingRef.value = false
        userInterruptedRef.value = true
    }
    val messagesState = rememberUpdatedState(messages)
    val isRunningState = rememberUpdatedState(isRunning)
    val trailingUserIdState = rememberUpdatedState(trailingUserId)

    SideEffect {
        if (trailingUserId != measuredUserId) {
            measuredUserId = trailingUserId
        }
        if (trailingAssistantId != measuredAssistantId) {
            measuredAssistantId = trailingAssistantId
            lastAssistantHeightPx = 0
        }
        if (trailingAssistantId < 0) {
            lastAssistantHeightPx = 0
        }
    }

    val onThinkingInteraction = remember {
        { detachFollow.value() }
    }

    val scrollConnection = remember(onDismissKeyboard, scrollEnabled) {
        object : NestedScrollConnection {
            private fun onUserScroll(deltaY: Float) {
                if (maintainingEnd) return
                if (!scrollEnabled || abs(deltaY) < 0.5f) return
                onDismissKeyboard()
                detachFollow.value()
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    onUserScroll(available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput) {
                    onUserScroll(consumed.y)
                }
                return Offset.Zero
            }
        }
    }

    SideEffect {
        if (!scrollEnabled) return@SideEffect
        var pulseId = -1L

        if (settledToEnd && userBubbleAnimOnMountToken > lastHandledMountAnimToken) {
            lastHandledMountAnimToken = userBubbleAnimOnMountToken
            pulseId = messages.lastOrNull { it.author == MessageAuthor.User }?.id ?: -1L
        }

        val count = messages.size
        when {
            count > lastPulsedCount -> {
                messages.takeLast(count - lastPulsedCount)
                    .lastOrNull { it.author == MessageAuthor.User }
                    ?.let { pulseId = it.id }
            }
            count < lastPulsedCount -> {
                messages.lastOrNull { it.author == MessageAuthor.User }?.let { pulseId = it.id }
            }
        }
        lastPulsedCount = count

        if (pulseId > 0) {
            userBubbleAnimId = pulseId
            userBubbleAnimToken += 1
        }
    }

    LaunchedEffect(conversationKey, scrollEnabled) {
        if (!scrollEnabled || messages.isEmpty()) {
            settledToEnd = true
            return@LaunchedEffect
        }
        userInterruptedRef.value = false
        following = isRunningState.value ||
            messagesState.value.lastOrNull()?.isStreaming == true
        followingRef.value = following
        settledToEnd = true
        listState.jumpToEnd()
        following = isRunningState.value ||
            messagesState.value.lastOrNull()?.isStreaming == true
        followingRef.value = following
    }

    LaunchedEffect(scrollToEndToken, scrollEnabled) {
        if (!scrollEnabled || scrollToEndToken == lastHandledScrollToEndToken) {
            return@LaunchedEffect
        }
        lastHandledScrollToEndToken = scrollToEndToken
        userInterruptedRef.value = false
        val streamingNow = isRunningState.value ||
            messagesState.value.lastOrNull()?.isStreaming == true
        if (streamingNow) {
            following = true
            followingRef.value = true
        }
        listState.animateToEnd(shouldAbort = { userInterruptedRef.value })
    }

    LaunchedEffect(conversationKey, scrollEnabled) {
        if (!scrollEnabled) return@LaunchedEffect
        var previousUserId = trailingUserIdState.value
        snapshotFlow { trailingUserIdState.value }
            .distinctUntilChanged()
            .collect { userId ->
                val previous = previousUserId
                previousUserId = userId
                if (userId < 0 || userId == previous || previous < 0) return@collect
                userInterruptedRef.value = false
                following = true
                followingRef.value = true
                listState.animateToEnd(shouldAbort = { userInterruptedRef.value })
            }
    }

    SideEffect {
        if (!nowStreaming && following) {
            following = false
            followingRef.value = false
        }
    }

    var wasStreaming by remember { mutableStateOf(false) }
    LaunchedEffect(nowStreaming, scrollEnabled) {
        if (!scrollEnabled) return@LaunchedEffect
        if (wasStreaming && !nowStreaming) {
            listState.jumpToEnd()
        }
        wasStreaming = nowStreaming
    }

    val bottomPaddingState = remember { mutableFloatStateOf(bottomPaddingPx) }
    bottomPaddingState.floatValue = bottomPaddingPx
    LaunchedEffect(conversationKey, scrollEnabled) {
        if (!scrollEnabled) return@LaunchedEffect
        var lastPad = bottomPaddingState.floatValue
        snapshotFlow { bottomPaddingState.floatValue }
            .collect { newPad ->
                val delta = newPad - lastPad
                if (delta == 0f) return@collect
                lastPad = newPad
                if (followingRef.value) {
                    listState.scrollBy(delta)
                }
            }
    }

    LaunchedEffect(activelyFollowing, scrollEnabled) {
        if (!scrollEnabled || !activelyFollowing) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val last = messagesState.value.lastOrNull()
            listOf(
                info.visibleItemsInfo.sumOf { it.size },
                info.totalItemsCount,
                last?.content?.length ?: 0,
                last?.reasoning?.length ?: 0,
                last?.webSearchSteps?.size ?: 0,
            )
        }
            .distinctUntilChanged()
            .collect {
                if (!followingRef.value || listState.isScrollInProgress) return@collect
                maintainingEnd = true
                try {
                    listState.maintainEnd()
                } finally {
                    maintainingEnd = false
                }
            }
    }

    val itemKeyPrefix = conversationKey ?: "new"

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewportHeightPx = it.height }
            .clipToBounds()
            .pointerInput(onDismissKeyboard) {
                detectTapGestures(onTap = {
                    onDismissKeyboard()
                    selectedPromptMessageId = -1L
                })
            }
            .nestedScroll(scrollConnection),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp),
        userScrollEnabled = scrollEnabled,
    ) {
        items(
            items = messages,
            key = { "$itemKeyPrefix:${it.id}" },
            contentType = { it.author.name },
        ) { message ->
            val actionsEnabled = !isRunning
            val canRegenerate = message.author == MessageAuthor.Assistant && actionsEnabled
            val promptBarVisible = message.author == MessageAuthor.User &&
                message.id == selectedPromptMessageId
            val trackHeight = message.id == trailingUserId || message.id == trailingAssistantId
            ChatBubble(
                message = message,
                onRegenerate = if (canRegenerate) {
                    { onRegenerate(message.id) }
                } else {
                    null
                },
                onVariantSwipe = { delta ->
                    if (actionsEnabled) onVariantSwipe(message.id, delta)
                },
                actionsEnabled = actionsEnabled,
                showActions = message.author == MessageAuthor.Assistant,
                promptBarVisible = promptBarVisible,
                onTogglePromptBar = {
                    onDismissKeyboard()
                    selectedPromptMessageId = if (selectedPromptMessageId == message.id) {
                        -1L
                    } else {
                        message.id
                    }
                },
                onStartEdit = {
                    selectedPromptMessageId = -1L
                    onStartEditUserMessage(message.id)
                },
                expectStreamedThinking = message.expectStreamedThinking && message.isStreaming,
                userAnimToken = if (message.id == userBubbleAnimId) userBubbleAnimToken else 0,
                onViewAttachment = onViewAttachment,
                onThinkingInteraction = onThinkingInteraction,
                autoPlayVoiceReplies = message.id == autoPlayMessageId,
                playbackStopToken = playbackStopToken,
                onVoiceAutoPlayStarted = { onVoiceAutoPlayStarted(message.id) },
                onVoicePlaybackFinished = onVoicePlaybackFinished,
                onVoicePlaybackPaused = onVoicePlaybackPaused,
                modifier = Modifier
                    .then(
                        if (trackHeight) {
                            Modifier.onSizeChanged { size ->
                                when (message.id) {
                                    trailingUserId -> lastUserHeightPx = size.height
                                    trailingAssistantId -> lastAssistantHeightPx = size.height
                                }
                            }
                        } else {
                            Modifier
                        },
                    )
                    .padding(bottom = 18.dp),
            )
        }
        item(
            key = "$itemKeyPrefix:$EndSpacerKey",
            contentType = EndSpacerKey,
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(spacerDp),
            )
        }
    }
}

@Composable
fun rememberConversationListState(initialFirstVisibleItemIndex: Int): LazyListState {
    return rememberLazyListState(
        initialFirstVisibleItemIndex = initialFirstVisibleItemIndex.coerceAtLeast(0),
    )
}
