package tedwester.convo.ui.chat.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import java.util.concurrent.atomic.AtomicInteger
import tedwester.convo.ui.chat.hintPopupFadeLayer
import tedwester.convo.ui.chat.rememberHintPopupFadeController

enum class ComposerHint {
    Search,
    Reasoning,
    SystemMessage,
    Dictation,
    Voice,
}

internal val ComposerHint.title: String
    get() = when (this) {
        ComposerHint.Search -> "Web search"
        ComposerHint.Reasoning -> "Reasoning"
        ComposerHint.SystemMessage -> "System message"
        ComposerHint.Dictation -> "Transcribe"
        ComposerHint.Voice -> "Voice"
    }

internal val ComposerHint.body: String
    get() = when (this) {
        ComposerHint.Search ->
            "Turn this on to let the model look things up on the web while it answers."
        ComposerHint.Reasoning ->
            "Tap to turn thinking on or off. Hold to choose how hard it thinks."
        ComposerHint.SystemMessage ->
            "Add instructions the model follows for this chat."
        ComposerHint.Dictation ->
            "Tap to speak into the text box. Audio is sent to your transcription model, which can cost money."
        ComposerHint.Voice ->
            "Tap to to start a conversation with a model, which uses your reply model after transcribing your words. Transcription and reply models can cost money."
    }

private val BubbleShape = RoundedCornerShape(16.dp)
private val CaretWidth = 12.dp
private val CaretHeight = 7.dp
private val ScreenMargin = 12.dp
private val BubbleWidth = 236.dp
private val AnchorGap = 6.dp

@Composable
internal fun ComposerHintAnchor(
    hint: ComposerHint,
    activeHint: ComposerHint?,
    onAdvance: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box {
        content()
        if (activeHint == hint) {
            ComposerHintPopup(
                hint = hint,
                onAdvance = onAdvance,
            )
        }
    }
}

@Composable
private fun ComposerHintPopup(
    hint: ComposerHint,
    onAdvance: () -> Unit,
) {
    val density = LocalDensity.current
    val caretX = remember { AtomicInteger(0) }
    val positionProvider = remember(density) {
        ComposerHintPositionProvider(
            caretX = caretX,
            density = density,
        )
    }
    val fade = rememberHintPopupFadeController(onDismissComplete = onAdvance)

    LaunchedEffect(hint) {
        fade.fadeIn()
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = fade::dismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = false,
        ),
    ) {
        val dark = isSystemInDarkTheme()
        val fill = if (dark) Color(0xFF1E1D1B) else Color.White
        val ring = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.10f)
        val continueLabel = if (hint == ComposerHint.Voice) "Got it" else "Next"

        Column(
            modifier = Modifier
                .width(BubbleWidth)
                .hintPopupFadeLayer(fade.value)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = fade::dismiss,
                ),
            horizontalAlignment = Alignment.Start,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(BubbleShape)
                    .background(fill)
                    .border(1.dp, ring, BubbleShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    text = hint.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = hint.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = continueLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            val caretWidthPx = with(density) { CaretWidth.toPx() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CaretHeight)
                    .offset(y = (-1).dp)
                    .drawBehind {
                        val cx = caretX.get().toFloat().coerceIn(
                            caretWidthPx,
                            size.width - caretWidthPx,
                        )
                        val path = Path().apply {
                            moveTo(cx - caretWidthPx / 2f, 0f)
                            lineTo(cx + caretWidthPx / 2f, 0f)
                            lineTo(cx, size.height)
                            close()
                        }
                        drawPath(path, fill.copy(alpha = fill.alpha * fade.value))
                    },
            )
        }
    }
}

private class ComposerHintPositionProvider(
    private val caretX: AtomicInteger,
    private val density: Density,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val margin = with(density) { ScreenMargin.roundToPx() }
        val gap = with(density) { AnchorGap.roundToPx() }
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val desiredX = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val x = desiredX.coerceIn(margin, maxX)
        caretX.set(anchorBounds.left + anchorBounds.width / 2 - x)
        val y = (anchorBounds.top - popupContentSize.height - gap)
            .coerceAtLeast(margin)
        return IntOffset(x, y)
    }
}
