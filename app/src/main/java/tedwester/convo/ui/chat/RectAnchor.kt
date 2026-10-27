package tedwester.convo.ui.chat

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot

/**
 * A live hook into where something sits on screen, without triggering recomposition.
 *
 * The send animation needs to know where the compose field and the bubble are while they are
 * moving — the message list scrolls, the keyboard opens and closes — but writing those
 * positions into snapshot state would recompose the conversation on every frame of any of
 * those motions. So the layout coordinates are kept by reference, which is a plain assignment
 * that costs nothing on a layout pass, and they are turned into a rectangle only when the
 * animation actually needs one — a few frames of a single bubble's flight, never the steady
 * state and never a keyboard transition.
 */
class RectAnchor {
    var coords: LayoutCoordinates? = null

    /** The current bounds on screen, or [Rect.Zero] when there is nothing to measure. */
    fun current(): Rect {
        val c = coords
        return if (c != null && c.isAttached) c.boundsInRoot() else Rect.Zero
    }
}
