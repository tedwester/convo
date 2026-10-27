package tedwester.convo.core.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Raw PCM helpers.
 *
 * OpenRouter returns uncompressed audio for the `pcm` response format: headerless 16-bit
 * little-endian samples. Android's players cannot open that on its own, so it is wrapped
 * in a WAV container before it is stored.
 */
object PcmAudio {

    /** Sample rate of the speech providers that return raw PCM. */
    const val SpeechSampleRate = 24_000

    const val BitsPerSample = 16

    const val Channels = 1

    /**
     * Wraps headerless PCM samples in a WAV container.
     *
     * @param samples raw little-endian 16-bit samples
     * @param sampleRate samples per second
     * @param channels number of channels
     */
    fun wrapInWav(
        samples: ByteArray,
        sampleRate: Int = SpeechSampleRate,
        channels: Int = Channels,
    ): ByteArray {
        val byteRate = sampleRate * channels * BitsPerSample / 8
        val blockAlign = channels * BitsPerSample / 8
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + samples.size)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1.toShort())
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(BitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(samples.size)
        }.array()
        return header + samples
    }
}
