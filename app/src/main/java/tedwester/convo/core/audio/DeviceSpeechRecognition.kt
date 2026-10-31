package tedwester.convo.core.audio

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Transcription through the phone's own speech service instead of an API model.
 *
 * This is the system recognizer (usually Google's), asked to prefer its offline
 * language packs — not a bundled model. Anything it can't do on-device may still
 * use the network, which is why the picker labels it honestly. It only handles
 * live microphone input: recorded files still go through the API path.
 */
object DeviceSpeechRecognition {

    fun isRecognitionAvailable(context: Context): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    fun isOnDeviceAvailable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            }.getOrDefault(false)

    fun statusLabel(context: Context): String = when {
        isOnDeviceAvailable(context) -> "On-device pack ready"
        isRecognitionAvailable(context) -> "System speech service (offline preferred)"
        else -> "Not available on this device"
    }
}

/**
 * One live recognition turn. Main-thread only: create, start, stop and cancel
 * all run on the calling thread, which is where the recognizer delivers callbacks.
 */
class DeviceSpeechSession(private val appContext: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var latestPartial: String? = null
    private var finished: String? = null
    private var settled = false
    private var resumeWaiting: (() -> Unit)? = null

    fun start(): Boolean {
        cancel()
        if (!DeviceSpeechRecognition.isRecognitionAvailable(appContext)) return false
        val session = runCatching {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        }.getOrNull() ?: return false
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.getDefault().toLanguageTag(),
            )
        }
        session.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onError(error: Int) = settle(null)
            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull { it.isNotBlank() }
                settle(text ?: latestPartial)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull { it.isNotBlank() }
                    ?.let { latestPartial = it }
            }
        })
        return runCatching {
            session.startListening(intent)
            recognizer = session
            true
        }.getOrDefault(false)
    }

    private fun settle(text: String?) {
        if (settled) return
        settled = true
        finished = text?.takeIf { it.isNotBlank() } ?: latestPartial?.takeIf { it.isNotBlank() }
        resumeWaiting?.invoke()
        resumeWaiting = null
    }

    suspend fun stopAndGetFinal(timeoutMs: Long = 3_000L): String? {
        val session = recognizer
        recognizer = null
        if (session == null) {
            val fallback = finished ?: latestPartial
            cancel()
            return fallback?.takeIf { it.isNotBlank() }
        }
        return try {
            withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    if (settled) {
                        cont.resume(finished)
                        return@suspendCancellableCoroutine
                    }
                    resumeWaiting = {
                        if (cont.isActive) cont.resume(finished)
                    }
                    runCatching { session.stopListening() }
                }
            } ?: finished
        } finally {
            runCatching { session.destroy() }
            resumeWaiting = null
        }
    }

    fun cancel() {
        resumeWaiting = null
        recognizer?.let { session ->
            runCatching {
                session.cancel()
                session.destroy()
            }
        }
        recognizer = null
        latestPartial = null
        finished = null
        settled = false
    }
}
