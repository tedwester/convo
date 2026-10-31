package tedwester.convo.core.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Transcription through the phone itself instead of an API model.
 *
 * Prefer the dedicated on-device recognizer when the system has one
 * ([SpeechRecognizer.createOnDeviceSpeechRecognizer]); otherwise fall back to
 * the regular recognizer asked to prefer its offline packs. The fallback may
 * still reach the network, which is why callers should label it honestly.
 * Either way this only handles live microphone input: recorded files still go
 * through the API path.
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

    /**
     * Languages the system recognizer reports, e.g. "en-US". Best effort: the
     * implementation may answer with nothing, in which case this stays null.
     */
    fun querySupportedLanguages(context: Context, onResult: (List<String>) -> Unit) {
        val intent = Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS)
        val appContext = context.applicationContext
        runCatching {
            appContext.sendOrderedBroadcast(
                intent,
                null,
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        val languages = getResultExtras(true)?.getStringArrayList(
                            RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES,
                        ).orEmpty().filter { it.isNotBlank() }.distinct()
                        onResult(languages)
                    }
                },
                Handler(Looper.getMainLooper()),
                0,
                null,
                null,
            )
        }.onFailure { onResult(emptyList()) }
    }
}

/** Languages the on-device recognizer reports, or null while unanswered. */
@Composable
fun rememberDeviceSpeechLanguages(): List<String>? {
    val context = LocalContext.current
    var languages by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(Unit) {
        if (!DeviceSpeechRecognition.isRecognitionAvailable(context)) {
            languages = emptyList()
            return@LaunchedEffect
        }
        DeviceSpeechRecognition.querySupportedLanguages(context) { languages = it }
    }
    return languages
}

/** Picker-sized summary: availability first, then the languages this device reports. */
fun deviceSpeechSublabel(context: Context, languages: List<String>?): String {
    if (!DeviceSpeechRecognition.isRecognitionAvailable(context)) {
        return "Not available on this device"
    }
    if (languages == null) return "Checking on-device languages…"
    val scope = if (DeviceSpeechRecognition.isOnDeviceAvailable(context)) {
        "On-device"
    } else {
        "System speech"
    }
    if (languages.isEmpty()) return "$scope (offline preferred)"
    val shown = languages.take(3).joinToString(", ")
    val rest = if (languages.size > 3) " +${languages.size - 3} more" else ""
    return "$scope • $shown$rest"
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

    /** Last recognizer error code, if the turn failed. Null when it worked. */
    var lastError: Int? = null
        private set

    fun start(): Boolean {
        cancel()
        if (!DeviceSpeechRecognition.isRecognitionAvailable(appContext)) return false
        val session = runCatching {
            // Guaranteed on-device when the system has the service; falls back
            // to the regular recognizer (offline preferred) below it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                DeviceSpeechRecognition.isOnDeviceAvailable(appContext)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }
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
            override fun onError(error: Int) {
                lastError = error
                settle(null)
            }
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
        lastError = null
    }

    companion object {
        /** The recognizer reports these when the offline language pack is the problem. */
        val LANGUAGE_ERRORS = setOf(
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        )
    }
}
