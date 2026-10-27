package tedwester.convo.ui.chat.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tedwester.convo.ui.chat.ChatSectionAnimMs

/**
 * Multi-select state for the chat sidebar.
 *
 * A long press on a chat row enters selection mode with that row checked. Every row then
 * shifts right to make room for a checkbox on the left, so titles stay readable while the
 * selection bar counts them. Leaving selection is always explicit — Cancel, system back, or
 * deleting — so a stray tap never drops a half-built selection.
 *
 * The class owns no composable state hooks beyond snapshot state, so it stays testable and
 * the screen only has to render from it.
 */
class ChatSelectionState {

    /** Whether the sidebar is currently in selection mode. */
    var isActive by mutableStateOf(false)
        private set

    /** Ids of the chats currently checked. */
    var selectedIds by mutableStateOf(setOf<String>())
        private set

    /**
     * Rows already on their way out after Delete: they fade over [ChatSectionAnimMs] while the
     * data still holds them, then the screen removes the chats.
     */
    var exitingIds by mutableStateOf(setOf<String>())
        private set

    val selectedCount: Int
        get() = selectedIds.size

    /**
     * Enters selection mode on [chatId], or keeps it selected when already selecting.
     *
     * Long press always lands on a checked row rather than toggling one off, so the gesture
     * never undoes itself.
     */
    fun select(chatId: String) {
        isActive = true
        if (chatId !in selectedIds) {
            selectedIds = selectedIds + chatId
        }
    }

    /** Toggles a row while selecting. Deselecting the last row leaves selection mode. */
    fun toggle(chatId: String) {
        if (!isActive) {
            select(chatId)
            return
        }
        selectedIds = if (chatId in selectedIds) {
            selectedIds - chatId
        } else {
            selectedIds + chatId
        }
        if (selectedIds.isEmpty()) isActive = false
    }

    fun clear() {
        selectedIds = emptySet()
        exitingIds = emptySet()
        isActive = false
    }

    /**
     * Starts a bulk delete: the selection drops out of mode, its rows fade, and once that
     * animation has landed [onDelete] runs for each id.
     */
    fun beginDelete(scope: CoroutineScope, onDelete: (String) -> Unit) {
        val ids = selectedIds
        if (ids.isEmpty()) return
        exitingIds = ids
        selectedIds = emptySet()
        isActive = false
        scope.launch {
            delay(ChatSectionAnimMs.toLong())
            ids.forEach(onDelete)
            exitingIds = emptySet()
        }
    }
}

/** Convenience factory so call sites read as a single unit. */
@Composable
fun rememberChatSelectionState(): ChatSelectionState {
    return remember { ChatSelectionState() }
}
