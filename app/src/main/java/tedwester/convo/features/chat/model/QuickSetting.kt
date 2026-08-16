package tedwester.convo.features.chat.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.painter.Painter
import tedwester.convo.ui.icons.ConvoIcons

/**
 * Stable string IDs for every setting that can be pinned to the chat-list
 * quick-settings dock. New settings should add an [id] here and register a
 * descriptor in [availableQuickSettings].
 */
object QuickSettingIds {
    const val NEW_CHAT = "new_chat"
    const val BIOMETRIC_LOCK = "biometric_lock"
    const val KEEP_SEARCH_ON = "keep_search_on"
    const val VOICE_MODE = "voice_mode"
    const val TRANSCRIPTION_MODEL = "transcription_model"
    const val REPLY_MODEL = "reply_model"
    const val CREDITS = "credits"
}

/**
 * How a quick-setting button behaves when tapped.
 */
enum class QuickSettingKind {
    /** Flips a boolean (e.g. web search on/off). */
    Toggle,

    /** Cycles through a small set of values (e.g. voice mode). */
    Cycle,

    /** Opens a picker modal (e.g. model selection). */
    Picker,

    /** Fires a one-shot action with no persisted value to display. */
    Action,
}

/**
 * Live, renderable state for a dock button.
 */
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

/**
 * Static description of an eligible quick setting — id, icon, label, kind.
 * Used by the settings page to list what the user can add to the dock.
 */
@Immutable
class QuickSettingDescriptor(
    val id: String,
    val label: String,
    val description: String,
    val kind: QuickSettingKind,
    val icon: @Composable () -> Painter,
)

/**
 * A resolved quick setting bound to live state + a tap callback, ready to render
 * in the dock. Built by the host that owns the underlying state (ChatState /
 * preference stores).
 */
@Immutable
class QuickSetting(
    val id: String,
    val label: String,
    val kind: QuickSettingKind,
    val icon: @Composable () -> Painter,
    val state: QuickSettingState,
    val onTap: () -> Unit,
)

/**
 * The full catalogue of settings the user can put in the dock. Add new entries
 * here as the settings page grows — the dock and the settings picker both
 * read from this list.
 */
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

/** Looks up a descriptor by id, or null if it was removed from the catalogue. */
@Composable
fun quickSettingDescriptor(id: String): QuickSettingDescriptor? =
    availableQuickSettings().firstOrNull { it.id == id }
