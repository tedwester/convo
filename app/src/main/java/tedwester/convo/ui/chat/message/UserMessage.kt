package tedwester.convo.ui.chat.message

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import tedwester.convo.features.chat.model.ChatAttachment
import tedwester.convo.features.chat.model.ChatMessage
import tedwester.convo.ui.chat.attachments.AttachmentFileBadge
import tedwester.convo.ui.chat.attachments.AttachmentImageThumb

private const val PromptBarAnimMs = 200
private val PromptBarTopGap = 6.dp

@Composable
internal fun UserMessage(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    animToken: Int = 0,
    promptBarVisible: Boolean = false,
    actionsEnabled: Boolean = true,
    onTogglePromptBar: () -> Unit = {},
    onStartEdit: () -> Unit = {},
    onViewAttachment: (ChatAttachment) -> Unit = {},
) {
    val dark = isSystemInDarkTheme()
    val clipboard = LocalClipboardManager.current
    val images = remember(message.attachments) { message.attachments.filter { it.isImage } }
    val files = remember(message.attachments) {
        message.attachments.filterNot {
            it.isImage || it.mimeType?.startsWith("audio/", ignoreCase = true) == true
        }
    }
    val audioAttachment = remember(message.attachments) {
        message.attachments.firstOrNull {
            it.mimeType?.startsWith("audio/", ignoreCase = true) == true
        }
    }
    val textToShow = remember(message.content) { message.userDisplayText() }

    val bubbleGrey = if (dark) Color(0xFF2C2B29) else Color(0xFFE4E6EC)
    val onBubble = if (dark) Color(0xFFE7EAF0) else Color(0xFF1A1D23)

    val density = LocalDensity.current
    val enter = remember { Animatable(1f) }
    var enterLayerActive by remember(message.id) { mutableStateOf(false) }
    var lastAnimatedToken by remember(message.id) { mutableIntStateOf(0) }
    LaunchedEffect(animToken) {
        if (animToken <= 0) {
            enterLayerActive = false
            enter.snapTo(1f)
            lastAnimatedToken = 0
            return@LaunchedEffect
        }
        if (animToken == lastAnimatedToken) return@LaunchedEffect
        lastAnimatedToken = animToken
        enterLayerActive = true
        enter.snapTo(0f)
        enter.animateTo(1f, tween(durationMillis = 380, easing = FastOutSlowInEasing))
        enterLayerActive = false
    }
    val pendingEnter = animToken > lastAnimatedToken && animToken > 0
    val t = when {
        enterLayerActive -> enter.value
        pendingEnter -> 0f
        else -> 1f
    }
    val slidePx = with(density) { 14.dp.toPx() }

    val toggleInteraction = remember { MutableInteractionSource() }
    val promptBarProgress by animateFloatAsState(
        targetValue = if (promptBarVisible) 1f else 0f,
        animationSpec = tween(durationMillis = PromptBarAnimMs, easing = FastOutSlowInEasing),
        label = "promptBarProgress",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (enterLayerActive || pendingEnter) {
                    Modifier.graphicsLayer {
                        alpha = 0.25f + 0.75f * t
                        translationY = (1f - t) * slidePx
                    }
                } else {
                    Modifier
                },
            ),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clickable(
                        interactionSource = toggleInteraction,
                        indication = null,
                        onClick = onTogglePromptBar,
                    ),
            ) {
            if (images.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    images.take(4).forEach { attachment ->
                        AttachmentImageThumb(
                            attachment = attachment,
                            onRemove = null,
                            onClick = { onViewAttachment(attachment) },
                            size = 72.dp,
                        )
                    }
                }
            }

            if (files.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    files.forEach { attachment ->
                        AttachmentFileBadge(
                            attachment = attachment,
                            onRemove = null,
                        )
                    }
                }
            }

            if (textToShow.isNotBlank()) {
                val bubbleShape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .clip(bubbleShape)
                        .background(bubbleGrey)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = textToShow,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = onBubble,
                            fontWeight = FontWeight.ExtraLight,
                        ),
                    )
                }
            }

            audioAttachment?.let { attachment ->
                AudioPlaybackRow(
                    path = attachment.path,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            }

            if (promptBarProgress > 0f) {
                Box(
                    modifier = Modifier
                        .height((PromptBarTopGap + PromptBarHeight) * promptBarProgress)
                        .clipToBounds()
                        .graphicsLayer { alpha = promptBarProgress },
                ) {
                    UserPromptControls(
                        copyEnabled = textToShow.isNotBlank(),
                        editEnabled = actionsEnabled,
                        onCopy = {
                            if (textToShow.isNotBlank()) {
                                clipboard.setText(AnnotatedString(textToShow))
                            }
                        },
                        onEdit = onStartEdit,
                        modifier = Modifier.padding(top = PromptBarTopGap),
                    )
                }
            }
        }
    }
}
