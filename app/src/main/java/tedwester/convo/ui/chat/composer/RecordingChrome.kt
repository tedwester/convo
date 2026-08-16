package tedwester.convo.ui.chat.composer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tedwester.convo.ui.chat.composerHintHighlightRing
import tedwester.convo.ui.icons.ConvoIcons

private enum class ActionButtonIcon {
    Running,
    Recording,
    Send,
    Waveform,
    SimpleMic,
}

@Composable
internal fun ActionButton(
    dark: Boolean,
    canSend: Boolean,
    isRunning: Boolean,
    isRecording: Boolean,
    supportsVoiceInput: Boolean,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    forceWaveform: Boolean = false,
    highlighted: Boolean = false,
    micOnlyMode: Boolean = false,
) {
    val fill = when {
        isRecording -> Color(0xFFE5484D)
        dark -> Color.White
        else -> MaterialTheme.colorScheme.primary
    }
    val tint = when {
        isRecording -> Color.White
        dark -> Color.Black
        else -> MaterialTheme.colorScheme.onPrimary
    }
    val iconState = when {
        isRunning -> ActionButtonIcon.Running
        isRecording -> ActionButtonIcon.Recording
        micOnlyMode -> ActionButtonIcon.SimpleMic
        forceWaveform -> ActionButtonIcon.Waveform
        canSend || !supportsVoiceInput -> ActionButtonIcon.Send
        else -> ActionButtonIcon.Waveform
    }

    Box(
        modifier = Modifier
            .size(ActionButtonSize)
            .background(color = fill, shape = CircleShape)
            .composerHintHighlightRing(highlighted)
            .clickable(
                enabled = isRunning || isRecording || canSend ||
                    supportsVoiceInput || forceWaveform || micOnlyMode,
                onClick = {
                    when {
                        isRunning -> onSend()
                        isRecording -> onMicClick()
                        forceWaveform || micOnlyMode -> onMicClick()
                        canSend -> onSend()
                        supportsVoiceInput -> onMicClick()
                        else -> onSend()
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = iconState,
            transitionSpec = {
                val popIn = (
                    fadeIn(tween(180)) +
                        scaleIn(
                            initialScale = 0.55f,
                            animationSpec = spring(
                                dampingRatio = 0.62f,
                                stiffness = 420f,
                            ),
                        ) +
                        slideInVertically(tween(180)) { it / 3 }
                    ) togetherWith (
                    fadeOut(tween(140)) +
                        scaleOut(targetScale = 0.72f, animationSpec = tween(140))
                    )
                val popOut = (
                    fadeIn(tween(160)) +
                        scaleIn(initialScale = 0.72f, animationSpec = tween(160))
                    ) togetherWith (
                    fadeOut(tween(140)) +
                        scaleOut(targetScale = 0.55f, animationSpec = tween(140)) +
                        slideOutVertically(tween(140)) { -it / 3 }
                    )
                when {
                    initialState == ActionButtonIcon.Waveform && targetState == ActionButtonIcon.Send -> popIn
                    initialState == ActionButtonIcon.Running &&
                        targetState != ActionButtonIcon.Recording -> popIn
                    initialState == ActionButtonIcon.Send && targetState == ActionButtonIcon.Waveform -> popOut
                    initialState == ActionButtonIcon.Send && targetState == ActionButtonIcon.SimpleMic -> popOut
                    initialState != ActionButtonIcon.Recording &&
                        targetState == ActionButtonIcon.Running -> popOut
                    else -> fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                }.using(SizeTransform(clip = false) { _, _ -> snap() })
            },
            label = "actionButtonIcon",
        ) { state ->
            when (state) {
                ActionButtonIcon.Running -> {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .background(tint, RoundedCornerShape(2.dp)),
                    )
                }
                ActionButtonIcon.Recording -> {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = if (micOnlyMode) "Stop recording" else "End conversation",
                        tint = tint,
                        modifier = Modifier.size(ActionIconSize),
                    )
                }
                ActionButtonIcon.Send -> {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = "Send",
                        tint = tint,
                        modifier = Modifier.size(ActionIconSize),
                    )
                }
                ActionButtonIcon.SimpleMic -> {
                    Icon(
                        painter = ConvoIcons.Mic(),
                        contentDescription = "Record",
                        tint = tint,
                        modifier = Modifier.size(ActionIconSize),
                    )
                }
                ActionButtonIcon.Waveform -> {
                    AnimatedAudioLines(
                        tint = tint,
                        modifier = Modifier.size(ActionIconSize),
                    )
                }
            }
        }
    }
}

@Composable
internal fun AnimatedAudioLines(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val baseFractions = listOf(0.17f, 0.61f, 1f, 0.39f, 0.72f, 0.17f)
    val (slow, mid, fast) = rememberWaveModulationPhases("audioLines")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(1.75.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        baseFractions.forEachIndexed { index, base ->
            val wave = layeredWave(slow, mid, fast, barPhaseOffset(index))
            val level = (base * (0.58f + 0.42f * wave)).coerceIn(0.14f, 1f)
            Box(
                modifier = Modifier
                    .width(1.75.dp)
                    .fillMaxHeight(level)
                    .clip(RoundedCornerShape(1.dp))
                    .background(tint),
            )
        }
    }
}
