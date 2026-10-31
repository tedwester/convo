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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tedwester.convo.core.audio.DeviceSpeechRecognition
import tedwester.convo.core.audio.DeviceSpeechSession
import tedwester.convo.core.audio.VoiceRecorder
import tedwester.convo.features.chat.ChatState
import tedwester.convo.features.chat.data.VoicePreferences
import tedwester.convo.features.chat.data.VoiceTtsMode
import tedwester.convo.features.chat.model.MessageAuthor

internal class ConversationVoiceRecording(
    val isRecording: Boolean,
    val isTranscribing: Boolean,
    val isOrbVisible: Boolean,
    val isAwaitingVoicePlayback: Boolean,
    val recordingAmplitudes: List<Float>,
    val recordingElapsedMs: Long,
    val onMicClick: () -> Unit,
    val cancelRecording: () -> Unit,
    val interruptResponse: () -> Unit,
    val pauseListening: () -> Unit,
    val resumeListening: () -> Unit,
    val playbackStopToken: Int,
    val onVoicePlaybackFinished: () -> Unit,
    val onVoicePlaybackPaused: () -> Unit,
)

private class VoiceSendJobs {
    var transcribe: Job? = null
}

@Composable
internal fun rememberConversationVoiceRecording(
    chatState: ChatState,
    isRunning: Boolean,
    transcriptionOnly: Boolean,
    onRecordingStarted: () -> Unit,
): ConversationVoiceRecording {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val voiceRecorder = remember { VoiceRecorder(context) }
    val deviceSession = remember { DeviceSpeechSession(context.applicationContext) }
    val jobs = remember { VoiceSendJobs() }

    var isRecording by remember { mutableStateOf(false) }
    var isTranscribing by remember { mutableStateOf(false) }
    var sessionActive by remember { mutableStateOf(false) }
    var expectPlayback by remember { mutableStateOf(false) }
    var hadVoiceTurn by remember { mutableStateOf(false) }
    var listeningPaused by remember { mutableStateOf(false) }
    var playbackStopToken by remember { mutableIntStateOf(0) }
    var recordingAmplitudes by remember { mutableStateOf(List(24) { 0f }) }
    var recordingElapsedMs by remember { mutableLongStateOf(0L) }

    fun resetIdle() {
        jobs.transcribe?.cancel()
        jobs.transcribe = null
        voiceRecorder.cancel()
        deviceSession.cancel()
        playbackStopToken += 1
        if (sessionActive && chatState.isRunning) chatState.interruptInFlight()
        isRecording = false
        isTranscribing = false
        sessionActive = false
        expectPlayback = false
        hadVoiceTurn = false
        listeningPaused = false
        recordingElapsedMs = 0L
        recordingAmplitudes = List(24) { 0f }
    }

    /**
     * Stops whatever is happening right now — recording, transcribing, responding,
     * playing — but stays in the call. The hands-free loop picks listening back up.
     */
    fun interruptResponse() {
        jobs.transcribe?.cancel()
        jobs.transcribe = null
        voiceRecorder.cancel()
        deviceSession.cancel()
        playbackStopToken += 1
        if (chatState.isRunning) chatState.interruptInFlight()
        isRecording = false
        isTranscribing = false
        expectPlayback = false
        listeningPaused = false
        recordingElapsedMs = 0L
        recordingAmplitudes = List(24) { 0f }
    }

    /**
     * Drops the microphone while audio plays, keeping the call alive. Listening
     * resumes through [resumeListening] once playback stops.
     */
    fun pauseListening() {
        if (!sessionActive || !isRecording) return
        voiceRecorder.cancel()
        deviceSession.cancel()
        isRecording = false
        listeningPaused = true
        recordingAmplitudes = List(24) { 0f }
    }

    fun useOnDeviceTranscription(): Boolean =
        chatState.voiceStore.load().transcriptionModelId ==
            VoicePreferences.LOCAL_TRANSCRIPTION_MODEL_ID

    fun startRecording() {
        if (isTranscribing || isRecording) return
        listeningPaused = false
        onRecordingStarted()
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        val prefs = chatState.voiceStore.load()
        val onDevice = prefs.transcriptionModelId ==
            VoicePreferences.LOCAL_TRANSCRIPTION_MODEL_ID
        if (onDevice && !DeviceSpeechRecognition.isRecognitionAvailable(context)) {
            Toast.makeText(context, "On-device transcription unavailable", Toast.LENGTH_SHORT)
                .show()
            return
        }
        if (!transcriptionOnly && prefs.mode != VoiceTtsMode.Conversation &&
            !prefs.seenCallConversationNotice
        ) {
            Toast.makeText(context, "Voice calls always use conversation mode", Toast.LENGTH_LONG)
                .show()
            chatState.voiceStore.save(prefs.copy(seenCallConversationNotice = true))
        }
        runCatching { voiceRecorder.start() }
            .onSuccess {
                if (!transcriptionOnly) sessionActive = true
                if (onDevice && !transcriptionOnly) deviceSession.start()
                isRecording = true
                recordingElapsedMs = 0L
                recordingAmplitudes = List(24) { 0f }
            }
    }

    fun resumeListening() {
        listeningPaused = false
        if (!sessionActive || isRecording || isTranscribing || chatState.isRunning) return
        startRecording()
    }

    fun stopAndSendRecording() {
        if (!isRecording || isTranscribing) return
        isTranscribing = true
        val recording = voiceRecorder.stop()
        isRecording = false
        recordingElapsedMs = 0L
        recordingAmplitudes = List(24) { 0f }
        if (recording == null || recording.durationMs < MinVoiceMs) {
            isTranscribing = false
            if (hadVoiceTurn && sessionActive) {
                startRecording()
            } else {
                resetIdle()
            }
            return
        }
        jobs.transcribe?.cancel()
        jobs.transcribe = scope.launch {
            if (transcriptionOnly) {
                try {
                    chatState.sendTranscriptionVoice(recording.bytes, recording.format)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Keep UI idle; error turn is written by sendTranscriptionVoice when possible.
                }
                if (!isActive) return@launch
                var waitedForStartMs = 0L
                while (isActive && !chatState.isRunning && waitedForStartMs < 10_000L) {
                    delay(VoicePollMs)
                    waitedForStartMs += VoicePollMs
                }
                isTranscribing = false
                while (isActive && chatState.isRunning) {
                    delay(VoicePollMs)
                }
                jobs.transcribe = null
                resetIdle()
                return@launch
            }
            val transcript = try {
                if (useOnDeviceTranscription()) {
                    deviceSession.stopAndGetFinal()
                } else {
                    chatState.transcribeRecording(recording.bytes, recording.format)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (!isActive) return@launch
            if (transcript.isNullOrBlank()) {
                isTranscribing = false
                jobs.transcribe = null
                if (sessionActive) startRecording() else resetIdle()
                return@launch
            }
            try {
                chatState.sendVoice(
                    recording.bytes,
                    recording.format,
                    transcript,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Send failed; keep the orb up so the conversation can continue.
            }
            if (!isActive) return@launch
            isTranscribing = false
            jobs.transcribe = null
            hadVoiceTurn = true
            expectPlayback = chatState.isRunning &&
                chatState.selectedModel?.supportsSpeechOutput == true
        }
    }

    val latestStopAndSend = rememberUpdatedState { stopAndSendRecording() }
    val latestStartRecording = rememberUpdatedState { startRecording() }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startRecording()
    }

    fun onMicClick() {
        if (isTranscribing) return
        if (isRecording) {
            stopAndSendRecording()
            return
        }
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

    fun onVoicePlaybackFinished() {
        expectPlayback = false
    }

    fun onVoicePlaybackPaused() {
        expectPlayback = false
    }

    DisposableEffect(Unit) {
        onDispose {
            jobs.transcribe?.cancel()
            voiceRecorder.cancel()
            deviceSession.cancel()
        }
    }

    LaunchedEffect(isRecording, transcriptionOnly) {
        if (!isRecording) return@LaunchedEffect
        val startedAt = System.currentTimeMillis()
        var ema = 0f
        var heardSpeech = false
        var silentForMs = 0L
        val maxDurationMs = if (transcriptionOnly) MaxTranscriptionVoiceMs else MaxVoiceMs
        while (isActive && isRecording) {
            val now = System.currentTimeMillis()
            recordingElapsedMs = now - startedAt
            val next = voiceRecorder.pollAmplitude()
            recordingAmplitudes = (recordingAmplitudes + next).takeLast(24)
            ema += (next - ema) * AmplitudeEma

            val elapsed = recordingElapsedMs
            if (!transcriptionOnly) {
                if (elapsed >= ListenGraceMs && ema >= SpeechThreshold) {
                    heardSpeech = true
                    silentForMs = 0L
                } else {
                    if (heardSpeech && ema < SilenceThreshold) {
                        silentForMs += VoicePollMs
                        if (silentForMs >= SilenceHoldMs) {
                            latestStopAndSend.value()
                            break
                        }
                    } else if (heardSpeech) {
                        silentForMs = 0L
                    }
                }
            }
            if (elapsed >= maxDurationMs) {
                when {
                    transcriptionOnly -> latestStopAndSend.value()
                    heardSpeech -> latestStopAndSend.value()
                    else -> resetIdle()
                }
                break
            }
            delay(VoicePollMs)
        }
    }

    LaunchedEffect(isRunning, sessionActive, isTranscribing, isRecording, expectPlayback, listeningPaused, transcriptionOnly) {
        if (transcriptionOnly || !sessionActive || isRunning || isTranscribing || isRecording || expectPlayback || listeningPaused) {
            return@LaunchedEffect
        }
        latestStartRecording.value()
    }

    LaunchedEffect(isRunning, expectPlayback) {
        if (isRunning || !expectPlayback) return@LaunchedEffect
        val lastAssistantHasAudio = chatState.messages
            .lastOrNull { it.author == MessageAuthor.Assistant }
            ?.hasAudioAttachment() == true
        if (!lastAssistantHasAudio) expectPlayback = false
    }

    return ConversationVoiceRecording(
        isRecording = isRecording,
        isTranscribing = isTranscribing,
        isOrbVisible = sessionActive && !transcriptionOnly,
        isAwaitingVoicePlayback = expectPlayback,
        recordingAmplitudes = recordingAmplitudes,
        recordingElapsedMs = recordingElapsedMs,
        onMicClick = ::onMicClick,
        cancelRecording = ::resetIdle,
        interruptResponse = ::interruptResponse,
        pauseListening = ::pauseListening,
        resumeListening = ::resumeListening,
        playbackStopToken = playbackStopToken,
        onVoicePlaybackFinished = ::onVoicePlaybackFinished,
        onVoicePlaybackPaused = ::onVoicePlaybackPaused,
    )
}

private const val VoicePollMs = 48L
private const val ListenGraceMs = 450L
private const val MinVoiceMs = 350L
private const val MaxVoiceMs = 60_000L
private const val MaxTranscriptionVoiceMs = 60_000L
private const val SilenceHoldMs = 1_250L
private const val SpeechThreshold = 0.10f
private const val SilenceThreshold = 0.065f
private const val AmplitudeEma = 0.35f
