package tedwester.convo.ui.chat.message

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Makes sure only one voice note plays at a time, no matter how many bubbles
 * hold audio. Each row claims the active slot when it starts playing; every
 * other row pauses the moment the slot moves on. Remembered per conversation
 * in the message list, so chats never mute each other.
 */
internal class AudioPlaybackCoordinator {

    var generation by mutableIntStateOf(0)
        private set

    var onPlaybackStarted: (() -> Unit)? = null

    /** Returns the claimed slot. The caller keeps it and yields when it moves on. */
    fun claim(): Int {
        generation += 1
        onPlaybackStarted?.invoke()
        return generation
    }
}

internal val LocalAudioPlaybackCoordinator: ProvidableCompositionLocal<AudioPlaybackCoordinator?> =
    compositionLocalOf { null }

@Composable
internal fun rememberAudioPlaybackCoordinator(): AudioPlaybackCoordinator {
    return remember { AudioPlaybackCoordinator() }
}
