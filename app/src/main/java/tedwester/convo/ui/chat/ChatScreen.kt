package tedwester.convo.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tedwester.convo.core.network.OpenRouterApi
import tedwester.convo.core.network.model.OpenRouterModel
import tedwester.convo.core.security.KeyStorage
import tedwester.convo.features.chat.data.QuickSettingsConfig
import tedwester.convo.features.chat.data.SearchPreferences
import tedwester.convo.features.chat.data.ComposerPreferences
import tedwester.convo.features.chat.data.VoicePreferences
import tedwester.convo.features.chat.rememberChatState
import tedwester.convo.ui.chat.conversation.ConversationScreen
import tedwester.convo.ui.chat.conversation.DismissKeyboardOnLifecycle
import kotlin.math.abs

private const val PageFlingVelocity = 700f

private const val ChatParallaxFraction = 0.18f

private val PageSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 1100f,
    visibilityThreshold = 0.001f,
)

@Composable
fun ChatScreen(
    apiKey: String,
    api: OpenRouterApi,
    keyStorage: KeyStorage,
    onOpenSettings: () -> Unit,
    navigationBlocked: Boolean = false,
    voicePreferences: VoicePreferences = VoicePreferences(),
    onVoicePreferencesChanged: (VoicePreferences) -> Unit = {},
    searchPreferences: SearchPreferences = SearchPreferences(),
    onSearchPreferencesChanged: (SearchPreferences) -> Unit = {},
    composerPreferences: ComposerPreferences = ComposerPreferences(),
    onComposerPreferencesChanged: (ComposerPreferences) -> Unit = {},
    onLockApp: () -> Unit = {},
    quickSettingsConfig: QuickSettingsConfig = QuickSettingsConfig(),
    onQuickSettingsChanged: (QuickSettingsConfig) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val initialModel = remember {
        keyStorage.getModel()?.let { OpenRouterModel(id = it.id, name = it.name) }
    }
    val chatState = rememberChatState(
        apiKey = apiKey,
        api = api,
        initialModel = initialModel,
    )

    var showChatList by rememberSaveable { mutableStateOf(false) }
    var pageDragging by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var pageProgress by remember { mutableFloatStateOf(if (showChatList) 1f else 0f) }
    var pageAnimJob by remember { mutableStateOf<Job?>(null) }
    var settleVelocity by remember { mutableFloatStateOf(0f) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    DismissKeyboardOnLifecycle()

    fun dismissKeyboardAndFocus() {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    fun animatePageTo(target: Float, initialVelocity: Float = 0f) {
        val clamped = target.coerceIn(0f, 1f)
        pageAnimJob?.cancel()
        pageAnimJob = scope.launch {
            animate(
                initialValue = pageProgress,
                targetValue = clamped,
                initialVelocity = initialVelocity,
                animationSpec = PageSpring,
            ) { value, _ ->
                pageProgress = value.coerceIn(0f, 1f)
            }
            pageProgress = clamped
        }
    }

    LaunchedEffect(showChatList) {
        if (pageDragging) return@LaunchedEffect
        val target = if (showChatList) 1f else 0f
        val velocity = settleVelocity
        settleVelocity = 0f
        if (abs(pageProgress - target) > 0.001f) {
            animatePageTo(target, velocity)
        } else {
            pageProgress = target
        }
    }

    LaunchedEffect(showChatList) {
        if (showChatList) {
            dismissKeyboardAndFocus()
            chatState.refreshChatList()
        }
    }

    BackHandler(enabled = showChatList && !navigationBlocked) {
        showChatList = false
    }

    val pageBackground = MaterialTheme.colorScheme.background

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(pageBackground)
            .clipToBounds(),
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)

        val conversationActive = !navigationBlocked && !showChatList && !pageDragging
        val chatListActive = !navigationBlocked && (showChatList || pageDragging)

        fun settlePage(openList: Boolean, velocityPxPerSec: Float = 0f) {
            if (openList) dismissKeyboardAndFocus()
            var velocity = velocityPxPerSec / widthPx
            if ((openList && velocity < 0f) || (!openList && velocity > 0f)) {
                velocity = 0f
            }
            val alreadyAtTarget = showChatList == openList
            if (alreadyAtTarget) {
                animatePageTo(if (openList) 1f else 0f, velocity)
            } else {
                settleVelocity = velocity
                showChatList = openList
            }
        }

        val pageDragState = rememberDraggableState { delta ->
            pageProgress = (pageProgress + delta / widthPx).coerceIn(0f, 1f)
        }

        fun onPageDragStopped(velocity: Float) {
            pageDragging = false
            when {
                pageProgress <= 0.001f && velocity <= 0f -> {
                    pageProgress = 0f
                    showChatList = false
                }
                pageProgress >= 0.999f && velocity >= 0f -> {
                    pageProgress = 1f
                    showChatList = true
                }
                else -> {
                    val open = when {
                        velocity > PageFlingVelocity -> true
                        velocity < -PageFlingVelocity -> false
                        else -> pageProgress >= 0.45f
                    }
                    settlePage(open, velocity)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .draggable(
                    state = pageDragState,
                    orientation = Orientation.Horizontal,
                    enabled = !navigationBlocked,
                    onDragStarted = {
                        pageDragging = true
                        pageAnimJob?.cancel()
                        dismissKeyboardAndFocus()
                    },
                    onDragStopped = { onPageDragStopped(it) },
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = pageProgress * widthPx * ChatParallaxFraction
                    },
            ) {
                ConversationScreen(
                    apiKey = apiKey,
                    api = api,
                    keyStorage = keyStorage,
                    chatState = chatState,
                    isSurfaceActive = conversationActive,
                    voicePreferences = voicePreferences,
                    composerPreferences = composerPreferences,
                    onOpenMenu = {
                        dismissKeyboardAndFocus()
                        showChatList = true
                    },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = (pageProgress - 1f) * widthPx
                    },
            ) {
                ChatListScreen(
                    chats = chatState.chats,
                    projects = chatState.projects,
                    activeChatId = chatState.currentChatId,
                    runStatuses = chatState.runStatuses,
                    isSurfaceActive = chatListActive,
                    apiKey = apiKey,
                    api = api,
                    chatState = chatState,
                    onBack = {
                        dismissKeyboardAndFocus()
                        showChatList = false
                    },
                    onOpenChat = { id ->
                        dismissKeyboardAndFocus()
                        chatState.openChat(id)
                        showChatList = false
                    },
                    onNewChat = {
                        dismissKeyboardAndFocus()
                        chatState.newChat()
                        showChatList = false
                    },
                    onDeleteChat = chatState::deleteChat,
                    onPinChat = chatState::pinChat,
                    onArchiveChat = chatState::archiveChat,
                    onRenameChat = chatState::renameChat,
                    onAssignChatToProject = chatState::assignChatToProject,
                    onCreateProject = chatState::createProject,
                    onDeleteProject = chatState::deleteProject,
                    onOpenSettings = onOpenSettings,
                    voicePreferences = voicePreferences,
                    onVoicePreferencesChanged = onVoicePreferencesChanged,
                    searchPreferences = searchPreferences,
                    onSearchPreferencesChanged = onSearchPreferencesChanged,
                    onLockApp = onLockApp,
                    quickSettingsConfig = quickSettingsConfig,
                    onQuickSettingsChanged = onQuickSettingsChanged,
                )
            }
        }
    }
}
