package tedwester.convo.features.chat.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.painter.Painter
import tedwester.convo.ui.icons.ConvoIcons

object QuickSettingIds {
    const val NEW_CHAT = "new_chat"
    const val BIOMETRIC_LOCK = "biometric_lock"
    const val KEEP_SEARCH_ON = "keep_search_on"
    const val VOICE_MODE = "voice_mode"
    const val TRANSCRIPTION_MODEL = "transcription_model"
    const val REPLY_MODEL = "reply_model"
    const val CREDITS = "credits"
}

enum class QuickSettingKind {

    Toggle,

    Cycle,

    Picker,

    Action,
}

sealed interface QuickSettingState {
    @Immutable
    data class Toggle(val on: Boolean) : QuickSettingState

    @Immutable
    data class Cycle(val valueLabel: String, val enabled: Boolean = true) : QuickSettingState

    @Immutable
    data class Picker(val valueLabel: String, val enabled: Boolean = true) : QuickSettingState

    @Immutable
    data class Action(val enabled: Boolean = true) : QuickSettingState
}

@Immutable
class QuickSettingDescriptor(
    val id: String,
    val label: String,
    val description: String,
    val kind: QuickSettingKind,
    val icon: @Composable () -> Painter,
)

@Immutable
class QuickSetting(
    val id: String,
    val label: String,
    val kind: QuickSettingKind,
    val icon: @Composable () -> Painter,
    val state: QuickSettingState,
    val onTap: () -> Unit,
)

@Composable
fun availableQuickSettings(): List<QuickSettingDescriptor> = listOf(
    QuickSettingDescriptor(
        id = QuickSettingIds.NEW_CHAT,
        label = "New chat",
        description = "Start a fresh conversation from the dock.",
        kind = QuickSettingKind.Action,
        icon = { ConvoIcons.Add() },
    ),
    QuickSettingDescriptor(
        id = QuickSettingIds.BIOMETRIC_LOCK,
        label = "Lock app",
        description = "Lock Convo immediately. Unlock with fingerprint, face, or device PIN.",
        kind = QuickSettingKind.Action,
        icon = { ConvoIcons.Lock() },
    ),
    QuickSettingDescriptor(
        id = QuickSettingIds.CREDITS,
        label = "Credits",
        description = "Show remaining OpenRouter credits on screen.",
        kind = QuickSettingKind.Action,
        icon = { ConvoIcons.CreditCard() },
    ),
    QuickSettingDescriptor(
        id = QuickSettingIds.KEEP_SEARCH_ON,
        label = "Keep search on after sending",
        description = "Leave web search enabled after you send a message (current chat only).",
        kind = QuickSettingKind.Toggle,
        icon = { ConvoIcons.Search() },
    ),
    QuickSettingDescriptor(
        id = QuickSettingIds.VOICE_MODE,
        label = "Voice mode and models",
        description = "Manage voice mode and both model settings.",
        kind = QuickSettingKind.Picker,
        icon = { ConvoIcons.AudioLines() },
    ),
    QuickSettingDescriptor(
        id = QuickSettingIds.TRANSCRIPTION_MODEL,
        label = "Transcription model",
        description = "Choose which model transcribes your voice input.",
        kind = QuickSettingKind.Picker,
        icon = { ConvoIcons.MonitorCog() },
    ),
    QuickSettingDescriptor(
        id = QuickSettingIds.REPLY_MODEL,
        label = "Reply model",
        description = "Choose which model replies in conversation voice mode.",
        kind = QuickSettingKind.Picker,
        icon = { ConvoIcons.MonitorCog() },
    ),
)

@Composable
fun quickSettingDescriptor(id: String): QuickSettingDescriptor? =
    availableQuickSettings().firstOrNull { it.id == id }
