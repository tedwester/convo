package tedwester.convo.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tedwester.convo.core.network.OpenRouterApi
import tedwester.convo.core.network.OpenRouterApiKeyValidation
import tedwester.convo.core.network.model.OpenRouterKeyInfo
import tedwester.convo.core.security.AppLockManager
import tedwester.convo.features.chat.data.ApiPreferences
import tedwester.convo.features.chat.data.QuickSettingsConfig
import tedwester.convo.features.chat.data.SearchPreferences
import tedwester.convo.features.chat.data.ComposerPreferences
import tedwester.convo.features.chat.data.VoicePreferences
import tedwester.convo.features.chat.data.VoiceTtsMode
import tedwester.convo.features.chat.model.QuickSettingDescriptor
import tedwester.convo.features.chat.model.availableQuickSettings
import tedwester.convo.ui.chat.modals.ModelFilterState
import tedwester.convo.ui.chat.modals.ModelOutputFilter
import tedwester.convo.ui.chat.modals.ModelSelectorModal
import tedwester.convo.ui.components.ConvoButton
import tedwester.convo.ui.components.ConvoIconButton
import tedwester.convo.ui.components.ConvoIconButtonGap
import tedwester.convo.ui.components.ConvoPickerField
import tedwester.convo.ui.components.ConvoTextField
import tedwester.convo.ui.components.ConvoToggle
import tedwester.convo.ui.components.PasswordVisibilityToggle
import tedwester.convo.ui.components.VoiceModeChip
import tedwester.convo.ui.components.rememberAnimatedPasswordReveal
import tedwester.convo.ui.icons.ConvoIcons
import tedwester.convo.ui.input.ConvoKeyboardOptions
import tedwester.convo.ui.input.rememberDismissKeyboard
import tedwester.convo.ui.theme.ConvoModalTokens
import java.util.Locale

private const val SettingsAnimMs = 240
private const val HelpPageAnimMs = 300
private const val ModelFetchRetryMs = 2_000L
private const val OpenRouterLogsUrl = "https://openrouter.ai/logs"

/**
 * Full-screen settings overlay — fade only, matching chat search presentation.
 */
@Composable
fun SettingsScreen(
    apiKey: String,
    api: OpenRouterApi,
    appLockManager: AppLockManager,
    biometricLockEnabled: Boolean,
    onBiometricLockChanged: (Boolean) -> Unit,
    voicePreferences: VoicePreferences,
    onVoicePreferencesChanged: (VoicePreferences) -> Unit,
    searchPreferences: SearchPreferences,
    onSearchPreferencesChanged: (SearchPreferences) -> Unit,
    composerPreferences: ComposerPreferences = ComposerPreferences(),
    onComposerPreferencesChanged: (ComposerPreferences) -> Unit = {},
    quickSettingsConfig: QuickSettingsConfig = QuickSettingsConfig(),
    onQuickSettingsChanged: (QuickSettingsConfig) -> Unit = {},
    apiPreferences: ApiPreferences = ApiPreferences(),
    onApiPreferencesChanged: (ApiPreferences) -> Unit = {},
    onApiTimeoutChanged: (Int) -> Unit = {},
    onApiKeyChanged: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val dismissKeyboard = rememberDismissKeyboard()
    val bg = MaterialTheme.colorScheme.background

    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }

    var keyDraft by remember(apiKey) { mutableStateOf(apiKey) }
    var keyVisible by remember { mutableStateOf(false) }
    val passwordReveal = rememberAnimatedPasswordReveal(
        visible = keyVisible,
        animateReveal = keyDraft.isNotBlank(),
    )
    var saving by remember { mutableStateOf(false) }
    var checkingCredits by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var creditsError by remember { mutableStateOf<String?>(null) }
    var keyInfo by remember { mutableStateOf<OpenRouterKeyInfo?>(null) }
    var biometricLockError by remember { mutableStateOf<String?>(null) }

    var showTranscriptionPicker by remember { mutableStateOf(false) }
    var showReplyPicker by remember { mutableStateOf(false) }
    var transcriptionModelName by remember { mutableStateOf<String?>(null) }
    var replyModelName by remember { mutableStateOf<String?>(null) }

    var showQuickSettingsPicker by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var maxTokensDraft by remember(apiPreferences.maxTokens) {
        mutableStateOf(apiPreferences.maxTokens.toString())
    }
    var maxTokensError by remember { mutableStateOf<String?>(null) }
    var timeoutDisplayMinutes by remember(apiPreferences.requestTimeoutMinutes) {
        mutableStateOf(apiPreferences.requestTimeoutMinutes)
    }
    var settingsScrollEnabled by remember { mutableStateOf(true) }
    val settingsScrollState = rememberScrollState()
    val availableSettings = availableQuickSettings()
    val quickSettingDescriptorsById = remember(availableSettings) {
        availableSettings.associateBy { it.id }
    }

    val trimmedKeyDraft = keyDraft.trim()
    val isKeyDirty = trimmedKeyDraft != apiKey
    val keyFormatValid = OpenRouterApiKeyValidation.isPlausibleFormat(trimmedKeyDraft)
    val saveButtonText = when {
        !isKeyDirty && trimmedKeyDraft.isNotBlank() -> "Saved"
        else -> "Save key"
    }
    val primaryButtonTextStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp)
    val primaryButtonModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = ConvoModalTokens.ActionHorizontalInset)

    LaunchedEffect(Unit) {
        visible = true
    }

    LaunchedEffect(apiKey) {
        if (apiKey.isBlank()) {
            keyInfo = null
            creditsError = null
            checkingCredits = false
            transcriptionModelName = null
            replyModelName = null
            return@LaunchedEffect
        }
        checkingCredits = true
        creditsError = null
        try {
            keyInfo = api.fetchKeyInfo(apiKey)
        } catch (e: Exception) {
            keyInfo = null
            creditsError = e.message ?: "Could not load credits."
        } finally {
            checkingCredits = false
        }
    }

    LaunchedEffect(apiKey, voicePreferences.transcriptionModelId, voicePreferences.replyModelId) {
        if (apiKey.isBlank()) return@LaunchedEffect
        while (true) {
            val fetched = runCatching {
                api.fetchModels(
                    apiKey = apiKey,
                    query = ModelFilterState(
                        outputFilters = setOf(
                            ModelOutputFilter.Transcribe,
                            ModelOutputFilter.Chat,
                        ),
                    ).toQuery(),
                )
            }
            if (fetched.isSuccess) {
                val models = fetched.getOrThrow()
                transcriptionModelName = models
                    .find { it.id == voicePreferences.transcriptionModelId }
                    ?.name
                replyModelName = models
                    .find { it.id == voicePreferences.replyModelId }
                    ?.name
                break
            }
            delay(ModelFetchRetryMs)
        }
    }

    val transcriptionDisplayName = when {
        apiKey.isBlank() -> "Save an API key first"
        else -> formatModelDisplay(voicePreferences.transcriptionModelId, transcriptionModelName)
    }
    val replyDisplayName = when {
        apiKey.isBlank() -> "Save an API key first"
        else -> formatModelDisplay(voicePreferences.replyModelId, replyModelName)
    }

    fun finishClose() {
        if (closing) return
        closing = true
        showTranscriptionPicker = false
        showReplyPicker = false
        showQuickSettingsPicker = false
        showHelp = false
        visible = false
        scope.launch {
            delay(SettingsAnimMs.toLong())
            onClose()
        }
    }

    fun commitMaxTokens() {
        val parsed = maxTokensDraft.trim().toIntOrNull()
        if (parsed == null || parsed < 1) {
            maxTokensError = "Enter a whole number of at least 1."
            maxTokensDraft = apiPreferences.maxTokens.toString()
            return
        }
        maxTokensError = null
        dismissKeyboard()
        if (parsed != apiPreferences.maxTokens) {
            onApiPreferencesChanged(apiPreferences.copy(maxTokens = parsed))
        }
    }

    BackHandler(enabled = !closing && !showHelp) { finishClose() }

    fun saveKey() {
        val formatError = OpenRouterApiKeyValidation.formatError(trimmedKeyDraft)
        if (formatError != null) {
            saveError = formatError
            return
        }
        if (!isKeyDirty) return
        dismissKeyboard()
        saving = true
        saveError = null
        scope.launch {
            try {
                api.fetchModels(trimmedKeyDraft)
                onApiKeyChanged(trimmedKeyDraft)
            } catch (e: Exception) {
                saveError = e.message ?: "Could not verify the key. Please try again."
            } finally {
                saving = false
            }
        }
    }

    fun onBiometricLockToggle(enabled: Boolean) {
        biometricLockError = null
        if (!enabled) {
            onBiometricLockChanged(false)
            return
        }
        if (!appLockManager.canAuthenticate()) {
            biometricLockError =
                "Set up fingerprint, face unlock, or a device PIN in system settings first."
            return
        }
        appLockManager.authenticate(
            title = "Enable app lock",
            subtitle = "Confirm it's you to require biometrics when opening Convo.",
            onSuccess = { onBiometricLockChanged(true) },
            onError = { biometricLockError = it },
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(SettingsAnimMs)),
            exit = fadeOut(tween(SettingsAnimMs)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bg)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                SettingsHeader(
                    onClose = ::finishClose,
                    onOpenHelp = { showHelp = true },
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(settingsScrollState, enabled = settingsScrollEnabled)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                SettingsSection(
                    title = "OpenRouter API key",
                    description = "View or replace the key used for chat. It's stored securely on this device.",
                ) {
                ConvoTextField(
                    value = keyDraft,
                    onValueChange = {
                        keyDraft = it
                        saveError = null
                    },
                    enabled = !saving,
                    placeholder = "sk-or-v1-…",
                    visualTransformation = passwordReveal.visualTransformation,
                    textAlpha = passwordReveal.textAlpha,
                    keyboardOptions = ConvoKeyboardOptions.Password,
                    keyboardActions = KeyboardActions(
                        onDone = { if (isKeyDirty && keyFormatValid && !saving) saveKey() },
                    ),
                    trailing = {
                        PasswordVisibilityToggle(
                            visible = keyVisible,
                            onToggle = { keyVisible = !keyVisible },
                            enabled = !saving,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))
                ConvoButton(
                    text = saveButtonText,
                    onClick = ::saveKey,
                    enabled = isKeyDirty && keyFormatValid && !saving,
                    loading = saving,
                    icon = null,
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    textStyle = primaryButtonTextStyle,
                    modifier = primaryButtonModifier,
                )

                if (saveError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = saveError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = ConvoModalTokens.ActionHorizontalInset),
                    )
                }
                }

                SettingsSection(
                    title = "Credits",
                    description = "Remaining credits and usage for your saved key.",
                    showDividerAbove = true,
                ) {
                when {
                    apiKey.isBlank() -> {
                        Text(
                            text = "Save an API key to view credits.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    checkingCredits && keyInfo == null -> {
                        CreditsCardSkeleton()
                    }
                    creditsError != null -> {
                        Text(
                            text = creditsError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    keyInfo != null -> {
                        CreditsCard(info = keyInfo!!)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "View request logs at openrouter.ai/logs",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clickable { uriHandler.openUri(OpenRouterLogsUrl) }
                        .padding(vertical = 4.dp),
                )
                }

                SettingsSection(
                    title = "Security",
                    description = "Lock Convo when you leave the app and require biometrics to open it again.",
                    showDividerAbove = true,
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Biometric lock",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    ConvoToggle(
                        checked = biometricLockEnabled,
                        onCheckedChange = ::onBiometricLockToggle,
                    )
                }

                if (biometricLockError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = biometricLockError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                }

                SettingsSection(
                    title = "AI requests",
                    description = "Control how long Convo waits for replies and how long responses can be.",
                    showDividerAbove = true,
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Request timeout",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = formatTimeoutLabel(timeoutDisplayMinutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = "How long to wait for a model reply before stopping the request.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp),
                )
                RequestTimeoutSlider(
                    minutes = apiPreferences.requestTimeoutMinutes,
                    onMinutesChange = { minutes ->
                        if (minutes != apiPreferences.requestTimeoutMinutes) {
                            onApiPreferencesChanged(
                                apiPreferences.copy(requestTimeoutMinutes = minutes),
                            )
                            onApiTimeoutChanged(minutes)
                        }
                    },
                    onDisplayMinutesChange = { timeoutDisplayMinutes = it },
                    onInteractionActiveChange = { active ->
                        settingsScrollEnabled = !active
                    },
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Max tokens",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Limits reply length. Higher values may increase OpenRouter costs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
                Spacer(modifier = Modifier.height(12.dp))
                ConvoTextField(
                    value = maxTokensDraft,
                    onValueChange = { draft ->
                        if (draft.isEmpty() || draft.all { it.isDigit() }) {
                            maxTokensDraft = draft
                            maxTokensError = null
                        }
                    },
                    placeholder = ApiPreferences.DEFAULT_MAX_TOKENS.toString(),
                    keyboardOptions = ConvoKeyboardOptions.Number,
                    keyboardActions = KeyboardActions(onDone = { commitMaxTokens() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (maxTokensError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = maxTokensError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                ConvoButton(
                    text = if (maxTokensDraft.trim().toIntOrNull() == apiPreferences.maxTokens) {
                        "Saved"
                    } else {
                        "Save max tokens"
                    },
                    onClick = ::commitMaxTokens,
                    enabled = maxTokensDraft.trim().toIntOrNull()?.let { it >= 1 } == true &&
                        maxTokensDraft.trim().toIntOrNull() != apiPreferences.maxTokens,
                    icon = null,
                    containerColor = Color.White,
                    contentColor = Color.Black,
                    textStyle = primaryButtonTextStyle,
                    modifier = primaryButtonModifier,
                )
                }

                SettingsSection(
                    title = "Web search",
                    description = "Control whether the search toggle in the composer stays on after you send a message.",
                    showDividerAbove = true,
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Keep search on after sending",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    ConvoToggle(
                        checked = searchPreferences.persistAfterPrompt,
                        onCheckedChange = { enabled ->
                            onSearchPreferencesChanged(
                                searchPreferences.copy(persistAfterPrompt = enabled),
                            )
                        },
                    )
                }
                }

                SettingsSection(
                    title = "Composer",
                    description = "Choose which shortcuts appear in the chat input bar and conversation.",
                    showDividerAbove = true,
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Show transcription button",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    ConvoToggle(
                        checked = composerPreferences.showDictationButton,
                        onCheckedChange = { enabled ->
                            onComposerPreferencesChanged(
                                composerPreferences.copy(showDictationButton = enabled),
                            )
                        },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Scroll to bottom button",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Jump to the latest messages when you've scrolled up",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    ConvoToggle(
                        checked = composerPreferences.showScrollToBottomButton,
                        onCheckedChange = { enabled ->
                            onComposerPreferencesChanged(
                                composerPreferences.copy(showScrollToBottomButton = enabled),
                            )
                        },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Scroll to top button",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Jump to the start of the chat when you've scrolled down",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    ConvoToggle(
                        checked = composerPreferences.showScrollToTopButton,
                        onCheckedChange = { enabled ->
                            onComposerPreferencesChanged(
                                composerPreferences.copy(showScrollToTopButton = enabled),
                            )
                        },
                    )
                }
                }

                SettingsSection(
                    title = "Voice on voice models",
                    description = "When a voice model is selected, this decides what it speaks — " +
                        "your words, or a generated reply.",
                    showDividerAbove = true,
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VoiceModeChip(
                        label = "Speak my words",
                        description = "Speak your words in the model's voice",
                        selected = voicePreferences.mode == VoiceTtsMode.SpeakMyWords,
                        onClick = {
                            onVoicePreferencesChanged(
                                voicePreferences.copy(mode = VoiceTtsMode.SpeakMyWords),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    VoiceModeChip(
                        label = "Conversation",
                        description = "Generate a reply, then speak it",
                        selected = voicePreferences.mode == VoiceTtsMode.Conversation,
                        onClick = {
                            onVoicePreferencesChanged(
                                voicePreferences.copy(mode = VoiceTtsMode.Conversation),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                ConvoPickerField(
                    label = "Transcription model",
                    value = transcriptionDisplayName,
                    enabled = apiKey.isNotBlank(),
                    onClick = { showTranscriptionPicker = true },
                )

                Spacer(modifier = Modifier.height(16.dp))
                ConvoPickerField(
                    label = "Reply model (Conversation mode)",
                    value = replyDisplayName,
                    enabled = apiKey.isNotBlank() &&
                        voicePreferences.mode == VoiceTtsMode.Conversation,
                    onClick = { showReplyPicker = true },
                )

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show voice replies as text first",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Open the script instead of the audio player; use the type button to switch back",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    ConvoToggle(
                        checked = voicePreferences.showVoiceRepliesAsTextFirst,
                        onCheckedChange = { enabled ->
                            onVoicePreferencesChanged(
                                voicePreferences.copy(showVoiceRepliesAsTextFirst = enabled),
                            )
                        },
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Play voice replies automatically",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Start playback when a voice reply arrives (happens in Conversation mode regardless of this setting)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    ConvoToggle(
                        checked = voicePreferences.autoPlayVoiceReplies,
                        onCheckedChange = { enabled ->
                            onVoicePreferencesChanged(
                                voicePreferences.copy(autoPlayVoiceReplies = enabled),
                            )
                        },
                    )
                }
                }

                QuickSettingsSection(
                    config = quickSettingsConfig,
                    descriptorsById = quickSettingDescriptorsById,
                    onChange = onQuickSettingsChanged,
                    onOpenAddPicker = { showQuickSettingsPicker = true },
                    onDragActiveChanged = { dragging -> settingsScrollEnabled = !dragging },
                )

                Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        if (visible && showTranscriptionPicker) {
            ModelSelectorModal(
                apiKey = apiKey,
                api = api,
                currentModelId = voicePreferences.transcriptionModelId,
                title = "Transcription model",
                fixedFilters = ModelFilterState(
                    outputFilters = setOf(ModelOutputFilter.Transcribe),
                ),
                showFilterBadges = false,
                modelFilter = { it.hasTranscriptionOutput },
                onSelect = { model ->
                    transcriptionModelName = model.name
                    onVoicePreferencesChanged(
                        voicePreferences.copy(transcriptionModelId = model.id),
                    )
                },
                onDismiss = { showTranscriptionPicker = false },
            )
        }

        if (visible && showReplyPicker) {
            ModelSelectorModal(
                apiKey = apiKey,
                api = api,
                currentModelId = voicePreferences.replyModelId,
                title = "Reply model",
                fixedFilters = ModelFilterState(
                    outputFilters = setOf(ModelOutputFilter.Chat),
                ),
                showFilterBadges = false,
                modelFilter = { it.isChatCapable },
                onSelect = { model ->
                    replyModelName = model.name
                    onVoicePreferencesChanged(
                        voicePreferences.copy(replyModelId = model.id),
                    )
                },
                onDismiss = { showReplyPicker = false },
            )
        }

        if (visible && showQuickSettingsPicker) {
            QuickSettingsAddPickerSheet(
                currentConfig = quickSettingsConfig,
                availableSettings = availableSettings,
                onAdd = { id ->
                    onQuickSettingsChanged(
                        QuickSettingsConfig(quickSettingsConfig.items + id),
                    )
                },
                onDismiss = { showQuickSettingsPicker = false },
            )
        }

        AnimatedVisibility(
            visible = visible && showHelp,
            enter = slideInHorizontally(tween(HelpPageAnimMs)) { it },
            exit = slideOutHorizontally(tween(HelpPageAnimMs)) { it },
            modifier = Modifier.fillMaxSize(),
        ) {
            HelpScreen(onBack = { showHelp = false })
        }
    }
}

private fun formatTimeoutLabel(minutes: Int): String =
    if (minutes == 1) "1 minute" else "$minutes minutes"
