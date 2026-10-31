package tedwester.convo.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import tedwester.convo.core.audio.DeviceSpeechRecognition
import tedwester.convo.core.network.OpenRouterApi
import tedwester.convo.features.chat.model.Chat
import tedwester.convo.features.chat.model.Project
import tedwester.convo.features.chat.ChatState
import tedwester.convo.features.chat.data.ChatRunStatus
import tedwester.convo.features.chat.data.QuickSettingsConfig
import tedwester.convo.features.chat.data.SearchPreferences
import tedwester.convo.features.chat.data.VoicePreferences
import tedwester.convo.ui.chat.modals.ChatSearchModal
import tedwester.convo.ui.chat.modals.ModelFilterState
import tedwester.convo.ui.chat.modals.ModelOutputFilter
import tedwester.convo.ui.chat.modals.ModelSelectorModal
import tedwester.convo.ui.chat.modals.QuickSettingsDock
import tedwester.convo.ui.chat.modals.RenameChatModal
import tedwester.convo.ui.chat.modals.SelectorTopOption
import tedwester.convo.ui.chat.modals.VoiceModeModal
import tedwester.convo.ui.chat.selection.ChatSelectionBar
import tedwester.convo.ui.chat.selection.rememberChatSelectionState

private const val ProjectsPageAnimMs = 300

private val SectionAnimSpec = tween<Float>(ChatSectionAnimMs, easing = FastOutSlowInEasing)
private val SectionSizeAnimSpec: FiniteAnimationSpec<IntSize> =
    tween(ChatSectionAnimMs, easing = FastOutSlowInEasing)

private val SectionEnter: EnterTransition =
    fadeIn(SectionAnimSpec) +
        expandVertically(animationSpec = SectionSizeAnimSpec, expandFrom = Alignment.Top)
private val SectionExit: ExitTransition =
    fadeOut(SectionAnimSpec) +
        shrinkVertically(animationSpec = SectionSizeAnimSpec, shrinkTowards = Alignment.Top)

@Composable
fun ChatListScreen(
    chats: List<Chat>,
    projects: List<Project>,
    activeChatId: String?,
    runStatuses: Map<String, ChatRunStatus> = emptyMap(),
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onNewChat: () -> Unit,
    onDeleteChat: (String) -> Unit,
    onDeleteChats: (Set<String>) -> Unit,
    onPinChat: (String, Boolean) -> Unit,
    onPinChats: (Set<String>, Boolean) -> Unit,
    onArchiveChat: (String, Boolean) -> Unit,
    onArchiveChats: (Set<String>, Boolean) -> Unit,
    onRenameChat: (String, String) -> Unit,
    onAssignChatToProject: (String, String?) -> Unit,
    onAssignChatsToProject: (Set<String>, String?) -> Unit,
    onCreateProject: (String, String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onOpenSettings: () -> Unit,
    apiKey: String = "",
    api: OpenRouterApi? = null,
    chatState: ChatState? = null,
    voicePreferences: VoicePreferences = VoicePreferences(),
    onVoicePreferencesChanged: (VoicePreferences) -> Unit = {},
    searchPreferences: SearchPreferences = SearchPreferences(),
    onSearchPreferencesChanged: (SearchPreferences) -> Unit = {},
    onLockApp: () -> Unit = {},
    quickSettingsConfig: QuickSettingsConfig = QuickSettingsConfig(),
    onQuickSettingsChanged: (QuickSettingsConfig) -> Unit = {},
    isSurfaceActive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val bg = MaterialTheme.colorScheme.background
    val scope = rememberCoroutineScope()
    var showSearch by remember { mutableStateOf(false) }
    var showProjects by rememberSaveable { mutableStateOf(false) }
    var assigningChatId by remember { mutableStateOf<String?>(null) }
    var bulkAssigning by remember { mutableStateOf(false) }
    var renamingChatId by remember { mutableStateOf<String?>(null) }
    var recentsExpanded by rememberSaveable { mutableStateOf(true) }
    var archiveExpanded by rememberSaveable { mutableStateOf(false) }
    val selection = rememberChatSelectionState()
    // The options cascade in only the very first time the bar appears. After that — in
    // particular when it comes back from a selection — the bar arrives with them already in it.
    var dockIntroPlayed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(selection.isActive) {
        if (selection.isActive) dockIntroPlayed = true
    }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptics = LocalHapticFeedback.current

    // System back leaves selection before it leaves the sidebar: a selection in progress is
    // worth more than the drawer's open state, and it keeps back from feeling destructive.
    BackHandler(enabled = selection.isActive) { selection.clear() }

    val recentChats = remember(chats) { chats.filter { !it.archived } }
    val archivedChats = remember(chats) { chats.filter { it.archived } }
    val projectNames = remember(projects) { projects.associate { it.id to it.name } }

    /** Tap: open the chat, or check it when a selection is already running. */
    fun onRowClick(chatId: String) {
        if (selection.isActive) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            selection.toggle(chatId)
        } else {
            onOpenChat(chatId)
        }
    }

    /** Long press: start a selection on this chat. Always lands on a checked row. */
    fun onRowLongClick(chatId: String) {
        if (!selection.isActive) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        selection.select(chatId)
    }

    var showTranscriptionPicker by remember { mutableStateOf(false) }
    var showReplyPicker by remember { mutableStateOf(false) }
    var showVoiceModeSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var creditsFlash by remember { mutableStateOf<CreditsFlashContent?>(null) }

    val quickSettings = buildQuickSettings(
        config = quickSettingsConfig,
        voicePreferences = voicePreferences,
        onVoicePreferencesChanged = onVoicePreferencesChanged,
        searchPreferences = searchPreferences,
        onSearchPreferencesChanged = onSearchPreferencesChanged,
        onLockApp = onLockApp,
        onShowCredits = {
            if (api == null || apiKey.isBlank()) {
                creditsFlash = CreditsFlashContent.Message("Save an API key in Settings first.")
            } else {
                scope.launch {
                    creditsFlash = runCatching { api.fetchKeyInfo(apiKey) }.fold(
                        onSuccess = ::creditsFlashContentFrom,
                        onFailure = {
                            CreditsFlashContent.Message(
                                it.message ?: "Could not load credits.",
                            )
                        },
                    )
                }
            }
        },
        onOpenTranscriptionPicker = { showTranscriptionPicker = true },
        onOpenReplyPicker = { showReplyPicker = true },
        onOpenVoiceMode = { showVoiceModeSheet = true },
        onNewChat = onNewChat,
    )

    LaunchedEffect(isSurfaceActive) {
        if (isSurfaceActive) return@LaunchedEffect
        showSearch = false
        showProjects = false
        showVoiceModeSheet = false
        assigningChatId = null
        renamingChatId = null
        selection.clear()
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = bg,
            topBar = {
                ChatListHeader(
                    title = "Chats",
                    onOpenSettings = onOpenSettings,
                    onSearch = { showSearch = true },
                    onBack = onBack,
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = ChatHeaderHorizontalPadding,
                    end = ChatHeaderHorizontalPadding,
                    top = 4.dp,
                    bottom = 96.dp,
                ),
            ) {
                item(key = "projects") {
                    ProjectsNavRow(onClick = { showProjects = true })
                }

                item(key = "divider") {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    )
                }

                item(key = "recents-header") {
                    CollapsibleSectionHeader(
                        title = "Recents",
                        expanded = recentsExpanded,
                        onToggle = { recentsExpanded = !recentsExpanded },
                    )
                }

                item(key = "recents-body") {
                    AnimatedVisibility(
                        visible = recentsExpanded,
                        enter = SectionEnter,
                        exit = SectionExit,
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .animateContentSize(
                                    animationSpec = tween(ChatSectionAnimMs, easing = FastOutSlowInEasing),
                                ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (recentChats.isEmpty()) {
                                EmptySectionHint(text = "No recent chats")
                            } else {
                                recentChats.forEach { chat ->
                                    AnimatedChatListRow(
                                        chat = chat,
                                        selected = chat.id == activeChatId,
                                        runStatus = runStatuses[chat.id],
                                        projectName = chat.projectId?.let(projectNames::get),
                                        selectionMode = selection.isActive,
                                        checked = chat.id in selection.selectedIds,
                                        pendingDelete = chat.id in selection.exitingIds,
                                        onClick = { onRowClick(chat.id) },
                                        onLongClick = { onRowLongClick(chat.id) },
                                        onPin = { onPinChat(chat.id, !chat.pinned) },
                                        onAddToProject = { assigningChatId = chat.id },
                                        onRename = { renamingChatId = chat.id },
                                        onArchive = { onArchiveChat(chat.id, true) },
                                        onDelete = { onDeleteChat(chat.id) },
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "archive-spacer") {
                    Spacer(modifier = Modifier.height(14.dp))
                }

                item(key = "archive-header") {
                    CollapsibleSectionHeader(
                        title = "Archive",
                        expanded = archiveExpanded,
                        onToggle = { archiveExpanded = !archiveExpanded },
                    )
                }

                item(key = "archive-body") {
                    AnimatedVisibility(
                        visible = archiveExpanded,
                        enter = SectionEnter,
                        exit = SectionExit,
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .animateContentSize(
                                    animationSpec = tween(ChatSectionAnimMs, easing = FastOutSlowInEasing),
                                ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (archivedChats.isEmpty()) {
                                EmptySectionHint(text = "No archived chats")
                            } else {
                                archivedChats.forEach { chat ->
                                    AnimatedChatListRow(
                                        chat = chat,
                                        selected = chat.id == activeChatId,
                                        runStatus = runStatuses[chat.id],
                                        projectName = chat.projectId?.let(projectNames::get),
                                        selectionMode = selection.isActive,
                                        checked = chat.id in selection.selectedIds,
                                        pendingDelete = chat.id in selection.exitingIds,
                                        onClick = { onRowClick(chat.id) },
                                        onLongClick = { onRowLongClick(chat.id) },
                                        onPin = { onPinChat(chat.id, !chat.pinned) },
                                        onAddToProject = { assigningChatId = chat.id },
                                        onRename = { renamingChatId = chat.id },
                                        onArchive = { onArchiveChat(chat.id, false) },
                                        onDelete = { onDeleteChat(chat.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // The corner holds one thing at a time: quick settings or the selection bar.
        // Each slides straight down off-screen when it leaves and rises back the same way.
        AnimatedVisibility(
            visible = !selection.isActive,
            enter = slideInVertically(
                animationSpec = tween(ChatSectionAnimMs, easing = FastOutSlowInEasing),
                initialOffsetY = { it },
            ) + fadeIn(tween(ChatSectionAnimMs, easing = FastOutSlowInEasing)),
            exit = slideOutVertically(
                animationSpec = tween(ChatSectionAnimMs, easing = FastOutSlowInEasing),
                targetOffsetY = { it },
            ) + fadeOut(tween(ChatSectionAnimMs / 2, easing = FastOutSlowInEasing)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        ) {
            QuickSettingsDock(
                quickSettings = quickSettings,
                playIntro = !dockIntroPlayed,
            )
        }

        AnimatedVisibility(
            visible = selection.isActive,
            enter = slideInVertically(
                animationSpec = tween(ChatSectionAnimMs, easing = FastOutSlowInEasing),
                initialOffsetY = { it },
            ) + fadeIn(tween(ChatSectionAnimMs, easing = FastOutSlowInEasing)),
            exit = slideOutVertically(
                animationSpec = tween(ChatSectionAnimMs, easing = FastOutSlowInEasing),
                targetOffsetY = { it },
            ) + fadeOut(tween(ChatSectionAnimMs / 2, easing = FastOutSlowInEasing)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        ) {
            ChatSelectionBar(
                selectedCount = selection.selectedCount,
                onCancel = { selection.clear() },
                onDelete = {
                    if (selection.selectedCount <= 0) return@ChatSelectionBar
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    selection.beginDelete(scope, onDeleteChats)
                },
                onArchive = {
                    val ids = selection.selectedIds
                    if (ids.isEmpty()) return@ChatSelectionBar
                    val unarchive = ids.all { id ->
                        chats.firstOrNull { it.id == id }?.archived == true
                    }
                    onArchiveChats(ids, !unarchive)
                    selection.clear()
                },
                onPin = {
                    val ids = selection.selectedIds
                    if (ids.isEmpty()) return@ChatSelectionBar
                    val unpin = ids.all { id ->
                        chats.firstOrNull { it.id == id }?.pinned == true
                    }
                    onPinChats(ids, !unpin)
                    selection.clear()
                },
                onAddToProject = {
                    if (selection.selectedCount > 0) bulkAssigning = true
                },
            )
        }

        CreditsFlashOverlay(
            content = creditsFlash,
            onDismiss = { creditsFlash = null },
        )

        if (showSearch) {
            ChatSearchModal(
                chats = recentChats,
                activeChatId = activeChatId,
                onOpenChat = { id ->
                    showSearch = false
                    onOpenChat(id)
                },
                onDismiss = { showSearch = false },
            )
        }

        if (showTranscriptionPicker && api != null) {
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
                topOptions = listOf(
                    SelectorTopOption(
                        label = "On-device",
                        sublabel = DeviceSpeechRecognition.statusLabel(context),
                        selected = voicePreferences.transcriptionModelId ==
                            VoicePreferences.LOCAL_TRANSCRIPTION_MODEL_ID,
                        onClick = {
                            onVoicePreferencesChanged(
                                voicePreferences.copy(
                                    transcriptionModelId =
                                        VoicePreferences.LOCAL_TRANSCRIPTION_MODEL_ID,
                                ),
                            )
                        },
                    ),
                ),
                onSelect = { model ->
                    onVoicePreferencesChanged(
                        voicePreferences.copy(transcriptionModelId = model.id),
                    )
                },
                onDismiss = { showTranscriptionPicker = false },
            )
        }

        if (showReplyPicker && api != null) {
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
                    onVoicePreferencesChanged(
                        voicePreferences.copy(replyModelId = model.id),
                    )
                },
                onDismiss = { showReplyPicker = false },
            )
        }

        if (showVoiceModeSheet) {
            VoiceModeModal(
                voicePreferences = voicePreferences,
                onVoicePreferencesChanged = onVoicePreferencesChanged,
                apiKey = apiKey,
                onOpenTranscriptionPicker = { showTranscriptionPicker = true },
                onOpenReplyPicker = { showReplyPicker = true },
                onDismiss = { showVoiceModeSheet = false },
            )
        }

        AnimatedVisibility(
            visible = showProjects,
            enter = slideInHorizontally(tween(ProjectsPageAnimMs)) { it },
            exit = slideOutHorizontally(tween(ProjectsPageAnimMs)) { it },
        ) {
            ProjectsScreen(
                projects = projects,
                onBack = { showProjects = false },
                onCreateProject = onCreateProject,
                onDeleteProject = onDeleteProject,
            )
        }

        assigningChatId?.let { chatId ->
            val chat = chats.firstOrNull { it.id == chatId }
            ProjectPickerOverlay(
                projects = projects,
                selectedProjectId = chat?.projectId,
                onSelect = { projectId -> onAssignChatToProject(chatId, projectId) },
                onDismiss = { assigningChatId = null },
            )
        }

        if (bulkAssigning) {
            val ids = selection.selectedIds
            val commonProject = ids.mapNotNull { id ->
                chats.firstOrNull { it.id == id }?.projectId
            }.distinct().singleOrNull()
            ProjectPickerOverlay(
                projects = projects,
                selectedProjectId = commonProject,
                onSelect = { projectId ->
                    onAssignChatsToProject(ids, projectId)
                    selection.clear()
                    bulkAssigning = false
                },
                onDismiss = { bulkAssigning = false },
            )
        }

        renamingChatId?.let { chatId ->
            val chat = chats.firstOrNull { it.id == chatId } ?: return@let
            RenameChatModal(
                initialTitle = chat.title,
                onRename = { title -> onRenameChat(chatId, title) },
                onDismiss = { renamingChatId = null },
            )
        }
    }
}
