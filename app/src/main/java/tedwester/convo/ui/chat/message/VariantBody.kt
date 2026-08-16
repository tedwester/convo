package tedwester.convo.ui.chat.message

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tedwester.convo.features.chat.model.ChatMessage
import tedwester.convo.features.chat.model.EMPTY_RESPONSE_TEXT
import tedwester.convo.features.chat.model.isAssistantStatusContent

/**
 * Assistant turn body text for streaming or single-variant turns.
 *
 * Multi-variant paging (text, voice, image, video) lives in [VariantPagerLayout]
 * via [AssistantMessage].
 */
@Composable
internal fun VariantBody(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    forceShow: Boolean = false,
) {
    val liveText = if (message.isStreaming) {
        message.content
    } else {
        message.activeBodyText()
    }

    if (message.isStreaming && liveText.isBlank()) {
        GeneratingResponseLabel(
            text = streamingStatusLabel(message.statusLabel, message.activeWebSearchSteps()),
            modifier = modifier.fillMaxWidth(),
        )
        return
    }
    if (!forceShow && message.hidesGeneratedBody()) {
        return
    }
    if (liveText.isBlank()) {
        AssistantStatusText(
            text = EMPTY_RESPONSE_TEXT,
            modifier = modifier.fillMaxWidth(),
        )
        return
    }
    AssistantTurnText(
        text = liveText,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
internal fun AssistantTurnText(
    text: String,
    modifier: Modifier = Modifier,
) {
    val resolved = text.ifBlank { EMPTY_RESPONSE_TEXT }
    if (isAssistantStatusContent(resolved)) {
        AssistantStatusText(text = resolved, modifier = modifier)
    } else {
        AssistantMarkdown(
            content = resolved,
            modifier = modifier,
        )
    }
}
