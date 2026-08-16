package tedwester.convo.ui.chat.message

import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tedwester.convo.features.chat.model.ChatAttachment
import tedwester.convo.features.chat.model.ChatMessage
import tedwester.convo.features.chat.model.EMPTY_RESPONSE_TEXT
import tedwester.convo.features.chat.model.STOPPED_RESPONSE_TEXT
import tedwester.convo.features.chat.model.allCitations
import tedwester.convo.features.chat.model.isAssistantStatusContent
import java.io.File

@Composable
internal fun AssistantMessage(
    message: ChatMessage,
    modifier: Modifier = Modifier,
    onRegenerate: (() -> Unit)?,
    onVariantSwipe: (delta: Int) -> Unit = {},
    actionsEnabled: Boolean = true,
    showActions: Boolean,
    expectStreamedThinking: Boolean = false,
    onViewAttachment: (ChatAttachment) -> Unit = {},
    onThinkingInteraction: () -> Unit = {},
    autoPlayVoiceReplies: Boolean = false,
    playbackStopToken: Int = 0,
    onVoiceAutoPlayStarted: () -> Unit = {},
    onVoicePlaybackFinished: () -> Unit = {},
    onVoicePlaybackPaused: () -> Unit = {},
) {
    val reasoning = message.activeReasoning()
    val webSearchSteps = message.activeWebSearchSteps()
    val searchCitations = remember(webSearchSteps) { webSearchSteps.allCitations() }
    val waitingForFirstToken = message.isStreaming && message.content.isBlank()
    val waitingForResponseContent = waitingForFirstToken
    val responseHasStarted = !waitingForResponseContent && message.content.isNotBlank()
    val stoppedWhileThinking = !message.isStreaming &&
        message.content == STOPPED_RESPONSE_TEXT &&
        message.stoppedWhileThinking
    val showThinkingPlaceholder = waitingForFirstToken &&
        (expectStreamedThinking || reasoning.isNotBlank())
    val showSearchActivity = webSearchSteps.any { step ->
        step.isSearching || step.query.isNotBlank() || step.citations.isNotEmpty()
    }
    val hasThinkingContent = showThinkingPlaceholder ||
        reasoning.isNotBlank() ||
        stoppedWhileThinking
    val shouldShowThinkingSection = hasThinkingContent
    val showSearchOnlyActivity = showSearchActivity && !hasThinkingContent
    val liveThinkingElapsedMs = rememberLiveThinkingElapsedMs(message)
    val thinkingHeaderText = when {
        stoppedWhileThinking -> "Stopped thinking after cancelled generation"
        reasoning.isNotBlank() && message.activeThoughtDuration() != null ->
            "Thought for ${formatThoughtDuration(message.activeThoughtDuration()!!)}"
        message.isStreaming && message.activeThoughtDuration() == null && liveThinkingElapsedMs != null ->
            formatLiveThinkingHeader(liveThinkingElapsedMs)
        waitingForFirstToken && showThinkingPlaceholder -> "Thinking…"
        reasoning.isNotBlank() -> "Thought"
        else -> "Thinking…"
    }
    val shimmerHeader = waitingForFirstToken && hasThinkingContent
    var showSearchResultsModal by remember(message.id, message.variantIndex) {
        mutableStateOf(false)
    }
    val showSearchResultsPill = searchCitations.isNotEmpty() && responseHasStarted
    val variants = message.savedVariants()
    val useVariantPager = !message.isStreaming && variants.size > 1
    val activeAttachments = message.activeAttachments()
    val audioAttachment = activeAttachments.firstOrNull {
        it.mimeType?.startsWith("audio/", ignoreCase = true) == true
    }
    val voiceScript = message.copyableText()
    val bodyText = message.activeBodyText()
    val isStatusBody = isAssistantStatusContent(bodyText)
    val canToggleVoiceDisplay = !message.isStreaming &&
        audioAttachment != null &&
        voiceScript.isNotBlank()
    var showVoiceAsText by rememberSaveable(message.id, message.variantIndex) {
        mutableStateOf(message.showVoiceAsTextFirst)
    }
    val showVoiceToggle = canToggleVoiceDisplay && !isStatusBody
    val showResponseBody = !waitingForResponseContent &&
        (isStatusBody ||
            ((message.isStreaming || bodyText.isNotBlank()) && !message.hidesGeneratedBody()))
    val showEmptyFallback = !message.isStreaming &&
        !waitingForResponseContent &&
        !showResponseBody &&
        !showVoiceToggle &&
        !shouldShowThinkingSection &&
        !showSearchActivity &&
        activeAttachments.none {
            it.isImage || it.isVideo ||
                it.mimeType?.startsWith("audio/", ignoreCase = true) == true
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when {
            waitingForResponseContent && !showThinkingPlaceholder -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    GeneratingResponseLabel(
                        text = streamingStatusLabel(message.statusLabel, webSearchSteps),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (showSearchOnlyActivity) {
                        WebSearchTimeline(
                            steps = webSearchSteps,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            else -> {
                if (shouldShowThinkingSection) {
                    ThinkingSection(
                        sectionKey = "${message.id}_v${message.variantIndex}",
                        reasoning = reasoning,
                        headerText = thinkingHeaderText,
                        shimmerHeader = shimmerHeader,
                        expandedByDefault = message.expectStreamedThinking ||
                            expectStreamedThinking ||
                            showSearchActivity,
                        collapseWhenResponseStarts = responseHasStarted,
                        showSearchTimeline = waitingForFirstToken && showSearchActivity,
                        webSearchSteps = webSearchSteps,
                        onInteraction = onThinkingInteraction,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (showSearchResultsPill) {
                    WebSearchResultsPill(
                        citations = searchCitations,
                        onClick = { showSearchResultsModal = true },
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                    )
                }
                if (showSearchResultsModal) {
                    WebSearchResultsModal(
                        citations = searchCitations,
                        onDismiss = { showSearchResultsModal = false },
                    )
                }
                if (useVariantPager) {
                    VariantPagerLayout(
                        messageId = message.id,
                        targetIndex = message.variantIndex,
                        pageCount = variants.size,
                        modifier = Modifier.fillMaxWidth(),
                    ) { index, pageModifier ->
                        AssistantVariantPage(
                            message = message,
                            variantIndex = index,
                            showVoiceAsText = if (index == message.variantIndex) {
                                showVoiceAsText
                            } else {
                                null
                            },
                            autoPlayVoiceReplies = autoPlayVoiceReplies && index == message.variantIndex,
                            playbackStopToken = playbackStopToken,
                            onViewAttachment = onViewAttachment,
                            onVoiceAutoPlayStarted = onVoiceAutoPlayStarted,
                            onVoicePlaybackFinished = onVoicePlaybackFinished,
                            onVoicePlaybackPaused = onVoicePlaybackPaused,
                            modifier = pageModifier,
                        )
                    }
                } else {
                    if (showResponseBody) {
                        VariantBody(message = message)
                    }
                    if (showEmptyFallback) {
                        AssistantStatusText(
                            text = bodyText.ifBlank { EMPTY_RESPONSE_TEXT },
                        )
                    }
                    audioAttachment?.takeIf { showVoiceToggle }?.let { attachment ->
                        VoiceReplyDisplayToggle(
                            showAsText = showVoiceAsText,
                            voiceScript = voiceScript,
                            audioPath = attachment.path,
                            autoPlay = autoPlayVoiceReplies && !showVoiceAsText,
                            playbackStopToken = playbackStopToken,
                            onAutoPlayStarted = onVoiceAutoPlayStarted,
                            onPlaybackFinished = onVoicePlaybackFinished,
                            onPlaybackPaused = onVoicePlaybackPaused,
                        )
                        if (showVoiceAsText && autoPlayVoiceReplies) {
                            AudioPlaybackRow(
                                path = attachment.path,
                                autoPlay = true,
                                showUi = false,
                                playbackStopToken = playbackStopToken,
                                onAutoPlayStarted = onVoiceAutoPlayStarted,
                                onPlaybackFinished = onVoicePlaybackFinished,
                                onPlaybackPaused = onVoicePlaybackPaused,
                            )
                        }
                    }
                    AssistantAttachments(
                        attachments = activeAttachments,
                        onViewAttachment = onViewAttachment,
                        hideAudio = showVoiceToggle,
                        autoPlayVoiceReplies = autoPlayVoiceReplies,
                        playbackStopToken = playbackStopToken,
                        onVoiceAutoPlayStarted = onVoiceAutoPlayStarted,
                        onVoicePlaybackFinished = onVoicePlaybackFinished,
                        onVoicePlaybackPaused = onVoicePlaybackPaused,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (showActions) {
            AssistantMessageActions(
                message = message,
                onRegenerate = onRegenerate,
                onVariantSwipe = onVariantSwipe,
                actionsEnabled = actionsEnabled,
                showVoiceAsText = showVoiceAsText,
                onToggleVoiceDisplay = if (showVoiceToggle) {
                    { showVoiceAsText = !showVoiceAsText }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
internal fun AssistantBlockBody(
    text: String,
    modifier: Modifier = Modifier,
    reasoning: String = "",
    showThinking: Boolean = false,
    messageId: Long = 0L,
    variantIndex: Int = 0,
    thoughtDurationMs: Long? = null,
    stoppedWhileThinking: Boolean = false,
    expandedThinkingByDefault: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showThinking && reasoning.isNotBlank()) {
            ThinkingSection(
                sectionKey = "block_${messageId}_v$variantIndex",
                reasoning = reasoning,
                headerText = if (stoppedWhileThinking) {
                    "Stopped thinking after cancelled generation"
                } else {
                    thoughtDurationMs?.let { "Thought for ${formatThoughtDuration(it)}" } ?: "Thought"
                },
                shimmerHeader = false,
                expandedByDefault = expandedThinkingByDefault,
            )
        }
        AssistantTurnText(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
        )
    }
}

private fun formatThoughtDuration(durationMs: Long): String {
    val totalSec = (durationMs / 1000L).coerceAtLeast(1L)
    val min = totalSec / 60
    val sec = totalSec % 60
    return if (min > 0) {
        "${min}m ${sec}s"
    } else {
        "${sec}s"
    }
}

private fun formatLiveThinkingHeader(elapsedMs: Long): String {
    if (elapsedMs < 1_000L) return "Thinking…"
    return "Thinking for ${formatThoughtDuration(elapsedMs)}"
}

@Composable
private fun rememberLiveThinkingElapsedMs(message: ChatMessage): Long? {
    val tracking = message.isStreaming && message.activeThoughtDuration() == null
    val startedAt = message.thinkingStartedAtElapsed
    var elapsedMs by remember(message.id, message.variantIndex) { mutableLongStateOf(0L) }

    LaunchedEffect(tracking, startedAt, message.id, message.variantIndex) {
        if (!tracking || startedAt == null) {
            elapsedMs = 0L
            return@LaunchedEffect
        }
        while (true) {
            elapsedMs = SystemClock.elapsedRealtime() - startedAt
            delay(250L)
        }
    }

    return if (tracking && startedAt != null) elapsedMs else null
}

@Composable
private fun rememberResponseControlsAlpha(
    messageId: Long,
    variantIndex: Int,
    isStreaming: Boolean,
): Float {
    val alpha = remember(messageId) { Animatable(if (isStreaming) 0f else 1f) }
    var skipVariantFade by remember(messageId) { mutableStateOf(true) }
    var skipStreamFadeIn by remember(messageId) { mutableStateOf(!isStreaming) }

    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            alpha.snapTo(0f)
            skipStreamFadeIn = false
        } else if (!skipStreamFadeIn) {
            alpha.animateTo(1f, tween(PagerAnimMs, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(variantIndex) {
        if (skipVariantFade) {
            skipVariantFade = false
            return@LaunchedEffect
        }
        if (isStreaming) return@LaunchedEffect
        alpha.animateTo(0f, tween(PagerAnimMs / 2, easing = FastOutSlowInEasing))
        delay(PagerAnimMs.toLong())
        alpha.animateTo(1f, tween(PagerAnimMs, easing = FastOutSlowInEasing))
    }

    return alpha.value
}

@Composable
internal fun AssistantMessageActions(
    message: ChatMessage,
    onRegenerate: (() -> Unit)?,
    onVariantSwipe: (delta: Int) -> Unit = {},
    actionsEnabled: Boolean = true,
    showVoiceAsText: Boolean = false,
    onToggleVoiceDisplay: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val controlsAlpha = rememberResponseControlsAlpha(
        messageId = message.id,
        variantIndex = message.variantIndex,
        isStreaming = message.isStreaming,
    )
    val controlsInteractive = !message.isStreaming && controlsAlpha > 0.5f
    val responseActionsEnabled = actionsEnabled && controlsInteractive
    val activeAttachments = message.activeAttachments()
    val audioAttachment = remember(message.id, message.variantIndex, activeAttachments) {
        activeAttachments.firstOrNull {
            it.mimeType?.startsWith("audio/", ignoreCase = true) == true
        }
    }
    val videoAttachment = remember(message.id, message.variantIndex, activeAttachments) {
        activeAttachments.firstOrNull { it.isVideo }
    }
    val mediaAttachment = audioAttachment ?: videoAttachment

    AssistantResponseControls(
        message = message,
        controlsVisible = controlsInteractive,
        responseActionsEnabled = responseActionsEnabled,
        controlsAlpha = controlsAlpha,
        onRegenerate = onRegenerate,
        onCopy = {
            val text = message.copyableText()
            if (text.isNotBlank()) {
                clipboard.setText(AnnotatedString(text))
            }
        },
        onShare = {
            val attachment = mediaAttachment
            if (attachment != null) {
                val title = if (attachment.isVideo) "Share video" else "Share voice message"
                if (!shareAttachment(context, attachment, title)) {
                    Toast.makeText(
                        context,
                        if (attachment.isVideo) "Couldn’t share video" else "Couldn’t share voice message",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } else {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message.content)
                }
                context.startActivity(Intent.createChooser(send, "Share reply"))
            }
        },
        onDownload = mediaAttachment?.let { attachment ->
            {
                scope.launch {
                    val saved = saveAttachmentToDownloads(context, attachment)
                    Toast.makeText(
                        context,
                        if (saved) "Saved to Downloads" else {
                            if (attachment.isVideo) "Couldn’t save video" else "Couldn’t save voice reply"
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        },
        showVoiceAsText = showVoiceAsText,
        onToggleVoiceDisplay = onToggleVoiceDisplay,
        onVariantChange = onVariantSwipe,
        modifier = modifier,
    )
}

@Composable
private fun AssistantVariantPage(
    message: ChatMessage,
    variantIndex: Int,
    showVoiceAsText: Boolean?,
    autoPlayVoiceReplies: Boolean,
    playbackStopToken: Int,
    onViewAttachment: (ChatAttachment) -> Unit,
    onVoiceAutoPlayStarted: () -> Unit,
    onVoicePlaybackFinished: () -> Unit,
    onVoicePlaybackPaused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bodyText = message.bodyTextAt(variantIndex)
    val attachments = message.attachmentsAt(variantIndex)
    val isStatusBody = isAssistantStatusContent(bodyText)
    val audioAttachment = attachments.firstOrNull {
        it.mimeType?.startsWith("audio/", ignoreCase = true) == true
    }
    val voiceScript = message.copyableTextAt(variantIndex)
    val canToggleVoiceDisplay = audioAttachment != null && voiceScript.isNotBlank()
    var savedShowVoiceAsText by rememberSaveable(message.id, variantIndex) {
        mutableStateOf(message.showVoiceAsTextFirst)
    }
    val resolvedShowVoiceAsText = showVoiceAsText ?: savedShowVoiceAsText
    val showVoiceToggle = canToggleVoiceDisplay && !isStatusBody
    val showResponseBody = isStatusBody ||
        (bodyText.isNotBlank() && !message.hidesGeneratedBodyAt(variantIndex))
    val showEmptyFallback = !showResponseBody &&
        !showVoiceToggle &&
        attachments.none {
            it.isImage || it.isVideo ||
                it.mimeType?.startsWith("audio/", ignoreCase = true) == true
        }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showResponseBody) {
            if (bodyText.isBlank()) {
                AssistantStatusText(
                    text = EMPTY_RESPONSE_TEXT,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                AssistantTurnText(
                    text = bodyText,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (showEmptyFallback) {
            AssistantStatusText(
                text = bodyText.ifBlank { EMPTY_RESPONSE_TEXT },
            )
        }
        audioAttachment?.takeIf { showVoiceToggle }?.let { attachment ->
            VoiceReplyDisplayToggle(
                showAsText = resolvedShowVoiceAsText,
                voiceScript = voiceScript,
                audioPath = attachment.path,
                autoPlay = autoPlayVoiceReplies && !resolvedShowVoiceAsText,
                playbackStopToken = playbackStopToken,
                onAutoPlayStarted = onVoiceAutoPlayStarted,
                onPlaybackFinished = onVoicePlaybackFinished,
                onPlaybackPaused = onVoicePlaybackPaused,
            )
            if (resolvedShowVoiceAsText && autoPlayVoiceReplies) {
                AudioPlaybackRow(
                    path = attachment.path,
                    autoPlay = true,
                    showUi = false,
                    playbackStopToken = playbackStopToken,
                    onAutoPlayStarted = onVoiceAutoPlayStarted,
                    onPlaybackFinished = onVoicePlaybackFinished,
                    onPlaybackPaused = onVoicePlaybackPaused,
                )
            }
        }
        AssistantAttachments(
            attachments = attachments,
            onViewAttachment = onViewAttachment,
            hideAudio = showVoiceToggle,
            autoPlayVoiceReplies = autoPlayVoiceReplies,
            playbackStopToken = playbackStopToken,
            onVoiceAutoPlayStarted = onVoiceAutoPlayStarted,
            onVoicePlaybackFinished = onVoicePlaybackFinished,
            onVoicePlaybackPaused = onVoicePlaybackPaused,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun VoiceReplyDisplayToggle(
    showAsText: Boolean,
    voiceScript: String,
    audioPath: String,
    autoPlay: Boolean = false,
    playbackStopToken: Int = 0,
    onAutoPlayStarted: () -> Unit = {},
    onPlaybackFinished: () -> Unit = {},
    onPlaybackPaused: () -> Unit = {},
) {
    AnimatedContent(
        targetState = showAsText,
        transitionSpec = {
            val enter = fadeIn(tween(PagerAnimMs, easing = FastOutSlowInEasing)) +
                expandVertically(
                    expandFrom = Alignment.Top,
                    animationSpec = tween(PagerAnimMs, easing = FastOutSlowInEasing),
                )
            val exit = fadeOut(tween(PagerAnimMs / 2, easing = FastOutSlowInEasing)) +
                shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = tween(PagerAnimMs / 2, easing = FastOutSlowInEasing),
                )
            (enter togetherWith exit).using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.TopStart,
        label = "voiceReplyDisplayToggle",
    ) { asText ->
        if (asText) {
            AssistantTurnText(
                text = voiceScript,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            key(audioPath) {
                AudioPlaybackRow(
                    path = audioPath,
                    autoPlay = autoPlay,
                    playbackStopToken = playbackStopToken,
                    onAutoPlayStarted = onAutoPlayStarted,
                    onPlaybackFinished = onPlaybackFinished,
                    onPlaybackPaused = onPlaybackPaused,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun MessageActionButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val tint = MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (enabled) 0.75f else 0.3f,
    )
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AssistantAttachments(
    attachments: List<ChatAttachment>,
    onViewAttachment: (ChatAttachment) -> Unit,
    hideAudio: Boolean = false,
    autoPlayVoiceReplies: Boolean = false,
    playbackStopToken: Int = 0,
    onVoiceAutoPlayStarted: () -> Unit = {},
    onVoicePlaybackFinished: () -> Unit = {},
    onVoicePlaybackPaused: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val images = attachments.filter { it.isImage }
    val videos = attachments.filter { it.isVideo }
    val audio = if (hideAudio) {
        emptyList()
    } else {
        attachments.filter { it.mimeType?.startsWith("audio/", ignoreCase = true) == true }
    }
    if (images.isEmpty() && videos.isEmpty() && audio.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        images.forEach { attachment ->
            AssistantImageOutput(
                attachment = attachment,
                onClick = { onViewAttachment(attachment) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        videos.forEach { attachment ->
            AssistantVideoOutput(
                attachment = attachment,
                onClick = { onViewAttachment(attachment) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        audio.forEach { attachment ->
            key(attachment.path) {
                AudioPlaybackRow(
                    path = attachment.path,
                    autoPlay = autoPlayVoiceReplies,
                    playbackStopToken = playbackStopToken,
                    onAutoPlayStarted = onVoiceAutoPlayStarted,
                    onPlaybackFinished = onVoicePlaybackFinished,
                    onPlaybackPaused = onVoicePlaybackPaused,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AssistantImageOutput(
    attachment: ChatAttachment,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = File(attachment.path),
        contentDescription = attachment.displayName,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .widthIn(max = 320.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick),
    )
}
