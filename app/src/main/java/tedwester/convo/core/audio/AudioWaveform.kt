package tedwester.convo.core.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.util.LruCache
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val DefaultBarCount = 88
private const val EnvelopeFrames = 256
private const val DecodeTimeoutMs = 45_000L
private const val DequeueTimeoutUs = 10_000L

internal data class ExtractedWaveform(
    val amplitudes: FloatArray,
    val durationMs: Long,
)

private val cache = LruCache<String, ExtractedWaveform>(48)

/**
 * Peak-envelope bars for a local audio file, used by the inline playback
 * waveform. Results are cached by path. Falls back to a quiet placeholder
 * if the file can't be decoded.
 *
 * Decoding uses a software codec and an in-memory copy so it never contends
 * with [android.media.MediaPlayer] for the hardware decoder or the file.
 * [ExtractedWaveform.durationMs] is counted from decoded PCM so MP3 files
 * without a Xing header still report their true length.
 */
internal fun extractAudioWaveform(
    path: String,
    barCount: Int = DefaultBarCount,
): ExtractedWaveform {
    val key = "$path#$barCount"
    cache.get(key)?.let { return it }
    val extracted = runCatching { decodeWaveform(path, barCount) }.getOrNull()
        ?.takeIf { it.amplitudes.any { sample -> sample > 0.05f } }
        ?: ExtractedWaveform(placeholderWaveform(barCount), 0L)
    cache.put(key, extracted)
    return extracted
}

internal fun placeholderWaveform(barCount: Int = DefaultBarCount): FloatArray =
    FloatArray(barCount) { i ->
        val t = i / barCount.toFloat()
        (0.10f + 0.07f * (0.5f + 0.5f * sin(t * 17.0).toFloat()))
            .coerceIn(0.08f, 0.22f)
    }

private fun decodeWaveform(path: String, barCount: Int): ExtractedWaveform {
    val bytes = File(path).readBytes()
    if (bytes.isEmpty()) return ExtractedWaveform(placeholderWaveform(barCount), 0L)

    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    try {
        extractor.setDataSource(ByteArrayMediaDataSource(bytes))
        val track = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index)
                .getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        } ?: return ExtractedWaveform(placeholderWaveform(barCount), 0L)

        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME)
            ?: return ExtractedWaveform(placeholderWaveform(barCount), 0L)
        val headerDurationMs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
            (format.getLong(MediaFormat.KEY_DURATION) / 1_000L).coerceAtLeast(0L)
        } else {
            0L
        }

        val decoder = createSoftwareDecoder(mime)
        codec = decoder
        decoder.configure(format, null, null, 0)
        decoder.start()

        val envelopes = ArrayList<Float>(2048)
        var envelopePeak = 0f
        var envelopeCount = 0
        var pcmFrames = 0L
        var sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } else {
            0
        }
        fun pushAbs(sample: Float) {
            pcmFrames++
            envelopePeak = max(envelopePeak, sample)
            envelopeCount++
            if (envelopeCount >= EnvelopeFrames) {
                envelopes.add(envelopePeak)
                envelopePeak = 0f
                envelopeCount = 0
            }
        }

        val info = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false
        val deadline = SystemClock.elapsedRealtime() + DecodeTimeoutMs

        while (!sawOutputEos && SystemClock.elapsedRealtime() < deadline) {
            if (!sawInputEos) {
                val inIndex = decoder.dequeueInputBuffer(DequeueTimeoutUs)
                if (inIndex >= 0) {
                    val input = decoder.getInputBuffer(inIndex)
                    val sampleSize = if (input != null) extractor.readSampleData(input, 0) else -1
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(
                            inIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        sawInputEos = true
                    } else {
                        decoder.queueInputBuffer(
                            inIndex,
                            0,
                            sampleSize,
                            extractor.sampleTime.coerceAtLeast(0L),
                            0,
                        )
                        extractor.advance()
                    }
                }
            }

            when (val outIndex = decoder.dequeueOutputBuffer(info, DequeueTimeoutUs)) {
                MediaCodec.INFO_TRY_AGAIN_LATER,
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED,
                -> {
                    if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val outFormat = decoder.outputFormat
                        if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                    }
                }
                else -> if (outIndex >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEos = true
                    }
                    if (info.size > 0) {
                        decoder.getOutputBuffer(outIndex)?.let { pcm ->
                            accumulatePcm(pcm, info, decoder.outputFormat, ::pushAbs)
                        }
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                }
            }
        }

        if (envelopeCount > 0) envelopes.add(envelopePeak)
        val pcmDurationMs = if (sampleRate > 0 && pcmFrames > 0L) {
            pcmFrames * 1_000L / sampleRate
        } else {
            0L
        }
        return ExtractedWaveform(
            amplitudes = resampleToBars(envelopes, barCount),
            durationMs = max(pcmDurationMs, headerDurationMs),
        )
    } finally {
        runCatching {
            codec?.stop()
            codec?.release()
        }
        extractor.release()
    }
}

/**
 * Prefer a software decoder so waveform extraction cannot steal the hardware
 * audio decoder [android.media.MediaPlayer] is using for playback.
 */
private fun createSoftwareDecoder(mime: String): MediaCodec {
    val software = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.firstOrNull { info ->
        !info.isEncoder &&
            info.isSoftwareOnly &&
            info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
    }
    if (software != null) {
        return MediaCodec.createByCodecName(software.name)
    }
    val google = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.firstOrNull { info ->
        !info.isEncoder &&
            info.name.contains("google", ignoreCase = true) &&
            info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
    }
    if (google != null) {
        return MediaCodec.createByCodecName(google.name)
    }
    return MediaCodec.createDecoderByType(mime)
}

private class ByteArrayMediaDataSource(
    private val data: ByteArray,
) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position < 0) return -1
        if (position >= data.size) return -1
        val length = min(size, data.size - position.toInt())
        System.arraycopy(data, position.toInt(), buffer, offset, length)
        return length
    }

    override fun getSize(): Long = data.size.toLong()

    override fun close() {}
}

private fun accumulatePcm(
    buffer: ByteBuffer,
    info: MediaCodec.BufferInfo,
    format: MediaFormat,
    pushAbs: (Float) -> Unit,
) {
    buffer.position(info.offset)
    buffer.limit(info.offset + info.size)
    val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
        format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
    } else {
        1
    }
    val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
        format.getInteger(MediaFormat.KEY_PCM_ENCODING)
    } else {
        AudioFormat.ENCODING_PCM_16BIT
    }

    when (encoding) {
        AudioFormat.ENCODING_PCM_FLOAT -> {
            val floats = buffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
            while (floats.remaining() >= channels) {
                var peak = 0f
                repeat(channels) { peak = max(peak, abs(floats.get())) }
                pushAbs(peak)
            }
        }
        else -> {
            val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
            while (shorts.remaining() >= channels) {
                var peak = 0f
                repeat(channels) {
                    peak = max(peak, abs(shorts.get().toFloat()) / 32768f)
                }
                pushAbs(peak)
            }
        }
    }
}

private fun resampleToBars(envelopes: List<Float>, barCount: Int): FloatArray {
    if (envelopes.isEmpty() || barCount <= 0) return placeholderWaveform(barCount.coerceAtLeast(1))
    val out = FloatArray(barCount)
    val n = envelopes.size
    for (i in 0 until barCount) {
        val start = (i.toLong() * n / barCount).toInt()
        val end = ((i + 1L) * n / barCount).toInt().coerceAtLeast(start + 1)
        var peak = 0f
        for (j in start until end.coerceAtMost(n)) {
            peak = max(peak, envelopes[j])
        }
        out[i] = peak
    }
    val maxPeak = out.maxOrNull()?.takeIf { it > 1e-5f } ?: return placeholderWaveform(barCount)
    for (i in out.indices) {
        out[i] = sqrt((out[i] / maxPeak).toDouble()).toFloat().coerceIn(0.06f, 1f)
    }
    return out
}
