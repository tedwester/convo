package tedwester.convo.ui.chat.message

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.SideEffect
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import tedwester.convo.features.chat.model.ChatAttachment
import tedwester.convo.features.chat.model.ChatMessage
import tedwester.convo.features.chat.model.MessageAuthor

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    listState: LazyListState,
    conversationKey: Any? = null,
    messageListRevision: Int = 0,
    scrollEnabled: Boolean = true,
    isRunning: Boolean = false,
    variantSwipeToken: Int = 0,
    variantSwipeMessageId: Long = -1L,
    resumeFollowToken: Int = 0,
    userBubbleAnimOnMountToken: Int = 0,
    onRegenerate: (Long) -> Unit = {},
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
    val listScopeKey = remember(conversationKey, messageListRevision) {
        listOf(conversationKey, messageListRevision)
    }
    var previousCount by remember(listScopeKey) { mutableIntStateOf(messages.size) }
    var lastPulsedCount by remember(listScopeKey) { mutableIntStateOf(messages.size) }
    var followBottom by remember(listScopeKey) { mutableStateOf(false) }
    var userDetached by remember(listScopeKey) { mutableStateOf(false) }
    var settledToBottom by remember(listScopeKey) { mutableStateOf(false) }
    var userBubbleAnimId by remember { mutableLongStateOf(-1L) }
    var userBubbleAnimToken by remember { mutableIntStateOf(0) }
    var lastHandledVariantSwipeToken by remember { mutableIntStateOf(0) }
    var lastHandledResumeFollowToken by remember { mutableIntStateOf(0) }
    var lastHandledMountAnimToken by remember { mutableIntStateOf(0) }

    fun attachToBottom() {
        followBottom = true
        userDetached = false
    }

    val scope = rememberCoroutineScope()
    val followBottomState = rememberUpdatedState(followBottom)
    val userDetachedState = rememberUpdatedState(userDetached)

    val onUserScrollAway = rememberUpdatedState {
        followBottom = false
        userDetached = true
    }

    fun detachFromBottom() {
        followBottom = false
        userDetached = true
    }

    val onThinkingInteraction = remember(listScopeKey, listState, scope) {
        {
            detachFromBottom()
            scope.launch {
                if (listState.isAtVisualBottom(VisualBottomThresholdPx)) {
                    listState.scrollBy(-1f)
                }
            }
            Unit
        }
    }

    val scrollConnection = remember(onDismissKeyboard, scrollEnabled) {
        object : NestedScrollConnection {
            private fun onUserScroll(deltaY: Float) {
                if (!scrollEnabled || deltaY <= 0f) return
                onDismissKeyboard()
                onUserScrollAway.value()
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

        if (settledToBottom && userBubbleAnimOnMountToken > lastHandledMountAnimToken) {
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

    LaunchedEffect(listScopeKey, scrollEnabled) {
        if (!scrollEnabled || messages.isEmpty()) {
            settledToBottom = true
            return@LaunchedEffect
        }
        userDetached = false
        followBottom = false
        settledToBottom = false
        listState.pinToVisualBottomUntilSettled()
        settledToBottom = true
    }

    LaunchedEffect(resumeFollowToken, scrollEnabled) {
        if (!scrollEnabled || resumeFollowToken == 0) return@LaunchedEffect
        if (resumeFollowToken == lastHandledResumeFollowToken) return@LaunchedEffect
        lastHandledResumeFollowToken = resumeFollowToken
        attachToBottom()
        listState.pinToVisualBottom()
    }

    val bottomPaddingPx = with(LocalDensity.current) {
        contentPadding.calculateBottomPadding().toPx()
    }
    val bottomPaddingState = remember(listScopeKey) { mutableFloatStateOf(bottomPaddingPx) }
    bottomPaddingState.floatValue = bottomPaddingPx
    LaunchedEffect(listScopeKey, scrollEnabled) {
        if (!scrollEnabled) return@LaunchedEffect
        var lastPad = bottomPaddingState.floatValue
        snapshotFlow { bottomPaddingState.floatValue }
            .collect { newPad ->
                val delta = newPad - lastPad
                if (delta == 0f) return@collect
                lastPad = newPad
                when {
                    followBottomState.value && !userDetachedState.value ->
                        listState.scrollBy(delta)
                    delta > 0f -> listState.scrollBy(delta)
                    !listState.isAtVisualBottom(PinSettleThresholdPx) ->
                        listState.scrollBy(delta)
                }
            }
    }

    LaunchedEffect(messages.size, messages.lastOrNull()?.id, scrollEnabled, isRunning) {
        if (!scrollEnabled) return@LaunchedEffect
        val count = messages.size
        val prev = previousCount
        previousCount = count
        if (count == prev) return@LaunchedEffect

        when {
            count < prev -> {
                attachToBottom()
                listState.pinToVisualBottom()
            }
            count > prev -> {
                attachToBottom()
                val tailIsAssistant = messages.lastOrNull()?.author == MessageAuthor.Assistant
                if (tailIsAssistant || isRunning) {
                    listState.pinToVisualBottom()
                }
            }
        }
    }

    val streamingTailId = messages.lastOrNull()
        ?.takeIf { it.author == MessageAuthor.Assistant && it.isStreaming }
        ?.id

    LaunchedEffect(followBottom, scrollEnabled, streamingTailId) {
        if (!scrollEnabled || !followBottom || streamingTailId == null) return@LaunchedEffect
        snapshotFlow { messages.lastOrNull()?.content?.length ?: 0 }
            .distinctUntilChanged()
            .drop(1)
            .collect {
                if (!followBottomState.value || userDetachedState.value || listState.isScrollInProgress) {
                    return@collect
                }
                listState.maintainVisualBottom()
            }
    }

    LaunchedEffect(scrollEnabled, streamingTailId) {
        if (!scrollEnabled || streamingTailId == null) return@LaunchedEffect
        var lastTailSize = -1
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.find {
                it.index == listState.layoutInfo.totalItemsCount - 1
            }?.size ?: -1
        }
            .distinctUntilChanged()
            .collect { newSize ->
                if (newSize < 0) return@collect
                if (lastTailSize < 0) {
                    lastTailSize = newSize
                    return@collect
                }
                val delta = newSize - lastTailSize
                lastTailSize = newSize
                if (delta <= 0 || listState.isScrollInProgress) return@collect
                if (followBottomState.value && !userDetachedState.value) return@collect
                if (listState.isAtVisualBottom(VisualBottomThresholdPx)) {
                    listState.scrollBy(-delta.toFloat())
                }
            }
    }

    LaunchedEffect(variantSwipeToken, variantSwipeMessageId, messages.size) {
        if (!scrollEnabled) return@LaunchedEffect
        if (variantSwipeToken == 0 || variantSwipeToken == lastHandledVariantSwipeToken) {
            return@LaunchedEffect
        }
        lastHandledVariantSwipeToken = variantSwipeToken
        val index = messages.indexOfFirst { it.id == variantSwipeMessageId }
        if (index < 0) return@LaunchedEffect
        listState.pinItemBottomIntoView(index)
    }

    val itemKeyPrefix = conversationKey ?: "new"

    key(listScopeKey) {
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .clipToBounds()
                .alpha(if (settledToBottom) 1f else 0f)
                .pointerInput(onDismissKeyboard) {
                    detectTapGestures(onTap = { onDismissKeyboard() })
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
                    expectStreamedThinking = message.expectStreamedThinking && message.isStreaming,
                    userAnimToken = if (message.id == userBubbleAnimId) userBubbleAnimToken else 0,
                    onViewAttachment = onViewAttachment,
                    onThinkingInteraction = onThinkingInteraction,
                    autoPlayVoiceReplies = message.id == autoPlayMessageId,
                    playbackStopToken = playbackStopToken,
                    onVoiceAutoPlayStarted = { onVoiceAutoPlayStarted(message.id) },
                    onVoicePlaybackFinished = onVoicePlaybackFinished,
                    onVoicePlaybackPaused = onVoicePlaybackPaused,
                    modifier = Modifier.padding(bottom = 18.dp),
                )
            }
        }
    }
}

@Composable
fun bottomAnchoredListState(messageCount: Int): LazyListState {
    val lastIndex = (messageCount - 1).coerceAtLeast(0)
    return rememberLazyListState(
        initialFirstVisibleItemIndex = lastIndex,
        initialFirstVisibleItemScrollOffset = Int.MAX_VALUE / 2,
    )
}
