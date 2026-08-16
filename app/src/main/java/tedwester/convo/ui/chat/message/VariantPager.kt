package tedwester.convo.ui.chat.message

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.max

internal const val PagerAnimMs = 220
internal const val HighlightAnimMs = 200
/** Last measured heights per assistant turn variant — avoids layout collapse on swap. */
private object VariantHeightCache {
    private val heights = mutableMapOf<Pair<Long, Int>, Int>()

    fun get(messageId: Long, index: Int): Int = heights[messageId to index] ?: 0

    fun put(messageId: Long, index: Int, height: Int) {
        if (height > 0) heights[messageId to index] = height
    }
}

/**
 * Horizontal slide/fade pager shared by text, media, and voice variant pages.
 *
 * Only the incoming and outgoing pages are composed mid-swipe.
 */
@Composable
internal fun VariantPagerLayout(
    messageId: Long,
    targetIndex: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    if (pageCount <= 1) {
        content(targetIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)), modifier)
        return
    }

    val density = LocalDensity.current
    val slidePx = with(density) { 56.dp.toPx() }
    var shownIndex by remember(messageId) { mutableIntStateOf(targetIndex.coerceIn(0, pageCount - 1)) }
    var outgoingIndex by remember(messageId) { mutableStateOf<Int?>(null) }
    val incomingOffset = remember { Animatable(0f) }
    val incomingAlpha = remember { Animatable(1f) }
    val outgoingOffset = remember { Animatable(0f) }
    val outgoingAlpha = remember { Animatable(1f) }

    LaunchedEffect(targetIndex, pageCount) {
        val next = targetIndex.coerceIn(0, pageCount - 1)
        if (next == shownIndex) return@LaunchedEffect
        val forward = next > shownIndex
        val dir = if (forward) 1f else -1f

        outgoingIndex = shownIndex
        shownIndex = next
        outgoingOffset.snapTo(0f)
        outgoingAlpha.snapTo(1f)
        incomingOffset.snapTo(dir * slidePx)
        incomingAlpha.snapTo(0f)

        val spec = tween<Float>(PagerAnimMs, easing = FastOutSlowInEasing)
        launch { outgoingOffset.animateTo(-dir * slidePx, spec) }
        launch {
            outgoingAlpha.animateTo(0f, tween(PagerAnimMs / 2, easing = FastOutSlowInEasing))
        }
        launch { incomingOffset.animateTo(0f, spec) }
        incomingAlpha.animateTo(1f, spec)

        outgoingIndex = null
        outgoingOffset.snapTo(0f)
        outgoingAlpha.snapTo(1f)
    }

    LaunchedEffect(pageCount) {
        if (shownIndex > pageCount - 1) {
            shownIndex = (pageCount - 1).coerceAtLeast(0)
        }
    }

    val activeIndices = remember(shownIndex, outgoingIndex) {
        buildList {
            add(shownIndex)
            outgoingIndex?.let { if (it != shownIndex) add(it) }
        }
    }

    Layout(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds(),
        content = {
            activeIndices.forEach { index ->
                key(messageId, index) {
                    content(
                        index,
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val isShown = index == shownIndex
                                val isOut = index == outgoingIndex
                                alpha = when {
                                    isShown -> incomingAlpha.value
                                    isOut -> outgoingAlpha.value
                                    else -> 0f
                                }
                                translationX = when {
                                    isShown -> incomingOffset.value
                                    isOut -> outgoingOffset.value
                                    else -> 0f
                                }
                            },
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val shownSlot = activeIndices.indexOf(shownIndex)
        val outSlot = outgoingIndex?.let { activeIndices.indexOf(it) }?.takeIf { it >= 0 }
        val shownH = placeables.getOrNull(shownSlot)?.height ?: 0
        val outH = outSlot?.let { placeables.getOrNull(it)?.height } ?: 0

        if (shownH > 0) VariantHeightCache.put(messageId, shownIndex, shownH)
        outgoingIndex?.let { idx ->
            if (outH > 0) VariantHeightCache.put(messageId, idx, outH)
        }

        val cachedShown = VariantHeightCache.get(messageId, shownIndex)
        val cachedOut = outgoingIndex?.let { VariantHeightCache.get(messageId, it) } ?: 0
        val height = max(max(shownH, outH), max(cachedShown, cachedOut))
        val width = constraints.maxWidth
        layout(width, height) {
            placeables.forEach { placeable ->
                placeable.placeRelative(0, 0)
            }
        }
    }
}

@Composable
internal fun VariantPagerButton(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val highlightAlpha by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.14f else 0f,
        animationSpec = tween(HighlightAnimMs, easing = FastOutSlowInEasing),
        label = "pager-highlight",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
        label = "pager-scale",
    )
    val iconAlpha by animateFloatAsState(
        targetValue = when {
            !enabled -> 0.3f
            pressed -> 1f
            else -> 0.75f
        },
        animationSpec = tween(HighlightAnimMs, easing = FastOutSlowInEasing),
        label = "pager-icon-alpha",
    )

    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = iconAlpha)

    Box(
        modifier = Modifier
            .size(36.dp)
            .scale(scale)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = highlightAlpha)),
        )
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}
