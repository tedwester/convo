package tedwester.convo.ui.chat

import androidx.compose.ui.geometry.Rect

/**
 * A rectangle that is measured on every layout pass without triggering recomposition.
 *
 * The send animation needs to know where things are on screen while they are moving — the
 * compose field's corner, the bubble's settled spot — but the message list scrolls underneath
 * it. Writing these into snapshot state would recompose the conversation on every scroll
 * frame, so they are read straight from the layer that draws them instead.
 */
class RectAnchor {
    var value: Rect = Rect.Zero
}
