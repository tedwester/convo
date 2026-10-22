package tedwester.convo.ui.chat.message

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import tedwester.convo.features.chat.model.ChatAttachment
import tedwester.convo.features.chat.model.ChatMessage
import tedwester.convo.features.chat.model.MessageAuthor

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

    var lastPulsedCount by remember { mutableIntStateOf(messages.size) }
    var userBubbleAnimId by remember { mutableLongStateOf(-1L) }
    var userBubbleAnimToken by remember { mutableIntStateOf(0) }
    var lastHandledMountAnimToken by remember { mutableIntStateOf(0) }
    var selectedPromptMessageId by remember { mutableLongStateOf(-1L) }

    val onThinkingInteraction = remember(scrollController) {
        { scrollController.stopFollowing() }
    }

    val scrollConnection = remember(onDismissKeyboard, scrollController) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    onDismissKeyboard()
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // Only let go of the newest content once the list actually moved, so that a
                // swipe that cannot scroll (at either end) does not drop the follow state.
                if (source == NestedScrollSource.UserInput && consumed.y != 0f) {
                    scrollController.onUserScrolled()
                }
                return Offset.Zero
            }
        }
    }

    SideEffect {
        if (!scrollEnabled) return@SideEffect
        var pulseId = -1L

        if (userBubbleAnimOnMountToken > lastHandledMountAnimToken) {
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

    val itemKeyPrefix = conversationKey ?: "new"

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
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
                modifier = Modifier.padding(bottom = 18.dp),
            )
        }
    }
}
