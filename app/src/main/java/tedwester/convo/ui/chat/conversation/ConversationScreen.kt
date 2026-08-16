package tedwester.convo.ui.chat.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import tedwester.convo.ConvoApp
import tedwester.convo.core.network.OpenRouterApi
import tedwester.convo.core.network.model.ModelKind
import tedwester.convo.core.security.KeyStorage
import tedwester.convo.features.chat.ChatState
import tedwester.convo.features.chat.data.ComposerPreferences
import tedwester.convo.features.chat.data.VoicePreferences
import tedwester.convo.features.chat.model.ChatAttachment
import tedwester.convo.ui.chat.attachments.ImageViewerDialog
import tedwester.convo.ui.chat.attachments.VideoViewerDialog
import tedwester.convo.ui.chat.composer.InputBar
import tedwester.convo.ui.chat.message.MessageList
import tedwester.convo.ui.chat.message.bottomAnchoredListState

@Composable
fun ConversationScreen(
    apiKey: String,
    api: OpenRouterApi,
    keyStorage: KeyStorage,
    chatState: ChatState,
    isSurfaceActive: Boolean,
    onOpenMenu: () -> Unit,
    voicePreferences: VoicePreferences = VoicePreferences(),
    composerPreferences: ComposerPreferences = ComposerPreferences(),
) {
    val bgColor = MaterialTheme.colorScheme.background
    val context = LocalContext.current
    val app = context.applicationContext as ConvoApp
    val composerHintsStore = app.composerHintsStore
    var composerHintsPending by remember {
        mutableStateOf(!composerHintsStore.hasSeen())
    }

    var showAttachmentOptions by rememberSaveable { mutableStateOf(false) }
    var pendingAttachmentOptions by remember { mutableStateOf(false) }
    var showModelSelector by rememberSaveable { mutableStateOf(false) }
    var pendingModelSelector by remember { mutableStateOf(false) }
    var showSystemMessage by rememberSaveable { mutableStateOf(false) }
    var pendingSystemMessage by remember { mutableStateOf(false) }
    var showReasoningSettings by rememberSaveable { mutableStateOf(false) }
    var pendingReasoningSettings by remember { mutableStateOf(false) }
    var showVoiceSelector by rememberSaveable { mutableStateOf(false) }
    var pendingVoiceSelector by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissKeyboard = rememberDismissKeyboard()
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    val attachmentLaunchers = rememberConversationAttachmentLaunchers(
        chatState = chatState,
        onAttachmentIngested = { showAttachmentOptions = false },
    )

    val dictationCancelRef = remember { mutableStateOf({}) }

    val voiceRecording = rememberConversationVoiceRecording(
        chatState = chatState,
        isRunning = chatState.isRunning,
        transcriptionOnly = chatState.selectedModel?.usesTranscriptionComposer == true,
        onRecordingStarted = {
            dictationCancelRef.value()
            showAttachmentOptions = false
            showSystemMessage = false
            pendingSystemMessage = false
        },
    )

    val dictation = rememberConversationDictation(
        chatState = chatState,
        onBeforeStart = {
            if (voiceRecording.isOrbVisible) voiceRecording.cancelRecording()
            showAttachmentOptions = false
            showSystemMessage = false
            pendingSystemMessage = false
        },
    )
    SideEffect {
        dictationCancelRef.value = dictation.cancel
    }

    LaunchedEffect(composerPreferences.showDictationButton) {
        if (!composerPreferences.showDictationButton) {
            dictation.cancel()
        }
    }

    LaunchedEffect(chatState.selectedModel?.id, chatState.selectedModel?.supportsComposerAttachments) {
        if (chatState.selectedModel?.supportsComposerAttachments == false) {
            showAttachmentOptions = false
            pendingAttachmentOptions = false
        }
        if (voiceRecording.isRecording || voiceRecording.isOrbVisible) {
            voiceRecording.cancelRecording()
        }
        if (chatState.selectedModel?.usesTranscriptionComposer == true) {
            dictation.cancel()
        }
    }

    LaunchedEffect(isSurfaceActive) {
        if (isSurfaceActive) chatState.reconcile()
    }

    ConversationSurfaceInactiveEffect(
        isSurfaceActive = isSurfaceActive,
        isRecording = voiceRecording.isOrbVisible || voiceRecording.isRecording || dictation.isActive,
        cancelRecording = {
            voiceRecording.cancelRecording()
            dictation.cancel()
        },
        onDismissAllOverlays = {
            showAttachmentOptions = false
            pendingAttachmentOptions = false
            showModelSelector = false
            pendingModelSelector = false
            showSystemMessage = false
            pendingSystemMessage = false
            showReasoningSettings = false
            pendingReasoningSettings = false
            showVoiceSelector = false
            pendingVoiceSelector = false
        },
    )

    ConversationPendingModalEffects(
        isRecording = voiceRecording.isOrbVisible,
        cancelRecording = {
            voiceRecording.cancelRecording()
            dictation.cancel()
        },
        pendingModelSelector = pendingModelSelector,
        onShowModelSelector = { showModelSelector = true },
        onClearPendingModelSelector = { pendingModelSelector = false },
        pendingAttachmentOptions = pendingAttachmentOptions,
        onShowAttachmentOptions = { showAttachmentOptions = true },
        onClearPendingAttachmentOptions = { pendingAttachmentOptions = false },
        pendingSystemMessage = pendingSystemMessage,
        onShowSystemMessage = { showSystemMessage = true },
        onClearPendingSystemMessage = { pendingSystemMessage = false },
        pendingReasoningSettings = pendingReasoningSettings,
        onShowReasoningSettings = { showReasoningSettings = true },
        onClearPendingReasoningSettings = { pendingReasoningSettings = false },
        pendingVoiceSelector = pendingVoiceSelector,
        onShowVoiceSelector = { showVoiceSelector = true },
        onClearPendingVoiceSelector = { pendingVoiceSelector = false },
    )

    val requestModelSelector = {
        if (!pendingModelSelector && !showModelSelector) {
            dictation.cancel()
            showAttachmentOptions = false
            pendingAttachmentOptions = false
            showSystemMessage = false
            pendingSystemMessage = false
            pendingModelSelector = true
        }
    }

    var modelHintPending by remember {
        mutableStateOf(!composerHintsStore.hasSeenModelHint())
    }
    val showModelHint = modelHintPending &&
        !composerHintsPending &&
        chatState.selectedModel == null &&
        isSurfaceActive &&
        !showModelSelector &&
        !pendingModelSelector &&
        !showAttachmentOptions &&
        !showSystemMessage &&
        !showReasoningSettings &&
        !showVoiceSelector &&
        !voiceRecording.isOrbVisible &&
        !dictation.isActive
    fun dismissModelHint() {
        composerHintsStore.markModelHintSeen()
        modelHintPending = false
    }

    val hasMessages = chatState.messages.isNotEmpty()
    var topChromeDp by remember { mutableStateOf(64.dp) }
    var bottomChromeDp by remember { mutableStateOf(96.dp) }
    var activeListState by remember { mutableStateOf<LazyListState?>(null) }
    var variantSwipeToken by remember { mutableIntStateOf(0) }
    var variantSwipeMessageId by remember { mutableLongStateOf(-1L) }
    var resumeFollowToken by remember { mutableIntStateOf(0) }
    var viewingAttachment by remember { mutableStateOf<ChatAttachment?>(null) }
    val applyComposerImePadding = !showModelSelector &&
        !showAttachmentOptions &&
        !showSystemMessage &&
        !showReasoningSettings &&
        !showVoiceSelector
    val imeBottomPx = imeInsets.getBottom(density)
    val imeBottomDp = with(density) { imeBottomPx.toDp() }
    val isKeyboardVisible = imeBottomPx > 0
    val listBottomImeDp = if (applyComposerImePadding) imeBottomDp else 0.dp

    fun startNewChat() {
        if (voiceRecording.isOrbVisible) voiceRecording.cancelRecording()
        dictation.cancel()
        dismissKeyboard()
        chatState.newChat()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
    ) {
        ConversationSessionAnimatedContent(
            sessionId = chatState.conversationSessionId,
            onSessionSettled = chatState::pruneConversationFrames,
            modifier = Modifier.fillMaxSize(),
        ) { activeSession ->
            val frame = chatState.frameFor(activeSession)
            val isLiveSession = activeSession == chatState.conversationSessionId
            val displayMessages = if (isLiveSession) chatState.messages else frame.messages
            val hasFrameMessages = displayMessages.isNotEmpty()
            val conversationKey = if (isLiveSession) chatState.currentChatId else frame.chatId
            val messageListRevision = if (isLiveSession) {
                chatState.messageListRevision
            } else {
                frame.messageListRevision
            }
            var userBubbleAnimOnMountToken by remember(activeSession) { mutableIntStateOf(0) }
            var hadFrameMessages by remember(activeSession) { mutableStateOf(hasFrameMessages) }

            LaunchedEffect(hasFrameMessages, isLiveSession) {
                if (!isLiveSession) return@LaunchedEffect
                if (hasFrameMessages && !hadFrameMessages) {
                    userBubbleAnimOnMountToken += 1
                }
                hadFrameMessages = hasFrameMessages
            }

            LaunchedEffect(activeSession) {
                if (isLiveSession) {
                    viewingAttachment = null
                }
            }

            if (hasFrameMessages) {
                key(activeSession, conversationKey, messageListRevision) {
                    val listState = bottomAnchoredListState(displayMessages.size)
                    SideEffect {
                        if (isLiveSession) {
                            activeListState = listState
                        }
                    }
                    MessageList(
                        messages = displayMessages,
                        listState = listState,
                        conversationKey = conversationKey,
                        messageListRevision = messageListRevision,
                        isRunning = chatState.isRunning && isLiveSession,
                        variantSwipeToken = if (isLiveSession) variantSwipeToken else 0,
                        variantSwipeMessageId = if (isLiveSession) variantSwipeMessageId else -1L,
                        resumeFollowToken = if (isLiveSession) resumeFollowToken else 0,
                        userBubbleAnimOnMountToken = if (isLiveSession) userBubbleAnimOnMountToken else 0,
                        onRegenerate = { id ->
                            if (!isLiveSession) return@MessageList
                            dismissKeyboard()
                            chatState.regenerate(id)
                            resumeFollowToken += 1
                        },
                        onVariantSwipe = { id, delta ->
                            if (!isLiveSession) return@MessageList
                            dismissKeyboard()
                            chatState.selectVariant(id, delta)
                            variantSwipeMessageId = id
                            variantSwipeToken += 1
                        },
                        onViewAttachment = { if (isLiveSession) viewingAttachment = it },
                        onDismissKeyboard = dismissKeyboard,
                        autoPlayVoiceReplies = voicePreferences.autoPlayVoiceReplies && isLiveSession,
                        forceAutoPlayVoiceReplies = isLiveSession &&
                            voiceRecording.isOrbVisible &&
                            !voiceRecording.isTranscribing,
                        suppressAutoPlay = !isLiveSession || voiceRecording.isTranscribing,
                        playbackStopToken = voiceRecording.playbackStopToken,
                        onVoiceAutoPlayStarted = { id ->
                            if (isLiveSession) chatState.markVoiceAutoPlayed(id)
                        },
                        onVoicePlaybackFinished = {
                            if (isLiveSession) voiceRecording.onVoicePlaybackFinished()
                        },
                        onVoicePlaybackPaused = {
                            if (isLiveSession) voiceRecording.onVoicePlaybackPaused()
                        },
                        contentPadding = PaddingValues(
                            start = 18.dp,
                            end = 18.dp,
                            top = topChromeDp + ConversationChromeMessageGap,
                            bottom = bottomChromeDp + ConversationChromeMessageGap +
                                listBottomImeDp,
                        ),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(screenHeight),
                ) {
                    EmptyChatPlaceholder(
                        modelName = chatState.selectedModel?.name,
                        visible = !isKeyboardVisible && !voiceRecording.isOrbVisible &&
                            !dictation.isActive &&
                            (isLiveSession || !frame.hasMessages),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .pointerInput(isLiveSession, dismissKeyboard) {
                                if (!isLiveSession) return@pointerInput
                                detectTapGestures(onTap = { dismissKeyboard() })
                            },
                    )
                }
            }
        }

        ConversationTopChrome(
            background = bgColor,
            enabled = hasMessages,
            onHeightChanged = { topChromeDp = it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
        ) {
            ConversationTopBar(
                modelName = chatState.selectedModel?.name,
                showModelHint = showModelHint,
                onDismissModelHint = ::dismissModelHint,
                onOpenMenu = {
                    if (voiceRecording.isOrbVisible) voiceRecording.cancelRecording()
                    dictation.cancel()
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                    showAttachmentOptions = false
                    pendingAttachmentOptions = false
                    showSystemMessage = false
                    pendingSystemMessage = false
                    onOpenMenu()
                },
                onOpenModelSelector = {
                    if (showModelHint) dismissModelHint()
                    requestModelSelector()
                },
                onNewChat = ::startNewChat,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        ConversationBottomChrome(
            background = bgColor,
            enabled = hasMessages,
            applyImePadding = applyComposerImePadding,
            onHeightChanged = { bottomChromeDp = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            val model = chatState.selectedModel
            val isTranscriptionOnly = model?.usesTranscriptionComposer == true
            val showComposerHints = composerHintsPending &&
                isSurfaceActive &&
                !voiceRecording.isOrbVisible &&
                !voiceRecording.isRecording &&
                !dictation.isActive &&
                !showModelSelector &&
                !showAttachmentOptions &&
                !showSystemMessage &&
                !showReasoningSettings &&
                !showVoiceSelector
            InputBar(
                value = chatState.input,
                onValueChange = chatState::onInputChange,
                placeholder = when {
                    isTranscriptionOnly -> "Tap mic to record..."
                    model?.modelKind == ModelKind.VideoGen -> "Describe a video..."
                    model?.modelKind == ModelKind.ImageGen -> "Describe an image..."
                    else -> "Ask Anything..."
                },
                onSend = {
                    if (chatState.isRunning) {
                        dismissKeyboard()
                        chatState.stop()
                    } else if (chatState.selectedModel == null) {
                        requestModelSelector()
                    } else {
                        dismissKeyboard()
                        chatState.send()
                    }
                },
                onOpenAttachOptions = {
                    if (pendingAttachmentOptions || showAttachmentOptions) return@InputBar
                    if (model?.supportsComposerAttachments == false) return@InputBar
                    dictation.cancel()
                    showSystemMessage = false
                    pendingSystemMessage = false
                    showReasoningSettings = false
                    pendingReasoningSettings = false
                    showVoiceSelector = false
                    pendingVoiceSelector = false
                    pendingAttachmentOptions = true
                },
                isAttachOptionsOpen = showAttachmentOptions || pendingAttachmentOptions,
                showAttachButton = !isTranscriptionOnly && model?.supportsComposerAttachments != false,
                showVoiceSelector = model?.supportsVoiceSelection == true,
                voiceLabel = model?.voiceDisplayLabel(chatState.selectedTtsVoice) ?: "Default",
                isVoiceSelectorOpen = showVoiceSelector || pendingVoiceSelector,
                onOpenVoiceSelector = {
                    if (pendingVoiceSelector || showVoiceSelector) return@InputBar
                    dictation.cancel()
                    showAttachmentOptions = false
                    pendingAttachmentOptions = false
                    showSystemMessage = false
                    pendingSystemMessage = false
                    showReasoningSettings = false
                    pendingReasoningSettings = false
                    pendingVoiceSelector = true
                },
                showSearchToggle = !isTranscriptionOnly &&
                    (model?.supportsWebSearch == true || composerHintsPending),
                isSearchEnabled = chatState.isSearchEnabled,
                onToggleSearch = chatState::toggleSearch,
                showReasoningToggle = !isTranscriptionOnly &&
                    (model?.supportsReasoning == true || composerHintsPending),
                isReasoningEnabled = chatState.reasoningPreferences.enabled ||
                    model?.requiresMandatoryReasoning == true,
                canDisableReasoning = model?.canDisableReasoning != false,
                onToggleReasoning = chatState::toggleReasoning,
                onOpenReasoningSettings = {
                    if (pendingReasoningSettings || showReasoningSettings) return@InputBar
                    dictation.cancel()
                    showAttachmentOptions = false
                    pendingAttachmentOptions = false
                    showSystemMessage = false
                    pendingSystemMessage = false
                    showVoiceSelector = false
                    pendingVoiceSelector = false
                    pendingReasoningSettings = true
                },
                hasSystemMessage = chatState.systemMessage.isNotBlank(),
                onOpenSystemMessage = {
                    if (pendingSystemMessage || showSystemMessage) return@InputBar
                    dictation.cancel()
                    showAttachmentOptions = false
                    pendingAttachmentOptions = false
                    showReasoningSettings = false
                    pendingReasoningSettings = false
                    showVoiceSelector = false
                    pendingVoiceSelector = false
                    pendingSystemMessage = true
                },
                isSystemMessageOpen = showSystemMessage || pendingSystemMessage,
                isRunning = chatState.isRunning,
                applyImePadding = false,
                supportsVoiceInput = isTranscriptionOnly || model?.supportsConversationOrb == true,
                micOnlyMode = isTranscriptionOnly,
                isRecording = voiceRecording.isRecording,
                isVoiceTranscribing = voiceRecording.isTranscribing ||
                    (isTranscriptionOnly && chatState.isRunning),
                isVoiceSession = voiceRecording.isOrbVisible,
                isAwaitingVoicePlayback = voiceRecording.isAwaitingVoicePlayback,
                recordingAmplitudes = voiceRecording.recordingAmplitudes,
                recordingElapsedMs = voiceRecording.recordingElapsedMs,
                onMicClick = voiceRecording.onMicClick,
                onStopVoiceSession = voiceRecording.cancelRecording,
                isDictating = dictation.isActive,
                isTranscribing = dictation.isTranscribing,
                dictationAmplitudes = dictation.amplitudes,
                dictationScrollPhase = dictation.scrollPhase,
                onDictationMicClick = dictation.onMicClick,
                onCancelDictation = dictation.cancel,
                onConfirmDictation = dictation.confirm,
                showDictationButton = !isTranscriptionOnly && composerPreferences.showDictationButton,
                attachments = chatState.pendingAttachments,
                onRemoveAttachment = chatState::removeAttachment,
                showComposerHints = showComposerHints,
                onComposerHintsFinished = {
                    composerHintsStore.markSeen()
                    composerHintsPending = false
                },
            )
        }

        activeListState?.let { listState ->
            if (composerPreferences.showScrollToTopButton ||
                composerPreferences.showScrollToBottomButton
            ) {
                ConversationScrollButtons(
                    listState = listState,
                    bottomInset = bottomChromeDp,
                    showTop = composerPreferences.showScrollToTopButton,
                    showBottom = composerPreferences.showScrollToBottomButton,
                    hasMessages = hasMessages,
                    applyImePadding = applyComposerImePadding,
                    onResumeFollow = { resumeFollowToken += 1 },
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }

        viewingAttachment?.takeIf { attachment ->
            chatState.messages.any { message ->
                message.attachments.any { it.id == attachment.id } ||
                    message.attachmentVariants.any { group ->
                        group.any { it.id == attachment.id }
                    }
            }
        }?.let { attachment ->
            if (attachment.isVideo) {
                VideoViewerDialog(
                    attachment = attachment,
                    onDismiss = { viewingAttachment = null },
                )
            } else {
                ImageViewerDialog(
                    attachment = attachment,
                    onDismiss = { viewingAttachment = null },
                )
            }
        }
    }

    ConversationScreenOverlays(
        apiKey = apiKey,
        api = api,
        keyStorage = keyStorage,
        chatState = chatState,
        showAttachmentOptions = showAttachmentOptions,
        attachmentLaunchers = attachmentLaunchers,
        onDismissAttachmentOptions = { showAttachmentOptions = false },
        showSystemMessage = showSystemMessage,
        onDismissSystemMessage = { showSystemMessage = false },
        showReasoningSettings = showReasoningSettings,
        onDismissReasoningSettings = { showReasoningSettings = false },
        showVoiceSelector = showVoiceSelector,
        onDismissVoiceSelector = { showVoiceSelector = false },
        showModelSelector = showModelSelector,
        onDismissModelSelector = { showModelSelector = false },
    )
}
