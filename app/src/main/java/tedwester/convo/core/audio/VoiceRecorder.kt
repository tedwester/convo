package tedwester.convo.core.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceRecorder(private val context: Context) {

    data class Recording(
        val bytes: ByteArray,
        val format: String,
        val durationMs: Long,
    )

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0L

    val isRecording: Boolean
        get() = recorder != null

    fun start() {
        stopInternal(deleteFile = true)

        val file = File(context.cacheDir, "convo_voice_${System.currentTimeMillis()}.m4a")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        recorder = mediaRecorder
        outputFile = file
        startedAtMs = System.currentTimeMillis()
    }

    fun pollAmplitude(): Float {
        val raw = recorder?.maxAmplitude ?: 0
        return (raw / 16000f).coerceIn(0f, 1f)
    }

    fun stop(): Recording? {
        val file = outputFile
        val started = startedAtMs
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (_: RuntimeException) {
            recorder?.release()
            file?.delete()
            recorder = null
            outputFile = null
            return null
        } finally {
            recorder = null
            outputFile = null
        }

        if (file == null || !file.exists()) return null
        val bytes = file.readBytes()
        file.delete()
        if (bytes.isEmpty()) return null

        return Recording(
            bytes = bytes,
            format = "m4a",
            durationMs = (System.currentTimeMillis() - started).coerceAtLeast(0L),
        )
    }

    fun cancel() {
        stopInternal(deleteFile = true)
    }

    private fun stopInternal(deleteFile: Boolean) {
        try {
            recorder?.apply {
                runCatching { stop() }
                release()
            }
        } catch (_: Exception) {
            // ignore
        }
        recorder = null
        if (deleteFile) {
            outputFile?.delete()
        }
        outputFile = null
        startedAtMs = 0L
    }
}
