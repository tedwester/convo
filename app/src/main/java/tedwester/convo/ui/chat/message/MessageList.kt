package tedwester.convo.ui.chat.message

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import tedwester.convo.features.chat.model.ChatAttachment
import tedwester.convo.features.chat.model.ChatMessage
import tedwester.convo.features.chat.model.MessageAuthor
import tedwester.convo.ui.chat.RectAnchor

/** Space under every chat bubble, down to the list's content end. */
internal val ChatBubbleBottomGap = 18.dp

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    listState: LazyListState,
    scrollController: ChatScrollController,
    conversationKey: Any? = null,
    scrollEnabled: Boolean = true,
    isRunning: Boolean = false,
    sendPulseMessageId: Long = 0,
    sendFlightOrigin: RectAnchor? = null,
    onSendAnimationConsumed: () -> Unit = {},
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

    var userBubbleAnimId by remember(conversationKey) { mutableLongStateOf(-1L) }
    var userBubbleAnimToken by remember(conversationKey) { mutableIntStateOf(0) }
    var selectedPromptMessageId by remember { mutableLongStateOf(-1L) }

    // A send is picked up exactly once: adopting it clears the marker in ChatState,
    // so reopening the chat or rebuilding this list can never replay it. Anything
    // that adds messages without a send (chat open, history load) sets no marker,
    // so those bubbles just appear.
    LaunchedEffect(sendPulseMessageId) {
        if (sendPulseMessageId <= 0 || sendPulseMessageId == userBubbleAnimId) {
            return@LaunchedEffect
        }
        if (messages.none { it.id == sendPulseMessageId && it.author == MessageAuthor.User }) {
            return@LaunchedEffect
        }
        userBubbleAnimId = sendPulseMessageId
        userBubbleAnimToken += 1
        onSendAnimationConsumed()
    }

    // The freshly sent message is already in the list before the adoption above
    // runs. It waits out of sight so it never flashes in its resting place first.
    val hiddenUserMessageId = if (sendPulseMessageId > 0 &&
        sendPulseMessageId != userBubbleAnimId &&
        messages.any { it.id == sendPulseMessageId && it.author == MessageAuthor.User }
    ) {
        sendPulseMessageId
    } else {
        -1L
    }

    val onThinkingInteraction = remember(scrollController) {
        {
            // Expanding a thinking block grows its message, which moves everything below it.
            // At the newest content the follow logic keeps the bottom flush instead.
            if (!scrollController.listState.isAtEnd()) {
                scrollController.stopFollowing()
            }
        }
    }

    val itemKeyPrefix = conversationKey ?: "new"

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            // Pulling past the newest content lifts the chat a little.
            .graphicsLayer { translationY = -scrollController.pullShift }
            .pointerInput(onDismissKeyboard) {
                detectTapGestures(onTap = {
                    onDismissKeyboard()
                    selectedPromptMessageId = -1L
                })
            }
            .pointerInput(scrollController) {
                // Finger down starts the gesture, finger up ends it. Observed
                // straight from touch events, so a lift can never go missing:
                // armed on release always means a new chat. Never consumes.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    scrollController.onGestureStart()
                    var lifted = false
                    while (!lifted) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Final)
                        if (event.changes.none { it.pressed }) lifted = true
                    }
                    scrollController.onGestureEnd()
                }
            }
            .nestedScroll(scrollController.scrollConnection),
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
                startHidden = message.id == hiddenUserMessageId,
                flightOrigin = sendFlightOrigin,
                onViewAttachment = onViewAttachment,
                onThinkingInteraction = onThinkingInteraction,
                autoPlayVoiceReplies = message.id == autoPlayMessageId,
                playbackStopToken = playbackStopToken,
                onVoiceAutoPlayStarted = { onVoiceAutoPlayStarted(message.id) },
                onVoicePlaybackFinished = onVoicePlaybackFinished,
                onVoicePlaybackPaused = onVoicePlaybackPaused,
                modifier = Modifier.padding(bottom = ChatBubbleBottomGap),
            )
        }
    }
}
