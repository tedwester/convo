package tedwester.convo.ui.chat.composer

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.pow

internal val VoiceOrbGlowSize = 140.dp

internal val VoiceOrbBottomGap = 10.dp

internal val VoiceOrbSlotHeight = VoiceOrbGlowSize + VoiceOrbBottomGap

internal val VoiceOrbSlotAnimation = spring<Dp>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium,
)

internal enum class VoiceOrbMode {
    Listening,
    Transcribing,
    Waiting,
}

private val CloudFallbackColors = listOf(
    Color(0xFF626AFB),
    Color(0xFF8F9DFB),
    Color(0xFFDDE6FD),
    Color(0xFFC9D3FB),
)

private const val GLOW_ALPHA = 0.45f
private val GlowColor = Color(0xFF5659DC)

@Composable
internal fun VoiceOrb(
    amplitudes: List<Float>,
    mode: VoiceOrbMode,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val agslAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val shader = remember { if (agslAvailable) RuntimeShader(CLOUD_SHADER) else null }

    val latestVolume = rememberUpdatedState(amplitudes.lastOrNull() ?: 0f)
    val latestMode = rememberUpdatedState(mode)

    var flowTime by remember { mutableFloatStateOf(0f) }
    var activity by remember { mutableFloatStateOf(CLOUD_LISTEN_ACTIVITY) }
    var cloudSpeed by remember { mutableFloatStateOf(CLOUD_LISTEN_SPEED) }
    var audioScale by remember { mutableFloatStateOf(1f) }
    var currentVolume by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var previousNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val deltaSeconds = ((now - previousNanos) / 1_000_000_000f).coerceAtMost(0.05f)
                previousNanos = now

                val listening = latestMode.value == VoiceOrbMode.Listening
                val raw = if (listening) {
                    (latestVolume.value * 1.35f)
                        .coerceIn(0f, 1f)
                        .pow(0.58f)
                } else {
                    0f
                }
                val volumeRate = if (raw > currentVolume) 14f else 7f
                currentVolume = damp(currentVolume, raw, volumeRate, deltaSeconds)

                val baseTarget = when (latestMode.value) {
                    VoiceOrbMode.Listening -> CLOUD_LISTEN_SCALE
                    VoiceOrbMode.Transcribing -> CLOUD_TRANSCRIBE_SCALE
                    VoiceOrbMode.Waiting -> CLOUD_WAIT_SCALE
                }
                val audioTarget = baseTarget + currentVolume * CLOUD_LISTEN_VOLUME_PULSE
                audioScale = damp(audioScale, audioTarget, CLOUD_SCALE_RATE, deltaSeconds)

                val speedTarget = when (latestMode.value) {
                    VoiceOrbMode.Listening ->
                        CLOUD_LISTEN_SPEED + currentVolume * CLOUD_LISTEN_SPEED_GAIN
                    VoiceOrbMode.Transcribing -> CLOUD_TRANSCRIBE_SPEED
                    VoiceOrbMode.Waiting -> CLOUD_WAIT_SPEED
                }
                val activityTarget = when (latestMode.value) {
                    VoiceOrbMode.Listening ->
                        CLOUD_LISTEN_ACTIVITY + currentVolume * CLOUD_LISTEN_ACTIVITY_GAIN
                    VoiceOrbMode.Transcribing -> CLOUD_TRANSCRIBE_ACTIVITY
                    VoiceOrbMode.Waiting -> CLOUD_WAIT_ACTIVITY
                }
                cloudSpeed = damp(cloudSpeed, speedTarget, 5f, deltaSeconds)
                activity = damp(activity, activityTarget, 5f, deltaSeconds)
                flowTime += deltaSeconds * cloudSpeed
            }
        }
    }

    val orbSize = 96.dp
    val glowSize = VoiceOrbGlowSize
    val description = when (mode) {
        VoiceOrbMode.Listening -> "End voice conversation"
        VoiceOrbMode.Transcribing -> "Transcribing"
        VoiceOrbMode.Waiting -> "End voice conversation"
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(glowSize)
                .graphicsLayer {
                    scaleX = audioScale
                    scaleY = audioScale
                }
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                GlowColor.copy(alpha = GLOW_ALPHA),
                                GlowColor.copy(alpha = GLOW_ALPHA * 0.35f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.minDimension / 2f,
                        ),
                    )
                },
        )

        Box(
            modifier = Modifier
                .size(orbSize)
                .semantics { contentDescription = description }
                .graphicsLayer {
                    scaleX = audioScale
                    scaleY = audioScale
                }
                .drawBehind {
                    if (shader != null) {
                        shader.setFloatUniform("u_resolution", size.width, size.height)
                        shader.setFloatUniform("u_time", flowTime)
                        shader.setFloatUniform("u_activity", activity)
                        drawRect(brush = ShaderBrush(shader))
                    } else {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = CloudFallbackColors,
                                startY = 0f,
                                endY = size.height,
                            ),
                        )
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = mode != VoiceOrbMode.Transcribing,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onTap()
                    },
                ),
        )
    }
}
