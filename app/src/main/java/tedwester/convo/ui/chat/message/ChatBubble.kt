package tedwester.convo.ui.chat.message

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tedwester.convo.features.chat.model.ChatAttachment
import tedwester.convo.features.chat.model.ChatMessage
import tedwester.convo.features.chat.model.MessageAuthor

@Composable
fun ChatBubble(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onRegenerate: (() -> Unit)? = null,
    onVariantSwipe: (delta: Int) -> Unit = {},
    actionsEnabled: Boolean = true,
    showActions: Boolean = true,
    promptBarVisible: Boolean = false,
    onTogglePromptBar: () -> Unit = {},
    onStartEdit: () -> Unit = {},
    onResend: () -> Unit = {},
    userAnimToken: Int = 0,
    expectStreamedThinking: Boolean = false,
    onViewAttachment: (ChatAttachment) -> Unit = {},
    onThinkingInteraction: () -> Unit = {},
    autoPlayVoiceReplies: Boolean = false,
    playbackStopToken: Int = 0,
    onVoiceAutoPlayStarted: () -> Unit = {},
    onVoicePlaybackFinished: () -> Unit = {},
    onVoicePlaybackPaused: () -> Unit = {},
) {
    if (message.author == MessageAuthor.User) {
        UserMessage(
            message = message,
            animToken = userAnimToken,
            promptBarVisible = promptBarVisible,
            actionsEnabled = actionsEnabled,
            onTogglePromptBar = onTogglePromptBar,
            onStartEdit = onStartEdit,
            onResend = onResend,
            onViewAttachment = onViewAttachment,
            modifier = modifier,
        )
    } else {
        AssistantMessage(
            message = message,
            modifier = modifier,
            onRegenerate = onRegenerate,
            onVariantSwipe = onVariantSwipe,
            actionsEnabled = actionsEnabled,
            showActions = showActions,
            expectStreamedThinking = expectStreamedThinking,
            onViewAttachment = onViewAttachment,
            onThinkingInteraction = onThinkingInteraction,
            autoPlayVoiceReplies = autoPlayVoiceReplies,
            playbackStopToken = playbackStopToken,
            onVoiceAutoPlayStarted = onVoiceAutoPlayStarted,
            onVoicePlaybackFinished = onVoicePlaybackFinished,
            onVoicePlaybackPaused = onVoicePlaybackPaused,
        )
    }
}
