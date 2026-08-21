package tedwester.convo.ui.chat.composer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import tedwester.convo.ui.input.ConvoKeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import tedwester.convo.features.chat.model.ChatAttachment
import tedwester.convo.ui.chat.HintPopupFadeTokens
import tedwester.convo.ui.chat.attachments.AttachmentPreviewStrip
import tedwester.convo.ui.chat.composerHintHighlightRing
import tedwester.convo.ui.components.ConvoIconButton
import tedwester.convo.ui.components.ConvoRingGapTokens
import tedwester.convo.ui.components.convoRingColor
import tedwester.convo.ui.components.convoRingGapSurface
import tedwester.convo.ui.icons.ConvoIcons
import tedwester.convo.ui.theme.DarkChatBox
import tedwester.convo.ui.theme.LightChatBox

private val HintControlFadeIn = tween<Float>(
    durationMillis = HintPopupFadeTokens.FadeInMs,
    easing = FastOutSlowInEasing,
)
private val HintControlFadeOut = tween<Float>(
    durationMillis = HintPopupFadeTokens.FadeOutMs,
    easing = FastOutSlowInEasing,
)

private const val ComposerMaxLines = 6

private val ComposerExpandSpring = spring<IntSize>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

private val ComposerToolbarLayoutAnimation = tween<IntSize>(
    durationMillis = 220,
    easing = FastOutSlowInEasing,
)

private const val EditBarAnimMs = 200
private val EditBarHeight = 32.dp

@Composable
private fun ComposerToolbarSlot(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    if (visible) {
        content()
    }
}

@Composable
private fun ComposerTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    isRecording: Boolean,
    isTranscribing: Boolean,
    isVoiceSession: Boolean,
    isRunning: Boolean,
    isAwaitingVoicePlayback: Boolean,
    dark: Boolean,
    textStyle: TextStyle,
    placeholder: String,
) {
    val textFieldState = rememberTextFieldState(initialText = value)
    val status = when {
        isTranscribing -> "Transcribing..."
        isRecording -> "Listening..."
        isVoiceSession && isRunning -> "Thinking..."
        isVoiceSession && isAwaitingVoicePlayback -> "Speaking..."
        isVoiceSession -> "Listening..."
        else -> placeholder
    }

    LaunchedEffect(value) {
        val current = textFieldState.text.toString()
        if (current != value) {
            textFieldState.edit {
                replace(0, length, value)
                selection = TextRange(value.length)
            }
        }
    }

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .distinctUntilChanged()
            .collect { text ->
                if (text != value) {
                    onValueChange(text)
                }
            }
    }

    BasicTextField(
        state = textFieldState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        enabled = enabled,
        textStyle = textStyle,
        keyboardOptions = ConvoKeyboardOptions.Text,
        cursorBrush = SolidColor(textStyle.color),
        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = ComposerMaxLines),
        decorator = { innerTextField ->
            Box(modifier = Modifier.fillMaxWidth()) {
                if (textFieldState.text.isEmpty()) {
                    Text(
                        text = status,
                        style = textStyle.copy(
                            color = if (dark) Color(0xFF6E6E6E) else Color(0xFF9AA3B1),
                        ),
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Ask Anything...",
    onOpenAttachOptions: () -> Unit = {},
    isAttachOptionsOpen: Boolean = false,
    showAttachButton: Boolean = true,
    showVoiceSelector: Boolean = false,
    voiceLabel: String = "Default",
    isVoiceSelectorOpen: Boolean = false,
    onOpenVoiceSelector: () -> Unit = {},
    showSearchToggle: Boolean = false,
    isSearchEnabled: Boolean = false,
    onToggleSearch: () -> Unit = {},
    showReasoningToggle: Boolean = false,
    isReasoningEnabled: Boolean = false,
    canDisableReasoning: Boolean = true,
    onToggleReasoning: () -> Unit = {},
    onOpenReasoningSettings: () -> Unit = {},
    hasSystemMessage: Boolean = false,
    onOpenSystemMessage: () -> Unit = {},
    isSystemMessageOpen: Boolean = false,
    isRunning: Boolean = false,
    applyImePadding: Boolean = true,
    supportsVoiceInput: Boolean = true,
    isRecording: Boolean = false,
    isVoiceTranscribing: Boolean = false,
    isVoiceSession: Boolean = false,
    isAwaitingVoicePlayback: Boolean = false,
    recordingAmplitudes: List<Float> = emptyList(),
    recordingElapsedMs: Long = 0L,
    onMicClick: () -> Unit = {},
    onStopVoiceSession: () -> Unit = {},
    isDictating: Boolean = false,
    isTranscribing: Boolean = false,
    dictationAmplitudes: List<Float> = emptyList(),
    dictationScrollPhase: Float = 0f,
    onDictationMicClick: () -> Unit = {},
    onCancelDictation: () -> Unit = {},
    onConfirmDictation: () -> Unit = {},
    showDictationButton: Boolean = true,
    attachments: List<ChatAttachment> = emptyList(),
    onRemoveAttachment: (String) -> Unit = {},
    showComposerHints: Boolean = false,
    onComposerHintsFinished: () -> Unit = {},
    micOnlyMode: Boolean = false,
    isEditingMessage: Boolean = false,
    onCancelEdit: () -> Unit = {},
) {
    val dark = isSystemInDarkTheme()
    val boxColor = if (dark) DarkChatBox else LightChatBox
    val editingBarColor = if (dark) Color(0xFF262524) else Color(0xFFD4D8E0)
    val canSend = !micOnlyMode && (
        isEditingMessage ||
            value.isNotBlank() ||
            attachments.isNotEmpty()
        )
    val outerRadius = ConvoRingGapTokens.ComposerOuterRadius
    val ringGap = ConvoRingGapTokens.Gap
    val outerShape = RoundedCornerShape(outerRadius)
    val innerShape = RoundedCornerShape((outerRadius - ringGap).coerceAtLeast(0.dp))
    val ringColor = convoRingColor()
    val pageBackground = MaterialTheme.colorScheme.background

    val haptics = LocalHapticFeedback.current
    val dictationProgress by animateFloatAsState(
        targetValue = if (isDictating) 1f else 0f,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "dictationOverlay",
    )
    val editBarProgress by animateFloatAsState(
        targetValue = if (isEditingMessage) 1f else 0f,
        animationSpec = tween(EditBarAnimMs, easing = FastOutSlowInEasing),
        label = "editBarProgress",
    )
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onBackground,
    )

    var hintStep by rememberSaveable { mutableStateOf<String?>(null) }
    val activeHint = hintStep?.let { step ->
        ComposerHint.entries.find { it.name == step }
    }?.takeIf { showComposerHints }

    LaunchedEffect(showComposerHints) {
        if (!showComposerHints) return@LaunchedEffect
        if (hintStep != null) return@LaunchedEffect
        delay(800)
        hintStep = ComposerHint.Search.name
    }

    fun advanceComposerHint() {
        hintStep = when (hintStep) {
            ComposerHint.Search.name -> ComposerHint.Reasoning.name
            ComposerHint.Reasoning.name -> ComposerHint.SystemMessage.name
            ComposerHint.SystemMessage.name -> {
                if (showDictationButton) {
                    ComposerHint.Dictation.name
                } else if (!micOnlyMode) {
                    ComposerHint.Voice.name
                } else {
                    onComposerHintsFinished()
                    null
                }
            }
            ComposerHint.Dictation.name -> {
                if (!micOnlyMode) ComposerHint.Voice.name else {
                    onComposerHintsFinished()
                    null
                }
            }
            else -> {
                onComposerHintsFinished()
                null
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .then(if (applyImePadding) Modifier.imePadding() else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        AnimatedVisibility(
            visible = attachments.isNotEmpty() && !isVoiceSession,
            enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { it / 3 },
            exit = fadeOut(tween(120)),
        ) {
            AttachmentPreviewStrip(
                attachments = attachments,
                onRemove = onRemoveAttachment,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
            )
        }

        val showVoiceOrb = isVoiceSession && !isDictating
        val orbSlotHeight by animateDpAsState(
            targetValue = if (showVoiceOrb) VoiceOrbSlotHeight else 0.dp,
            animationSpec = VoiceOrbSlotAnimation,
            label = "voiceOrbSlotHeight",
        )
        val orbRevealProgress by remember {
            derivedStateOf {
                (orbSlotHeight.value / VoiceOrbSlotHeight.value).coerceIn(0f, 1f)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(orbSlotHeight),
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (orbSlotHeight > 0.dp) {
                VoiceOrb(
                    amplitudes = recordingAmplitudes,
                    mode = when {
                        isRecording -> VoiceOrbMode.Listening
                        isVoiceTranscribing -> VoiceOrbMode.Transcribing
                        else -> VoiceOrbMode.Waiting
                    },
                    onTap = onStopVoiceSession,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = VoiceOrbBottomGap)
                        .graphicsLayer {
                            alpha = orbRevealProgress
                            val scale = 0.92f + 0.08f * orbRevealProgress
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .convoRingGapSurface(
                    outerShape = outerShape,
                    innerShape = innerShape,
                    fillColor = pageBackground,
                    gapColor = pageBackground,
                    ringColor = ringColor,
                    ringGap = ringGap,
                ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (editBarProgress > 0f) {
                    val editMuted = if (dark) Color(0xFF9AA3B1) else Color(0xFF5A6472)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(EditBarHeight * editBarProgress)
                            .clipToBounds()
                            .graphicsLayer { alpha = editBarProgress }
                            .background(editingBarColor),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(EditBarHeight)
                                .padding(start = 14.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                painter = ConvoIcons.Pencil(),
                                contentDescription = null,
                                tint = editMuted,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "Editing message",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = editMuted,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            ConvoIconButton(
                                painter = ConvoIcons.X(),
                                contentDescription = "Cancel edit",
                                onClick = onCancelEdit,
                                size = 24.dp,
                                iconSize = 13.dp,
                                showBorder = false,
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = ComposerExpandSpring,
                            alignment = Alignment.BottomCenter,
                        )
                        .heightIn(min = 96.dp)
                        .background(boxColor)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = 1f - dictationProgress },
            ) {
            ComposerTextInput(
                value = value,
                onValueChange = onValueChange,
                enabled = !isVoiceSession && !isDictating && !micOnlyMode,
                isRecording = isRecording,
                isTranscribing = isVoiceTranscribing,
                isVoiceSession = isVoiceSession,
                isRunning = isRunning,
                isAwaitingVoicePlayback = isAwaitingVoicePlayback,
                dark = dark,
                textStyle = textStyle,
                placeholder = placeholder,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier
                        .animateContentSize(ComposerToolbarLayoutAnimation)
                        .clip(RectangleShape),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ComposerToolbarSlot(visible = showAttachButton) {
                        AttachmentAddButton(
                            isOpen = isAttachOptionsOpen,
                            isRecording = isVoiceSession || isDictating,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpenAttachOptions()
                            },
                        )
                    }

                    ComposerToolbarSlot(visible = showVoiceSelector) {
                        VoiceSelectorButton(
                            label = voiceLabel,
                            isOpen = isVoiceSelectorOpen,
                            isRecording = isVoiceSession || isDictating,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpenVoiceSelector()
                            },
                        )
                    }

                    ComposerToolbarSlot(visible = showSearchToggle) {
                        ComposerHintAnchor(
                            hint = ComposerHint.Search,
                            activeHint = activeHint,
                            onAdvance = ::advanceComposerHint,
                        ) {
                            SearchToggleButton(
                                dark = dark,
                                enabled = isSearchEnabled,
                                isRecording = isVoiceSession || isDictating,
                                highlighted = activeHint == ComposerHint.Search,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleSearch()
                                },
                            )
                        }
                    }

                    ComposerToolbarSlot(visible = showReasoningToggle) {
                        ComposerHintAnchor(
                            hint = ComposerHint.Reasoning,
                            activeHint = activeHint,
                            onAdvance = ::advanceComposerHint,
                        ) {
                            ReasoningToggleButton(
                                dark = dark,
                                enabled = isReasoningEnabled,
                                canDisable = canDisableReasoning,
                                isRecording = isVoiceSession || isDictating,
                                highlighted = activeHint == ComposerHint.Reasoning,
                                onToggle = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleReasoning()
                                },
                                onLongPress = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onOpenReasoningSettings()
                                },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .animateContentSize(ComposerToolbarLayoutAnimation)
                        .clip(RectangleShape),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ComposerHintAnchor(
                        hint = ComposerHint.SystemMessage,
                        activeHint = activeHint,
                        onAdvance = ::advanceComposerHint,
                    ) {
                        SystemMessageButton(
                            dark = dark,
                            hasMessage = hasSystemMessage,
                            isOpen = isSystemMessageOpen,
                            isRecording = isVoiceSession || isDictating,
                            highlighted = activeHint == ComposerHint.SystemMessage,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpenSystemMessage()
                            },
                        )
                    }

                    ComposerToolbarSlot(visible = showDictationButton) {
                        ComposerHintAnchor(
                            hint = ComposerHint.Dictation,
                            activeHint = activeHint,
                            onAdvance = ::advanceComposerHint,
                        ) {
                            DictationMicButton(
                                isRecording = isVoiceSession || isDictating,
                                highlighted = activeHint == ComposerHint.Dictation,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDictationMicClick()
                                },
                            )
                        }
                    }

                    if (micOnlyMode) {
                        ActionButton(
                            dark = dark,
                            canSend = false,
                            isRunning = isRunning,
                            isRecording = isRecording,
                            supportsVoiceInput = true,
                            forceWaveform = false,
                            highlighted = false,
                            micOnlyMode = true,
                            onSend = {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                onSend()
                            },
                            onMicClick = {
                                if (isDictating || isRunning) return@ActionButton
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                onMicClick()
                            },
                        )
                    } else {
                        ComposerHintAnchor(
                            hint = ComposerHint.Voice,
                            activeHint = activeHint,
                            onAdvance = ::advanceComposerHint,
                        ) {
                            ActionButton(
                                dark = dark,
                                canSend = canSend && !isVoiceSession,
                                isRunning = isRunning && !isVoiceSession,
                                isRecording = isRecording || isVoiceSession,
                                supportsVoiceInput = supportsVoiceInput && !isDictating,
                                forceWaveform = activeHint == ComposerHint.Voice,
                                highlighted = activeHint == ComposerHint.Voice,
                                micOnlyMode = false,
                                onSend = {
                                    if (isDictating || isVoiceSession) return@ActionButton
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    onSend()
                                },
                                onMicClick = {
                                    if (isDictating) return@ActionButton
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    if (isVoiceSession) onStopVoiceSession() else onMicClick()
                                },
                            )
                        }
                    }
                }
            }
            }

            if (dictationProgress > 0.001f) {
                DictationOverlay(
                    isTranscribing = isTranscribing,
                    amplitudes = dictationAmplitudes,
                    scrollPhase = dictationScrollPhase,
                    dark = dark,
                    onCancel = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCancelDictation()
                    },
                    onConfirm = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onConfirmDictation()
                    },
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = dictationProgress },
                )
            }
                }
            }
        }
    }
}

@Composable
private fun VoiceSelectorButton(
    label: String,
    isOpen: Boolean,
    isRecording: Boolean,
    onClick: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val defaultFill = if (dark) {
        Color.White.copy(alpha = 0.025f)
    } else {
        Color.Black.copy(alpha = 0.03f)
    }
    val defaultBorder = if (dark) {
        Color.White.copy(alpha = 0.055f)
    } else {
        Color.Black.copy(alpha = 0.055f)
    }
    val activeFill = if (dark) Color.White else MaterialTheme.colorScheme.primary
    val activeContent = if (dark) Color.Black else MaterialTheme.colorScheme.onPrimary
    val idleContent = if (dark) Color(0xFF6E6E6E) else Color(0xFF9AA3B1)

    val containerColor by animateColorAsState(
        targetValue = if (isOpen) activeFill else defaultFill,
        animationSpec = tween(240),
        label = "voiceSelectorContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isOpen) activeContent else idleContent,
        animationSpec = tween(240),
        label = "voiceSelectorContent",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isOpen) Color.Transparent else defaultBorder,
        animationSpec = tween(240),
        label = "voiceSelectorBorder",
    )

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "voiceSelectorScale",
    )
    val shape = RoundedCornerShape(999.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .widthIn(min = 72.dp, max = 132.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(containerColor)
            .border(Dp.Hairline, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isRecording,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Icon(
            painter = ConvoIcons.AudioLines(),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun AttachmentAddButton(
    isOpen: Boolean,
    isRecording: Boolean,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isOpen) 135f else 0f,
        animationSpec = spring(
            dampingRatio = 0.6f,
            stiffness = 360f,
        ),
        label = "attachRotation",
    )

    ConvoIconButton(
        painter = ConvoIcons.Add(),
        contentDescription = "Add attachment",
        onClick = onClick,
        enabled = !isRecording,
        size = 36.dp,
        iconSize = 18.dp,
        iconRotation = rotation,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReasoningToggleButton(
    dark: Boolean,
    enabled: Boolean,
    canDisable: Boolean,
    isRecording: Boolean,
    highlighted: Boolean = false,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
) {
    ComposerToggleButton(
        dark = dark,
        active = enabled,
        isRecording = isRecording,
        highlighted = highlighted,
        onClick = {
            if (!enabled || canDisable) {
                onToggle()
            }
        },
        onLongClick = onLongPress,
        painter = ConvoIcons.Brain(),
        contentDescription = when {
            !enabled -> "Enable reasoning"
            canDisable -> "Disable reasoning"
            else -> "Reasoning always on — hold for settings"
        },
        containerColorLabel = "reasoningContainerColor",
        iconColorLabel = "reasoningIconColor",
        borderColorLabel = "reasoningBorderColor",
        scaleLabel = "reasoningBtnScale",
    )
}

@Composable
private fun SearchToggleButton(
    dark: Boolean,
    enabled: Boolean,
    isRecording: Boolean,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    ComposerToggleButton(
        dark = dark,
        active = enabled,
        isRecording = isRecording,
        highlighted = highlighted,
        onClick = onClick,
        painter = ConvoIcons.Search(),
        contentDescription = if (enabled) "Disable web search" else "Enable web search",
        containerColorLabel = "searchContainerColor",
        iconColorLabel = "searchIconColor",
        borderColorLabel = "searchBorderColor",
        scaleLabel = "searchBtnScale",
    )
}

@Composable
private fun SystemMessageButton(
    dark: Boolean,
    hasMessage: Boolean,
    isOpen: Boolean,
    isRecording: Boolean,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    ComposerToggleButton(
        dark = dark,
        active = hasMessage || isOpen,
        isRecording = isRecording,
        highlighted = highlighted,
        onClick = onClick,
        painter = ConvoIcons.MonitorCog(),
        contentDescription = "System message",
        containerColorLabel = "systemMessageContainerColor",
        iconColorLabel = "systemMessageIconColor",
        borderColorLabel = "systemMessageBorderColor",
        scaleLabel = "systemMessageBtnScale",
    )
}

@Composable
private fun DictationMicButton(
    isRecording: Boolean,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    ConvoIconButton(
        painter = ConvoIcons.Mic(),
        contentDescription = "Dictate",
        onClick = onClick,
        enabled = !isRecording,
        size = 36.dp,
        iconSize = 18.dp,
        showBorder = !highlighted,
        modifier = Modifier.composerHintHighlightRing(highlighted),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ComposerToggleButton(
    dark: Boolean,
    active: Boolean,
    isRecording: Boolean,
    onClick: () -> Unit,
    painter: Painter,
    contentDescription: String,
    containerColorLabel: String,
    iconColorLabel: String,
    borderColorLabel: String,
    scaleLabel: String,
    highlighted: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val defaultFill = if (dark) {
        Color.White.copy(alpha = 0.025f)
    } else {
        Color.Black.copy(alpha = 0.03f)
    }
    val defaultBorder = if (dark) {
        Color.White.copy(alpha = 0.055f)
    } else {
        Color.Black.copy(alpha = 0.055f)
    }
    val activeFill = if (dark) Color.White else MaterialTheme.colorScheme.primary
    val activeIcon = if (dark) Color.Black else MaterialTheme.colorScheme.onPrimary
    val idleIcon = if (dark) Color(0xFF6E6E6E) else Color(0xFF9AA3B1)

    val containerColor by animateColorAsState(
        targetValue = if (active) activeFill else defaultFill,
        animationSpec = tween(240),
        label = containerColorLabel,
    )
    val iconColor by animateColorAsState(
        targetValue = if (active) activeIcon else idleIcon,
        animationSpec = tween(240),
        label = iconColorLabel,
    )
    val borderColor by animateColorAsState(
        targetValue = if (active && !highlighted) Color.Transparent else defaultBorder,
        animationSpec = tween(240),
        label = borderColorLabel,
    )

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = scaleLabel,
    )
    val clickEnabled = !isRecording

    Box(
        modifier = Modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(containerColor)
            .then(
                if (!highlighted && !active) {
                    Modifier.border(Dp.Hairline, borderColor, CircleShape)
                } else {
                    Modifier
                },
            )
            .composerHintHighlightRing(highlighted)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = clickEnabled,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = clickEnabled,
                        onClick = onClick,
                    )
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(18.dp),
        )
    }
}
