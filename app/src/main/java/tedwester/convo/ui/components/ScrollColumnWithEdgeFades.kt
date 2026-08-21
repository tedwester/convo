package tedwester.convo.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DefaultScrollFadeExtent = 36.dp
private val HorizontalScrollFadeWidth = 20.dp

private fun edgeFadeOutStops(
    background: Color,
    intensity: Float,
): Array<Pair<Float, Color>> = arrayOf(
    0.00f to background.copy(alpha = intensity),
    0.38f to background.copy(alpha = 0.94f * intensity),
    0.68f to background.copy(alpha = 0.52f * intensity),
    0.88f to background.copy(alpha = 0.18f * intensity),
    1.00f to Color.Transparent,
)

private fun edgeFadeInStops(
    background: Color,
    intensity: Float,
): Array<Pair<Float, Color>> = arrayOf(
    0.00f to Color.Transparent,
    0.12f to background.copy(alpha = 0.18f * intensity),
    0.32f to background.copy(alpha = 0.52f * intensity),
    0.62f to background.copy(alpha = 0.94f * intensity),
    1.00f to background.copy(alpha = intensity),
)

private fun verticalTopFadeGradient(
    background: Color,
    fadeHeightPx: Float,
    intensity: Float,
): Brush = Brush.verticalGradient(
    colorStops = edgeFadeOutStops(background, intensity),
    endY = fadeHeightPx,
)

private fun verticalBottomFadeGradient(
    background: Color,
    fadeHeightPx: Float,
    containerHeight: Float,
    intensity: Float,
): Brush = Brush.verticalGradient(
    colorStops = edgeFadeInStops(background, intensity),
    startY = containerHeight - fadeHeightPx,
    endY = containerHeight,
)

private fun horizontalEdgeFadeOutStops(
    background: Color,
    intensity: Float,
): Array<Pair<Float, Color>> = arrayOf(
    0.00f to background.copy(alpha = intensity),
    0.32f to background.copy(alpha = intensity),
    0.58f to background.copy(alpha = 0.42f * intensity),
    0.78f to background.copy(alpha = 0.10f * intensity),
    1.00f to Color.Transparent,
)

private fun horizontalEdgeFadeInStops(
    background: Color,
    intensity: Float,
): Array<Pair<Float, Color>> = arrayOf(
    0.00f to Color.Transparent,
    0.22f to background.copy(alpha = 0.10f * intensity),
    0.42f to background.copy(alpha = 0.42f * intensity),
    0.68f to background.copy(alpha = intensity),
    1.00f to background.copy(alpha = intensity),
)

private fun horizontalStartFadeGradient(
    background: Color,
    fadeWidthPx: Float,
    intensity: Float,
): Brush = Brush.horizontalGradient(
    colorStops = horizontalEdgeFadeOutStops(background, intensity),
    endX = fadeWidthPx,
)

private fun horizontalEndFadeGradient(
    background: Color,
    fadeWidthPx: Float,
    containerWidth: Float,
    intensity: Float,
): Brush = Brush.horizontalGradient(
    colorStops = horizontalEdgeFadeInStops(background, intensity),
    startX = containerWidth - fadeWidthPx,
    endX = containerWidth,
)

private fun Modifier.scrollEdgeFades(
    background: Color,
    fadeHeightPx: Float,
    topFadeAlpha: Float,
    bottomFadeAlpha: Float,
): Modifier = drawWithContent {
    drawContent()
    if (topFadeAlpha > 0.001f) {
        drawRect(
            brush = verticalTopFadeGradient(
                background = background,
                fadeHeightPx = fadeHeightPx,
                intensity = topFadeAlpha,
            ),
            size = Size(size.width, fadeHeightPx),
        )
    }
    if (bottomFadeAlpha > 0.001f) {
        drawRect(
            brush = verticalBottomFadeGradient(
                background = background,
                fadeHeightPx = fadeHeightPx,
                containerHeight = size.height,
                intensity = bottomFadeAlpha,
            ),
            topLeft = Offset(0f, size.height - fadeHeightPx),
            size = Size(size.width, fadeHeightPx),
        )
    }
}

private fun Modifier.horizontalScrollEdgeFades(
    background: Color,
    fadeWidthPx: Float,
    startFadeAlpha: Float,
    endFadeAlpha: Float,
): Modifier = drawWithContent {
    drawContent()
    if (startFadeAlpha > 0.001f) {
        drawRect(
            brush = horizontalStartFadeGradient(
                background = background,
                fadeWidthPx = fadeWidthPx,
                intensity = startFadeAlpha,
            ),
            size = Size(fadeWidthPx, size.height),
        )
    }
    if (endFadeAlpha > 0.001f) {
        drawRect(
            brush = horizontalEndFadeGradient(
                background = background,
                fadeWidthPx = fadeWidthPx,
                containerWidth = size.width,
                intensity = endFadeAlpha,
            ),
            topLeft = Offset(size.width - fadeWidthPx, 0f),
            size = Size(fadeWidthPx, size.height),
        )
    }
}

@Composable
fun ScrollColumnWithEdgeFades(
    background: Color,
    modifier: Modifier = Modifier,
    fadeHeight: Dp = DefaultScrollFadeExtent,
    state: ScrollState = rememberScrollState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    nestedScrollConnection: NestedScrollConnection? = null,
    userScrollEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = state
    val density = LocalDensity.current
    val fadeHeightPx = with(density) { fadeHeight.toPx() }
    val topFadeAlpha by remember(scrollState, fadeHeightPx) {
        derivedStateOf {
            (scrollState.value / fadeHeightPx).coerceIn(0f, 1f)
        }
    }
    val bottomFadeAlpha by remember(scrollState, fadeHeightPx) {
        derivedStateOf {
            if (scrollState.maxValue <= 0) {
                0f
            } else {
                ((scrollState.maxValue - scrollState.value) / fadeHeightPx).coerceIn(0f, 1f)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (nestedScrollConnection != null) Modifier.nestedScroll(nestedScrollConnection)
                else Modifier,
            )
            .clipToBounds()
            .scrollEdgeFades(
                background = background,
                fadeHeightPx = fadeHeightPx,
                topFadeAlpha = topFadeAlpha,
                bottomFadeAlpha = bottomFadeAlpha,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState, enabled = userScrollEnabled)
                .padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

@Composable
fun LazyColumnWithEdgeFades(
    background: Color,
    modifier: Modifier = Modifier,
    fadeHeight: Dp = DefaultScrollFadeExtent,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: LazyListScope.() -> Unit,
) {
    val density = LocalDensity.current
    val fadeHeightPx = with(density) { fadeHeight.toPx() }
    val topFadeAlpha by remember(state, fadeHeightPx) {
        derivedStateOf {
            if (state.firstVisibleItemIndex == 0) {
                (state.firstVisibleItemScrollOffset / fadeHeightPx).coerceIn(0f, 1f)
            } else {
                1f
            }
        }
    }
    val bottomFadeAlpha by remember(state, fadeHeightPx) {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            if (layoutInfo.totalItemsCount == 0) return@derivedStateOf 0f
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf 0f
            if (lastVisible.index < layoutInfo.totalItemsCount - 1) {
                1f
            } else {
                val distanceFromBottom =
                    (lastVisible.offset + lastVisible.size) - layoutInfo.viewportEndOffset
                if (distanceFromBottom <= 0) {
                    0f
                } else {
                    (distanceFromBottom / fadeHeightPx).coerceIn(0f, 1f)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .scrollEdgeFades(
                background = background,
                fadeHeightPx = fadeHeightPx,
                topFadeAlpha = topFadeAlpha,
                bottomFadeAlpha = bottomFadeAlpha,
            ),
    ) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

@Composable
fun ScrollRowWithEdgeFades(
    background: Color,
    modifier: Modifier = Modifier,
    fadeWidth: Dp = HorizontalScrollFadeWidth,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable RowScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val fadeWidthPx = with(density) { fadeWidth.toPx() }
    val startFadeAlpha by remember(scrollState) {
        derivedStateOf {
            if (scrollState.value <= 0) 0f else 1f
        }
    }
    val endFadeAlpha by remember(scrollState) {
        derivedStateOf {
            if (scrollState.maxValue <= 0 || scrollState.value >= scrollState.maxValue) {
                0f
            } else {
                1f
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .horizontalScrollEdgeFades(
                background = background,
                fadeWidthPx = fadeWidthPx,
                startFadeAlpha = startFadeAlpha,
                endFadeAlpha = endFadeAlpha,
            ),
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(contentPadding),
            horizontalArrangement = horizontalArrangement,
            content = content,
        )
    }
}
