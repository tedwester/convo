package tedwester.convo.ui.chat.message

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import tedwester.convo.features.chat.model.ChatAttachment
import tedwester.convo.features.chat.model.ChatMessage
import tedwester.convo.ui.chat.attachments.AttachmentFileBadge
import tedwester.convo.ui.chat.attachments.AttachmentImageThumb
import tedwester.convo.ui.input.ConvoKeyboardOptions

@Composable
internal fun UserMessage(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    animToken: Int = 0,
    promptBarVisible: Boolean = false,
    actionsEnabled: Boolean = true,
    onTogglePromptBar: () -> Unit = {},
    onResend: (editedText: String) -> Unit = {},
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

    var isEditing by remember(message.id) { mutableStateOf(false) }
    var draft by remember(message.id) { mutableStateOf(TextFieldValue(textToShow)) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(promptBarVisible, message.id) {
        if (!promptBarVisible) {
            isEditing = false
            draft = TextFieldValue(textToShow)
        }
    }
    LaunchedEffect(textToShow, isEditing) {
        if (!isEditing) draft = TextFieldValue(textToShow)
    }
    LaunchedEffect(isEditing) {
        if (isEditing) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    val toggleInteraction = remember { MutableInteractionSource() }

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
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clickable(
                    enabled = !isEditing,
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

            if (textToShow.isNotBlank() || isEditing) {
                val bubbleShape = RoundedCornerShape(14.dp)
                Box(
                    modifier = Modifier
                        .widthIn(min = if (isEditing) 120.dp else 0.dp)
                        .clip(bubbleShape)
                        .background(bubbleGrey)
                        .then(
                            if (isEditing) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = onBubble.copy(alpha = 0.28f),
                                    shape = bubbleShape,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    val textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = onBubble,
                        fontWeight = FontWeight.ExtraLight,
                    )
                    if (isEditing) {
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            textStyle = textStyle,
                            cursorBrush = SolidColor(onBubble),
                            keyboardOptions = ConvoKeyboardOptions.Text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                        )
                    } else {
                        Text(
                            text = textToShow,
                            style = textStyle,
                        )
                    }
                }
            }

            audioAttachment?.let { attachment ->
                AudioPlaybackRow(
                    path = attachment.path,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AnimatedVisibility(
                visible = promptBarVisible,
                enter = fadeIn(tween(PagerAnimMs, easing = FastOutSlowInEasing)) +
                    expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = tween(PagerAnimMs, easing = FastOutSlowInEasing),
                    ),
                exit = fadeOut(tween(PagerAnimMs / 2, easing = FastOutSlowInEasing)) +
                    shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = tween(PagerAnimMs / 2, easing = FastOutSlowInEasing),
                    ),
            ) {
                val draftText = draft.text.trim()
                UserPromptControls(
                    copyEnabled = draftText.isNotBlank(),
                    editEnabled = actionsEnabled,
                    resendEnabled = actionsEnabled &&
                        (draftText.isNotBlank() || message.attachments.isNotEmpty()),
                    isEditing = isEditing,
                    onCopy = {
                        if (draftText.isNotBlank()) {
                            clipboard.setText(AnnotatedString(draftText))
                        }
                    },
                    onEdit = {
                        if (isEditing) {
                            isEditing = false
                            draft = TextFieldValue(textToShow)
                        } else {
                            draft = TextFieldValue(
                                text = textToShow,
                                selection = TextRange(textToShow.length),
                            )
                            isEditing = true
                        }
                    },
                    onResend = { onResend(draft.text) },
                )
            }
        }
    }
}
