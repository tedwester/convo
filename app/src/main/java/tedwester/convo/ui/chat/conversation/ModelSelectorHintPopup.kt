package tedwester.convo.ui.chat.conversation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch
import tedwester.convo.ui.chat.HintPopupFadeTokens
import tedwester.convo.ui.chat.hintPopupFadeLayer

private val HintBubbleShape = RoundedCornerShape(16.dp)
private val HintCaretWidth = 12.dp
private val HintCaretHeight = 7.dp
private val HintScreenMargin = 12.dp
private val HintBubbleWidth = 236.dp
private val HintAnchorGap = 6.dp

/**
 * First-run callout anchored under the model selector chip in the top bar.
 * The caret points up at the chip; tap / outside / back dismisses it.
 */
@Composable
fun ModelSelectorHintAnchor(
    show: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box {
        content()
        ModelSelectorHintPopup(
            visible = show,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun ModelSelectorHintPopup(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val caretX = remember { AtomicInteger(0) }
    val positionProvider = remember(density) {
        ModelHintPositionProvider(caretX = caretX, density = density)
    }
    val alpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var mounted by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
            alpha.snapTo(0f)
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = HintPopupFadeTokens.FadeInMs,
                    easing = FastOutSlowInEasing,
                ),
            )
        } else if (mounted) {
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = HintPopupFadeTokens.FadeOutMs,
                    easing = FastOutSlowInEasing,
                ),
            )
            mounted = false
        }
    }

    fun dismissWithFade() {
        scope.launch {
            alpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = HintPopupFadeTokens.FadeOutMs,
                    easing = FastOutSlowInEasing,
                ),
            )
            mounted = false
            onDismiss()
        }
    }

    if (!mounted) return

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = ::dismissWithFade,
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

        Column(
            modifier = Modifier
                .width(HintBubbleWidth)
                .hintPopupFadeLayer(alpha.value)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = ::dismissWithFade,
                ),
            horizontalAlignment = Alignment.Start,
        ) {
            val caretWidthPx = with(density) { HintCaretWidth.toPx() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HintCaretHeight)
                    .drawBehind {
                        val cx = caretX.get().toFloat().coerceIn(
                            caretWidthPx,
                            size.width - caretWidthPx,
                        )
                        val path = Path().apply {
                            moveTo(cx - caretWidthPx / 2f, size.height)
                            lineTo(cx + caretWidthPx / 2f, size.height)
                            lineTo(cx, 0f)
                            close()
                        }
                        drawPath(path, fill.copy(alpha = fill.alpha * alpha.value))
                    },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(HintBubbleShape)
                    .background(fill)
                    .border(1.dp, ring, HintBubbleShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Choose a model",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Pick the model you want to chat with. You can change it anytime from up here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Got it",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

private class ModelHintPositionProvider(
    private val caretX: AtomicInteger,
    private val density: Density,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val margin = with(density) { HintScreenMargin.roundToPx() }
        val gap = with(density) { HintAnchorGap.roundToPx() }
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val desiredX = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val x = desiredX.coerceIn(margin, maxX)
        caretX.set(anchorBounds.left + anchorBounds.width / 2 - x)
        val y = (anchorBounds.bottom + gap)
            .coerceAtMost(windowSize.height - popupContentSize.height - margin)
        return IntOffset(x, y)
    }
}
