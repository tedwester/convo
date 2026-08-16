package tedwester.convo.ui.settings

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import tedwester.convo.features.chat.data.ApiPreferences

private val TrackHeightRest = 5.dp
private val TrackHeightActive = 6.dp
private val ThumbSize = 24.dp
private val ThumbPressedScale = 1.12f
private val HitHeight = 44.dp

private val ThumbSpring = spring<Float>(
    dampingRatio = 0.72f,
    stiffness = 420f,
)
private val FractionSpring = spring<Float>(
    dampingRatio = 0.82f,
    stiffness = Spring.StiffnessMediumLow,
)

private enum class GestureKind { Drag, Tap, Cancel }

/**
 * Discrete request-timeout control for Settings (1–45 minutes).
 *
 * Follows the finger while dragging, springs to the nearest minute on release,
 * and ticks haptically on each step. Horizontal-slop aware so it coexists with
 * the settings page's vertical scroll.
 */
@Composable
fun RequestTimeoutSlider(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    onDisplayMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onInteractionActiveChange: ((Boolean) -> Unit)? = null,
    minMinutes: Int = ApiPreferences.MIN_REQUEST_TIMEOUT_MINUTES,
    maxMinutes: Int = ApiPreferences.MAX_REQUEST_TIMEOUT_MINUTES,
) {
    val safeMin = minMinutes
    val safeMax = maxMinutes.coerceAtLeast(safeMin)
    val clampedMinutes = minutes.coerceIn(safeMin, safeMax)

    val dark = isSystemInDarkTheme()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val trackColor = if (dark) Color(0xFF2A2A2A) else Color(0xFFD8DCE3)
    val fillColor = if (dark) Color.White else Color(0xFF1A1D23)
    val thumbColor = if (dark) Color(0xFF171615) else Color.White
    val thumbRing = if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)

    val onMinutesChangeState = rememberUpdatedState(onMinutesChange)
    val onDisplayMinutesChangeState = rememberUpdatedState(onDisplayMinutesChange)
    val onInteractionActiveChangeState = rememberUpdatedState(onInteractionActiveChange)

    val fraction = remember {
        Animatable(minutesToFraction(clampedMinutes, safeMin, safeMax))
    }
    val lastEmitted = remember { mutableIntStateOf(clampedMinutes) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(minutesToFraction(clampedMinutes, safeMin, safeMax)) }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val visualFraction = (if (dragging) dragFraction else fraction.value).coerceIn(0f, 1f)

    val thumbScale by animateFloatAsState(
        targetValue = if (dragging) ThumbPressedScale else 1f,
        animationSpec = ThumbSpring,
        label = "timeoutThumbScale",
    )
    val trackHeight by animateDpAsState(
        targetValue = if (dragging) TrackHeightActive else TrackHeightRest,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
        label = "timeoutTrackHeight",
    )
    val thumbElevation by animateDpAsState(
        targetValue = if (dragging) 8.dp else 3.dp,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 520f),
        label = "timeoutThumbElevation",
    )

    val insetPx = with(density) { (ThumbSize * ThumbPressedScale / 2f).toPx() }

    fun emitDisplay(value: Int) {
        val next = value.coerceIn(safeMin, safeMax)
        if (next != lastEmitted.intValue) {
            tickHaptic(view)
        }
        lastEmitted.intValue = next
        onDisplayMinutesChangeState.value(next)
    }

    fun commit(value: Int) {
        val next = value.coerceIn(safeMin, safeMax)
        lastEmitted.intValue = next
        onDisplayMinutesChangeState.value(next)
        onMinutesChangeState.value(next)
    }

    fun fractionAt(x: Float, width: Float): Float {
        val travel = (width - insetPx * 2f).coerceAtLeast(1f)
        val raw = ((x - insetPx) / travel).coerceIn(0f, 1f)
        return if (rtl) 1f - raw else raw
    }

    LaunchedEffect(clampedMinutes, safeMin, safeMax) {
        if (dragging) return@LaunchedEffect
        val target = minutesToFraction(clampedMinutes, safeMin, safeMax)
        if (clampedMinutes != lastEmitted.intValue) {
            lastEmitted.intValue = clampedMinutes
            onDisplayMinutesChangeState.value(clampedMinutes)
        }
        if (abs(fraction.value - target) > 0.0005f) {
            fraction.animateTo(target, FractionSpring)
        }
    }

    val stateLabel = if (clampedMinutes == 1) "1 minute" else "$clampedMinutes minutes"

    fun animateFractionTo(minutesValue: Int) {
        scope.launch {
            fraction.animateTo(
                minutesToFraction(minutesValue, safeMin, safeMax),
                FractionSpring,
            )
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HitHeight)
                .progressSemantics(
                    value = clampedMinutes.toFloat(),
                    valueRange = safeMin.toFloat()..safeMax.toFloat(),
                    steps = (safeMax - safeMin - 1).coerceAtLeast(0),
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = "Request timeout"
                    stateDescription = stateLabel
                    setProgress { target ->
                        val next = target.roundToInt().coerceIn(safeMin, safeMax)
                        animateFractionTo(next)
                        commit(next)
                        true
                    }
                }
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .pointerInput(safeMin, safeMax, rtl, insetPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        when (awaitGestureKind(down)) {
                            GestureKind.Cancel -> return@awaitEachGesture
                            GestureKind.Tap -> {
                                val mins = fractionToMinutes(
                                    fractionAt(down.position.x, width),
                                    safeMin,
                                    safeMax,
                                )
                                animateFractionTo(mins)
                                commit(mins)
                            }
                            GestureKind.Drag -> {
                                dragging = true
                                onInteractionActiveChangeState.value?.invoke(true)
                                var settled = false
                                fun settle(from: Float) {
                                    val endMinutes = fractionToMinutes(from, safeMin, safeMax)
                                    commit(endMinutes)
                                    scope.launch {
                                        fraction.snapTo(from)
                                        dragging = false
                                        fraction.animateTo(
                                            minutesToFraction(endMinutes, safeMin, safeMax),
                                            FractionSpring,
                                        )
                                    }
                                }
                                try {
                                    dragFraction = fractionAt(down.position.x, width)
                                    emitDisplay(
                                        fractionToMinutes(dragFraction, safeMin, safeMax),
                                    )
                                    down.consume()
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.find { it.id == down.id }
                                            ?: break
                                        if (!change.pressed) break
                                        if (change.positionChange() != Offset.Zero) {
                                            change.consume()
                                        }
                                        dragFraction = fractionAt(change.position.x, width)
                                        emitDisplay(
                                            fractionToMinutes(dragFraction, safeMin, safeMax),
                                        )
                                    }
                                    settled = true
                                    settle(dragFraction)
                                } finally {
                                    onInteractionActiveChangeState.value?.invoke(false)
                                    if (!settled) settle(dragFraction)
                                }
                            }
                        }
                    }
                },
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val trackH = trackHeight.toPx()
                val travel = (size.width - insetPx * 2f).coerceAtLeast(1f)
                val trackTop = (size.height - trackH) / 2f
                val trackLeft = insetPx
                val visualX = insetPx +
                    (if (rtl) 1f - visualFraction else visualFraction) * travel
                val radius = CornerRadius(trackH / 2f, trackH / 2f)

                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(trackLeft, trackTop),
                    size = Size(travel, trackH),
                    cornerRadius = radius,
                )

                val fillLeft = if (rtl) visualX else trackLeft
                val fillWidth = (if (rtl) {
                    trackLeft + travel - visualX
                } else {
                    visualX - trackLeft
                }).coerceAtLeast(0f)
                if (fillWidth > 0.5f) {
                    drawRoundRect(
                        color = fillColor,
                        topLeft = Offset(fillLeft, trackTop),
                        size = Size(fillWidth, trackH),
                        cornerRadius = radius,
                    )
                }
            }

            if (trackWidthPx > 0f) {
                val travel = (trackWidthPx - insetPx * 2f).coerceAtLeast(1f)
                val thumbCenterX = insetPx +
                    (if (rtl) 1f - visualFraction else visualFraction) * travel
                val thumbPx = with(density) { ThumbSize.toPx() }
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (thumbCenterX - thumbPx / 2f).roundToInt(),
                                y = ((HitHeight.toPx() - thumbPx) / 2f).roundToInt(),
                            )
                        }
                        .graphicsLayer {
                            scaleX = thumbScale
                            scaleY = thumbScale
                        }
                        .size(ThumbSize)
                        .shadow(thumbElevation, CircleShape, clip = false)
                        .clip(CircleShape)
                        .background(thumbColor)
                        .border(0.5.dp, thumbRing, CircleShape),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = with(density) { insetPx.toDp() }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$safeMin min",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "$safeMax min",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
    }
}

private fun minutesToFraction(minutes: Int, min: Int, max: Int): Float {
    val range = (max - min).coerceAtLeast(1)
    return ((minutes - min).toFloat() / range).coerceIn(0f, 1f)
}

private fun fractionToMinutes(fraction: Float, min: Int, max: Int): Int {
    val range = (max - min).coerceAtLeast(1)
    return (min + fraction.coerceIn(0f, 1f) * range).roundToInt().coerceIn(min, max)
}

private fun tickHaptic(view: View) {
    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
}

/**
 * Classifies the gesture after the initial down:
 * - mostly horizontal past slop → drag
 * - lift before slop → tap
 * - mostly vertical past slop → cancel (parent scroll wins)
 */
private suspend fun AwaitPointerEventScope.awaitGestureKind(
    down: PointerInputChange,
): GestureKind {
    val slop = viewConfiguration.touchSlop
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.find { it.id == down.id } ?: return GestureKind.Cancel
        if (!change.pressed) return GestureKind.Tap
        val dx = change.position.x - down.position.x
        val dy = change.position.y - down.position.y
        if (hypot(dx, dy) >= slop) {
            return if (abs(dx) > abs(dy)) GestureKind.Drag else GestureKind.Cancel
        }
    }
}
