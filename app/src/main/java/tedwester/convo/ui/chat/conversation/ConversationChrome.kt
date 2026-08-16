package tedwester.convo.ui.chat.conversation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val ConversationChromeMessageGap = 8.dp

private val TopFadeTail = 12.dp

private val BottomFadeRise = 36.dp

private const val ChromeFadeMs = 280

private fun topChromeGradient(background: Color, chromeFraction: Float): Brush {
    val chromeEnd = chromeFraction.coerceIn(0.55f, 0.90f)
    return Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to background,
            chromeEnd * 0.50f to background.copy(alpha = 0.98f),
            chromeEnd * 0.78f to background.copy(alpha = 0.84f),
            chromeEnd to background.copy(alpha = 0.58f),
            chromeEnd + (1f - chromeEnd) * 0.40f to background.copy(alpha = 0.30f),
            chromeEnd + (1f - chromeEnd) * 0.72f to background.copy(alpha = 0.12f),
            1.00f to Color.Transparent,
        ),
    )
}

private fun bottomChromeGradient(background: Color, riseEnd: Float): Brush {
    val boundary = riseEnd.coerceIn(0.18f, 0.42f)
    val intoComposer = boundary + (1f - boundary) * 0.55f
    return Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to Color.Transparent,
            boundary * 0.30f to background.copy(alpha = 0.05f),
            boundary * 0.55f to background.copy(alpha = 0.14f),
            boundary * 0.78f to background.copy(alpha = 0.28f),
            boundary to background.copy(alpha = 0.46f),
            boundary + (1f - boundary) * 0.22f to background.copy(alpha = 0.64f),
            intoComposer to background.copy(alpha = 0.82f),
            boundary + (1f - boundary) * 0.78f to background.copy(alpha = 0.93f),
            1.00f to background,
        ),
    )
}

@Composable
fun ConversationTopChrome(
    background: Color,
    enabled: Boolean,
    onHeightChanged: (Dp) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var contentHeight by remember { mutableStateOf(0.dp) }
    val fadeProgress by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(ChromeFadeMs, easing = FastOutSlowInEasing),
        label = "topChromeFade",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged {
                val h = with(density) { it.height.toDp() }
                contentHeight = h
                onHeightChanged(h)
            },
    ) {
        if (fadeProgress < 0.999f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 1f - fadeProgress }
                    .background(background),
            )
        }
        if (fadeProgress > 0.001f && contentHeight > 0.dp) {
            val fadeHeight = contentHeight + TopFadeTail
            val chromeFraction = (contentHeight / fadeHeight).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .wrapContentHeight(align = Alignment.Top, unbounded = true)
                    .fillMaxWidth()
                    .height(fadeHeight)
                    .graphicsLayer { alpha = fadeProgress }
                    .background(topChromeGradient(background, chromeFraction)),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding(),
        ) {
            content()
        }
    }
}

@Composable
fun ConversationBottomChrome(
    background: Color,
    enabled: Boolean,
    applyImePadding: Boolean,
    onHeightChanged: (Dp) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var contentHeight by remember { mutableStateOf(0.dp) }
    val fadeProgress by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(ChromeFadeMs, easing = FastOutSlowInEasing),
        label = "bottomChromeFade",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (applyImePadding) Modifier.imePadding() else Modifier),
    ) {
        if (fadeProgress > 0.001f && contentHeight > 0.dp) {
            val fadeHeight = contentHeight + BottomFadeRise
            val riseEnd = (BottomFadeRise / fadeHeight).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .wrapContentHeight(align = Alignment.Bottom, unbounded = true)
                    .fillMaxWidth()
                    .height(fadeHeight)
                    .graphicsLayer { alpha = fadeProgress }
                    .background(bottomChromeGradient(background, riseEnd)),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged {
                    val h = with(density) { it.height.toDp() }
                    contentHeight = h
                    onHeightChanged(h)
                },
        ) {
            content()
        }
    }
}
