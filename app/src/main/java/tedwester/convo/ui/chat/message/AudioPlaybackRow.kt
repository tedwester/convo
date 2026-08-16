package tedwester.convo.ui.chat.message

import android.media.MediaPlayer
import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import tedwester.convo.core.audio.extractAudioWaveform
import tedwester.convo.core.audio.placeholderWaveform
import tedwester.convo.ui.components.SpinningLoader
import tedwester.convo.ui.icons.ConvoIcons
import tedwester.convo.ui.theme.InterFontFamily
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.abs

private val PlayerTabHeight = 46.dp
private val PlayerTabRadius = 12.dp
private val TimestampWidth = 38.dp
private val WaveformGap = 1.6.dp
private const val PlayedAlpha = 0.82f
private const val UnplayedAlpha = 0.22f

@Composable
internal fun AudioPlaybackRow(
    path: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    showUi: Boolean = true,
    playbackStopToken: Int = 0,
    onAutoPlayStarted: () -> Unit = {},
    onPlaybackFinished: () -> Unit = {},
    onPlaybackPaused: () -> Unit = {},
) {
    var player by remember(path) { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember(path) { mutableStateOf(false) }
    var isPreparing by remember(path) { mutableStateOf(true) }
    var durationMs by remember(path) { mutableFloatStateOf(0f) }
    var positionMs by remember(path) { mutableFloatStateOf(0f) }
    var userSeeking by remember(path) { mutableStateOf(false) }
    var seekProgress by remember(path) { mutableFloatStateOf(0f) }
    var pendingSeekMs by remember(path) { mutableFloatStateOf(-1f) }
    var waveform by remember(path) { mutableStateOf(placeholderWaveform()) }
    var playStartElapsed by remember(path) { mutableLongStateOf(0L) }
    var playStartOffsetMs by remember(path) { mutableFloatStateOf(0f) }
    val latestOnAutoPlayStarted = rememberUpdatedState(onAutoPlayStarted)
    val latestOnPlaybackFinished = rememberUpdatedState(onPlaybackFinished)
    val latestOnPlaybackPaused = rememberUpdatedState(onPlaybackPaused)
    var hasAutoPlayed by remember(path) { mutableStateOf(false) }
    var didAutoPlay by remember(path) { mutableStateOf(false) }
    val latestDidAutoPlay = rememberUpdatedState(didAutoPlay)

    LaunchedEffect(player, isPlaying) {
        while (isPlaying && player != null) {
            if (!userSeeking) {
                val elapsed = playStartOffsetMs +
                    (SystemClock.elapsedRealtime() - playStartElapsed).toFloat()
                positionMs = elapsed
                if (elapsed > durationMs && durationMs > 0f) {
                    durationMs = elapsed
                }
            }
            delay(16L)
        }
    }

    LaunchedEffect(path) {
        isPreparing = true
        isPlaying = false
        positionMs = 0f
        durationMs = 0f
        player = null
        waveform = placeholderWaveform()

        val waveformJob = launch(Dispatchers.IO) {
            val extracted = extractAudioWaveform(path)
            waveform = extracted.amplitudes
            val decoded = extracted.durationMs.toFloat()
            if (decoded > durationMs) durationMs = decoded
        }

        val mediaPlayer = MediaPlayer()
        try {
            suspendCancellableCoroutine { cont ->
                mediaPlayer.setOnPreparedListener { prepared ->
                    val reported = prepared.duration.toFloat().coerceAtLeast(0f)
                    if (reported > durationMs) durationMs = reported
                    val target = when {
                        pendingSeekMs >= 0f -> pendingSeekMs
                        seekProgress > 0f -> seekProgress * durationMs
                        else -> -1f
                    }
                    if (target >= 0f && durationMs > 0f) {
                        prepared.seekClosest(target.toInt())
                        positionMs = target
                        pendingSeekMs = -1f
                    }
                    isPreparing = false
                    player = prepared
                    if (cont.isActive) cont.resume(Unit)
                }
                mediaPlayer.setOnCompletionListener {
                    isPlaying = false
                    val elapsed = playStartOffsetMs +
                        (SystemClock.elapsedRealtime() - playStartElapsed).toFloat()
                    if (elapsed > durationMs) durationMs = elapsed
                    positionMs = durationMs
                    if (latestDidAutoPlay.value) latestOnPlaybackFinished.value()
                }
                mediaPlayer.setOnErrorListener { _, _, _ ->
                    isPreparing = false
                    if (cont.isActive) cont.resume(Unit)
                    true
                }
                try {
                    mediaPlayer.setDataSource(path)
                    mediaPlayer.prepareAsync()
                } catch (_: Exception) {
                    isPreparing = false
                    if (cont.isActive) cont.resume(Unit)
                }
            }
            awaitCancellation()
        } finally {
            waveformJob.cancel()
            mediaPlayer.setOnPreparedListener(null)
            mediaPlayer.setOnCompletionListener(null)
            mediaPlayer.setOnErrorListener(null)
            runCatching { mediaPlayer.release() }
            if (player === mediaPlayer) player = null
            isPlaying = false
        }
    }

    fun seekToProgress(progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        seekProgress = clamped
        if (durationMs <= 0f) return
        val target = clamped * durationMs
        positionMs = target
        val current = player
        if (current != null) {
            current.seekClosest(target.toInt())
            if (isPlaying) {
                playStartOffsetMs = target
                playStartElapsed = SystemClock.elapsedRealtime()
            }
        } else {
            pendingSeekMs = target
        }
    }

    fun startPlayback(existing: MediaPlayer) {
        if (positionMs >= durationMs && durationMs > 0f) {
            existing.seekClosest(0)
            positionMs = 0f
        }
        playStartOffsetMs = positionMs
        playStartElapsed = SystemClock.elapsedRealtime()
        runCatching { existing.start() }
            .onSuccess { isPlaying = true }
    }

    val onToggle: () -> Unit = {
        val existing = player
        if (existing != null && !isPreparing) {
            if (isPlaying) {
                runCatching { existing.pause() }
                existing.seekClosest(positionMs.toInt())
                isPlaying = false
                if (latestDidAutoPlay.value) latestOnPlaybackPaused.value()
            } else {
                startPlayback(existing)
            }
        }
    }

    LaunchedEffect(player, isPreparing, autoPlay) {
        if (!autoPlay || hasAutoPlayed || isPreparing) return@LaunchedEffect
        val existing = player ?: return@LaunchedEffect
        hasAutoPlayed = true
        didAutoPlay = true
        latestOnAutoPlayStarted.value()
        startPlayback(existing)
    }

    LaunchedEffect(playbackStopToken) {
        if (playbackStopToken == 0) return@LaunchedEffect
        val existing = player ?: return@LaunchedEffect
        if (isPlaying) {
            runCatching { existing.pause() }
            isPlaying = false
        }
    }

    if (!showUi) return

    val progress = when {
        userSeeking -> seekProgress
        durationMs > 0f -> (positionMs / durationMs).coerceIn(0f, 1f)
        else -> 0f
    }
    val tabColor = MaterialTheme.colorScheme.surfaceVariant
    val waveColor = MaterialTheme.colorScheme.onSurface
    val iconTint = waveColor.copy(alpha = if (isPreparing) 0.3f else 0.75f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(PlayerTabHeight)
            .clip(RoundedCornerShape(PlayerTabRadius))
            .background(tabColor)
            .padding(start = 6.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clickableNoRipple(enabled = !isPreparing, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            if (isPreparing) {
                SpinningLoader(
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Icon(
                    painter = if (isPlaying) ConvoIcons.Pause() else ConvoIcons.Play(),
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        AudioWaveformScrubber(
            amplitudes = waveform,
            progress = progress,
            scrubbing = userSeeking,
            color = waveColor,
            onScrubStart = { fraction ->
                userSeeking = true
                seekToProgress(fraction)
            },
            onScrub = { fraction -> seekToProgress(fraction) },
            onScrubEnd = { userSeeking = false },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(vertical = 7.dp),
        )

        Text(
            text = formatMillis(
                if (isPlaying || userSeeking || positionMs > 0f) {
                    positionMs.toLong()
                } else {
                    durationMs.toLong()
                },
            ),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = InterFontFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.width(TimestampWidth),
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

@Composable
private fun AudioWaveformScrubber(
    amplitudes: FloatArray,
    progress: Float,
    scrubbing: Boolean,
    color: Color,
    onScrubStart: (Float) -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrubFade by animateFloatAsState(
        targetValue = if (scrubbing) 1f else 0f,
        animationSpec = tween(180, easing = FastOutSlowInEasing),
        label = "waveformScrubFade",
    )

    val onScrubStartState = rememberUpdatedState(onScrubStart)
    val onScrubState = rememberUpdatedState(onScrub)
    val onScrubEndState = rememberUpdatedState(onScrubEnd)

    Canvas(
        modifier = modifier
            .graphicsLayer { alpha = 1f - 0.18f * scrubFade }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    fun fraction(x: Float) = (x / width).coerceIn(0f, 1f)
                    try {
                        onScrubStartState.value(fraction(down.position.x))
                        drag(down.id) { change ->
                            change.consume()
                            onScrubState.value(fraction(change.position.x))
                        }
                    } finally {
                        onScrubEndState.value()
                    }
                }
            },
    ) {
        val count = amplitudes.size.coerceAtLeast(1)
        val gap = WaveformGap.toPx()
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1.2f)
        val playheadX = progress.coerceIn(0f, 1f) * size.width
        val playheadBlend = (size.width * 0.045f).coerceAtLeast(barWidth * 2f)
        val fadeRadius = size.width * (0.12f + 0.28f * scrubFade)
        val minBar = 2.dp.toPx()
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)

        for (i in 0 until count) {
            val amp = amplitudes.getOrElse(i) { 0.1f }.coerceIn(0.06f, 1f)
            val x = i * (barWidth + gap)
            val h = (minBar + amp * (size.height - minBar)).coerceAtMost(size.height)
            val centerX = x + barWidth / 2f
            val playedMix = ((playheadX - centerX) / playheadBlend + 0.5f).coerceIn(0f, 1f)
            val baseAlpha = UnplayedAlpha + (PlayedAlpha - UnplayedAlpha) * playedMix
            val dimFactor = if (centerX <= playheadX) {
                1f
            } else {
                val proximity = 1f - (abs(centerX - playheadX) / fadeRadius).coerceIn(0f, 1f)
                1f - scrubFade * (1f - (0.28f + 0.72f * proximity))
            }
            drawRoundRect(
                color = color.copy(alpha = baseAlpha * dimFactor),
                topLeft = Offset(x, (size.height - h) / 2f),
                size = Size(barWidth, h),
                cornerRadius = radius,
            )
        }
    }
}

private fun MediaPlayer.seekClosest(ms: Int) {
    runCatching { seekTo(ms.coerceAtLeast(0).toLong(), MediaPlayer.SEEK_CLOSEST) }
}

private fun formatMillis(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%d:%02d", min, sec)
}

@Composable
private fun Modifier.clickableNoRipple(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.then(
        clickable(
            enabled = enabled,
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        ),
    )
}
