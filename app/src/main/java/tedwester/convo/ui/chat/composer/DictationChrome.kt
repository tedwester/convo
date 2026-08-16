package tedwester.convo.ui.chat.composer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import tedwester.convo.ui.components.SpinningLoader
import tedwester.convo.ui.icons.ConvoIcons
import kotlin.math.pow

private val DictationSideFadeWidth = 72.dp
private val DictationSampleSpacing = 2.8.dp
private val DictationWaveStroke = 1.75.dp
private val DictationWaveVerticalPad = 10.dp
private val DictationWaveHorizontalPad = 6.dp

@Composable
internal fun DictationOverlay(
    isTranscribing: Boolean,
    amplitudes: List<Float>,
    scrollPhase: Float,
    dark: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cancelFill = if (dark) Color(0xFF3A3937) else Color(0xFFC8CCD4)
    val cancelTint = if (dark) Color(0xFFE7EAF0) else Color(0xFF3A4049)
    val consumeClicks = remember { MutableInteractionSource() }
    val waveColor = if (dark) Color(0xFF9AA3B1) else Color(0xFF5A6472)

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = consumeClicks,
                    indication = null,
                    onClick = {},
                ),
        )
        StreamingDictationWaveform(
            amplitudes = amplitudes,
            scrollPhase = scrollPhase,
            waveColor = waveColor,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = DictationWaveHorizontalPad,
                    end = DictationWaveHorizontalPad,
                    top = DictationWaveVerticalPad,
                    bottom = ActionButtonSize + DictationWaveVerticalPad,
                )
                .clipToBounds(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DictationCircleButton(
                fill = cancelFill,
                onClick = onCancel,
                enabled = true,
            ) {
                Icon(
                    painter = ConvoIcons.X(),
                    contentDescription = "Cancel dictation",
                    tint = cancelTint,
                    modifier = Modifier.size(ActionIconSize),
                )
            }

            DictationCircleButton(
                fill = Color.White,
                onClick = onConfirm,
                enabled = !isTranscribing,
            ) {
                AnimatedContent(
                    targetState = isTranscribing,
                    transitionSpec = {
                        val transform = (
                            fadeIn(tween(160)) +
                                scaleIn(
                                    initialScale = 0.62f,
                                    animationSpec = spring(
                                        dampingRatio = 0.68f,
                                        stiffness = 420f,
                                    ),
                                )
                            ) togetherWith (
                            fadeOut(tween(120)) +
                                scaleOut(targetScale = 0.72f, animationSpec = tween(120))
                            )
                        transform.using(SizeTransform(clip = false) { _, _ -> snap() })
                    },
                    label = "dictationConfirmIcon",
                ) { transcribing ->
                    if (transcribing) {
                        SpinningLoader(
                            tint = Color.Black,
                            modifier = Modifier.size(ActionIconSize),
                        )
                    } else {
                        Icon(
                            painter = ConvoIcons.Check(),
                            contentDescription = "Transcribe",
                            tint = Color.Black,
                            modifier = Modifier.size(ActionIconSize),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DictationCircleButton(
    fill: Color,
    onClick: () -> Unit,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "dictationBtnScale",
    )

    Box(
        modifier = Modifier
            .size(ActionButtonSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(fill, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun StreamingDictationWaveform(
    amplitudes: List<Float>,
    scrollPhase: Float,
    waveColor: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val sampleSpacingPx = with(density) { DictationSampleSpacing.toPx() }
    val strokeWidthPx = with(density) { DictationWaveStroke.toPx() }
    val fadeWidthPx = with(density) { DictationSideFadeWidth.toPx() }
    val scrollPx = scrollPhase.coerceIn(0f, 1f) * sampleSpacingPx

    Canvas(
        modifier = modifier.dictationSideFades(fadeWidthPx),
    ) {
        if (amplitudes.isEmpty() || size.width <= 0f || size.height <= 0f) return@Canvas

        val canvasWidth = size.width
        val canvasHeight = size.height
        val y0 = canvasHeight / 2f
        val waveHeightPx = (canvasHeight / 2f - strokeWidthPx).coerceAtLeast(4f)
        val last = amplitudes.lastIndex

        val points = buildList {
            for (index in 0..last) {
                val age = last - index
                val x = canvasWidth - age * sampleSpacingPx - scrollPx
                if (x < -sampleSpacingPx * 2f || x > canvasWidth + sampleSpacingPx * 2f) continue
                val lifted = (amplitudes[index] * 1.45f).coerceIn(0f, 1f).pow(0.62f)
                add(Offset(x, y0 - lifted * waveHeightPx))
            }
        }

        if (points.isEmpty()) return@Canvas

        val path = buildClippedWavePath(
            points = points,
            left = 0f,
            right = canvasWidth,
            baselineY = y0,
        ) ?: return@Canvas

        drawPath(
            path = path,
            color = waveColor,
            style = Stroke(
                width = strokeWidthPx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

private fun buildClippedWavePath(
    points: List<Offset>,
    left: Float,
    right: Float,
    baselineY: Float,
): Path? {
    if (points.isEmpty()) return null

    val sorted = points.sortedBy { it.x }
    val path = Path()

    val startY = interpolateWaveY(sorted, left).coerceIn(0f, baselineY * 2f)
    path.moveTo(left, startY)

    for (point in sorted) {
        if (point.x <= left) continue
        if (point.x >= right) break
        path.lineTo(point.x, point.y)
    }

    val endY = interpolateWaveY(sorted, right).coerceIn(0f, baselineY * 2f)
    path.lineTo(right, endY)

    return path
}

private fun interpolateWaveY(points: List<Offset>, x: Float): Float {
    if (points.isEmpty()) return 0f
    if (x <= points.first().x) return points.first().y
    if (x >= points.last().x) return points.last().y

    for (index in 0 until points.lastIndex) {
        val a = points[index]
        val b = points[index + 1]
        if (x in a.x..b.x) {
            val span = (b.x - a.x).coerceAtLeast(0.0001f)
            val t = (x - a.x) / span
            return a.y + (b.y - a.y) * t
        }
    }
    return points.last().y
}

private fun Modifier.dictationSideFades(
    fadeWidthPx: Float,
): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        if (size.width <= 0f || fadeWidthPx <= 0f) return@drawWithContent
        val fadeFrac = (fadeWidthPx / size.width).coerceIn(0.12f, 0.38f)
        drawRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    fadeFrac * 0.32f to Color.White.copy(alpha = 0.12f),
                    fadeFrac * 0.62f to Color.White.copy(alpha = 0.48f),
                    fadeFrac to Color.White,
                    1f - fadeFrac to Color.White,
                    1f - fadeFrac * 0.62f to Color.White.copy(alpha = 0.48f),
                    1f - fadeFrac * 0.32f to Color.White.copy(alpha = 0.12f),
                    1.00f to Color.Transparent,
                ),
            ),
            blendMode = BlendMode.DstIn,
        )
    }
