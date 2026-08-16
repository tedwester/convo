package tedwester.convo.ui.chat.conversation

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tedwester.convo.core.audio.VoiceRecorder
import tedwester.convo.features.chat.ChatState

internal class ConversationDictation(
    val isActive: Boolean,
    val isTranscribing: Boolean,
    val amplitudes: List<Float>,
    val scrollPhase: Float,
    val onMicClick: () -> Unit,
    val cancel: () -> Unit,
    val confirm: () -> Unit,
)

@Composable
internal fun rememberConversationDictation(
    chatState: ChatState,
    onBeforeStart: () -> Unit,
): ConversationDictation {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val voiceRecorder = remember { VoiceRecorder(context) }

    var isActive by remember { mutableStateOf(false) }
    var isTranscribing by remember { mutableStateOf(false) }
    var transcribeJob by remember { mutableStateOf<Job?>(null) }
    var amplitudes by remember { mutableStateOf(List(DictationAmplitudeCount) { 0f }) }
    var scrollPhase by remember { mutableFloatStateOf(0f) }

    fun showFailedToast() {
        Toast.makeText(context, "Couldn’t transcribe audio", Toast.LENGTH_SHORT).show()
    }

    fun resetIdle() {
        transcribeJob?.cancel()
        transcribeJob = null
        voiceRecorder.cancel()
        isTranscribing = false
        isActive = false
        amplitudes = List(DictationAmplitudeCount) { 0f }
        scrollPhase = 0f
    }

    fun startRecording() {
        onBeforeStart()
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        runCatching { voiceRecorder.start() }
            .onSuccess {
                amplitudes = List(DictationAmplitudeCount) { 0f }
                scrollPhase = 0f
                isActive = true
            }
    }

    fun insertTranscript(text: String) {
        val current = chatState.input
        val next = when {
            current.isBlank() -> text
            current.last().isWhitespace() -> current + text
            else -> "$current $text"
        }
        chatState.onInputChange(next)
    }

    fun confirm() {
        if (!isActive || isTranscribing) return
        val recording = voiceRecorder.stop()
        if (recording == null || recording.durationMs < 350L) {
            resetIdle()
            showFailedToast()
            return
        }
        isTranscribing = true
        transcribeJob = scope.launch {
            val transcript = try {
                chatState.transcribeRecording(recording.bytes, recording.format)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            isTranscribing = false
            isActive = false
            transcribeJob = null
            if (transcript.isNullOrBlank()) {
                showFailedToast()
            } else {
                insertTranscript(transcript)
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startRecording()
    }

    fun onMicClick() {
        if (isActive) return
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startRecording()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            transcribeJob?.cancel()
            voiceRecorder.cancel()
        }
    }

    LaunchedEffect(isActive, isTranscribing) {
        if (!isActive || isTranscribing) return@LaunchedEffect
        var smoothed = 0f
        var lastSampleAt = System.currentTimeMillis()
        while (true) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastSampleAt
            scrollPhase = (elapsed.toFloat() / DictationSampleIntervalMs).coerceIn(0f, 1f)

            if (elapsed >= DictationSampleIntervalMs) {
                val next = (voiceRecorder.pollAmplitude() * 1.35f).coerceIn(0f, 1f)
                smoothed = (smoothed * 0.58f + next * 0.42f).coerceIn(0f, 1f)
                amplitudes = (amplitudes + smoothed).takeLast(DictationAmplitudeCount)
                lastSampleAt += DictationSampleIntervalMs
                scrollPhase = ((now - lastSampleAt).toFloat() / DictationSampleIntervalMs)
                    .coerceIn(0f, 1f)
            }
            delay(16)
        }
    }

    return ConversationDictation(
        isActive = isActive,
        isTranscribing = isTranscribing,
        amplitudes = amplitudes,
        scrollPhase = scrollPhase,
        onMicClick = ::onMicClick,
        cancel = ::resetIdle,
        confirm = ::confirm,
    )
}

private const val DictationAmplitudeCount = 180
private const val DictationSampleIntervalMs = 32L
