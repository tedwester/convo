package tedwester.convo.ui.chat.conversation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun ConversationPendingModalEffects(
    isRecording: Boolean,
    cancelRecording: () -> Unit,
    pendingModelSelector: Boolean,
    onShowModelSelector: () -> Unit,
    onClearPendingModelSelector: () -> Unit,
    pendingAttachmentOptions: Boolean,
    onShowAttachmentOptions: () -> Unit,
    onClearPendingAttachmentOptions: () -> Unit,
    pendingSystemMessage: Boolean,
    onShowSystemMessage: () -> Unit,
    onClearPendingSystemMessage: () -> Unit,
    pendingReasoningSettings: Boolean,
    onShowReasoningSettings: () -> Unit,
    onClearPendingReasoningSettings: () -> Unit,
    pendingVoiceSelector: Boolean,
    onShowVoiceSelector: () -> Unit,
    onClearPendingVoiceSelector: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime

    LaunchedEffect(pendingModelSelector) {
        if (!pendingModelSelector) return@LaunchedEffect
        if (isRecording) cancelRecording()
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        withTimeoutOrNull(400) {
            snapshotFlow { imeInsets.getBottom(density) }
                .first { it == 0 }
        }
        onShowModelSelector()
        onClearPendingModelSelector()
    }

    LaunchedEffect(pendingAttachmentOptions) {
        if (!pendingAttachmentOptions) return@LaunchedEffect
        if (isRecording) return@LaunchedEffect
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        withTimeoutOrNull(400) {
            snapshotFlow { imeInsets.getBottom(density) }
                .first { it == 0 }
        }
        onShowAttachmentOptions()
        onClearPendingAttachmentOptions()
    }

    LaunchedEffect(pendingSystemMessage) {
        if (!pendingSystemMessage) return@LaunchedEffect
        if (isRecording) return@LaunchedEffect
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        withTimeoutOrNull(400) {
            snapshotFlow { imeInsets.getBottom(density) }
                .first { it == 0 }
        }
        onShowSystemMessage()
        onClearPendingSystemMessage()
    }

    LaunchedEffect(pendingReasoningSettings) {
        if (!pendingReasoningSettings) return@LaunchedEffect
        if (isRecording) return@LaunchedEffect
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        withTimeoutOrNull(400) {
            snapshotFlow { imeInsets.getBottom(density) }
                .first { it == 0 }
        }
        onShowReasoningSettings()
        onClearPendingReasoningSettings()
    }

    LaunchedEffect(pendingVoiceSelector) {
        if (!pendingVoiceSelector) return@LaunchedEffect
        if (isRecording) return@LaunchedEffect
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        withTimeoutOrNull(400) {
            snapshotFlow { imeInsets.getBottom(density) }
                .first { it == 0 }
        }
        onShowVoiceSelector()
        onClearPendingVoiceSelector()
    }
}

@Composable
internal fun ConversationSurfaceInactiveEffect(
    isSurfaceActive: Boolean,
    isRecording: Boolean,
    cancelRecording: () -> Unit,
    onDismissAllOverlays: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isSurfaceActive) {
        if (isSurfaceActive) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            return@LaunchedEffect
        }
        if (isRecording) cancelRecording()
        onDismissAllOverlays()
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }
}
