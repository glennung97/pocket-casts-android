package au.com.shiftyjelly.pocketcasts.repositories.notes

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes part of an episode (a local file or an http URL) and writes it as a 16 kHz mono 16-bit WAV,
 * which keeps a two minute clip under 4 MB for upload.
 */
internal object AudioSegmentExtractor {
    private const val TARGET_SAMPLE_RATE = 16_000
    private const val TIMEOUT_US = 10_000L
    private const val MAX_IDLE_LOOPS = 500

    fun extractWav(source: String, startMs: Long, endMs: Long, output: File) {
        val startUs = startMs.coerceAtLeast(0) * 1000
        val endUs = endMs * 1000
        require(endUs > startUs) { "Empty audio range" }

        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            if (source.startsWith("http://") || source.startsWith("https://")) {
                extractor.setDataSource(source, mapOf("User-Agent" to "Pocket Casts"))
            } else {
                extractor.setDataSource(source)
            }
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("No audio track")
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: throw IOException("Unknown audio format")
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val codec = MediaCodec.createDecoderByType(mime)
            decoder = codec
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var isFloat = false
            val resampler = MonoResampler(TARGET_SAMPLE_RATE)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var idleLoops = 0

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex) ?: throw IOException("No decoder input buffer")
                        val size = extractor.readSampleData(buffer, 0)
                        val sampleTimeUs = extractor.sampleTime
                        if (size < 0 || sampleTimeUs > endUs) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, sampleTimeUs, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = codec.outputFormat
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        isFloat = format.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            format.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    }

                    outIndex >= 0 -> {
                        idleLoops = 0
                        val buffer = codec.getOutputBuffer(outIndex)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val reachedEnd = appendFrames(
                                buffer = buffer.slice().order(ByteOrder.nativeOrder()),
                                presentationTimeUs = info.presentationTimeUs,
                                sampleRate = sampleRate,
                                channels = channels,
                                isFloat = isFloat,
                                startUs = startUs,
                                endUs = endUs,
                                resampler = resampler,
                            )
                            if (reachedEnd) outputDone = true
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }

                    inputDone -> {
                        idleLoops++
                        if (idleLoops > MAX_IDLE_LOOPS) outputDone = true
                    }
                }
            }

            val pcm = resampler.toByteArray()
            if (pcm.isEmpty()) throw IOException("No audio decoded in this range")
            writeWav(output, pcm, TARGET_SAMPLE_RATE)
        } finally {
            decoder?.let { codec ->
                runCatching { codec.stop() }
                runCatching { codec.release() }
            }
            extractor.release()
        }
    }

    /** Returns true once a frame past [endUs] is reached. */
    private fun appendFrames(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        sampleRate: Int,
        channels: Int,
        isFloat: Boolean,
        startUs: Long,
        endUs: Long,
        resampler: MonoResampler,
    ): Boolean {
        val channelCount = channels.coerceAtLeast(1)
        if (isFloat) {
            val floats = buffer.asFloatBuffer()
            val frames = floats.remaining() / channelCount
            for (frame in 0 until frames) {
                val timeUs = presentationTimeUs + frame * 1_000_000L / sampleRate
                if (timeUs >= endUs) return true
                var sum = 0f
                for (channel in 0 until channelCount) sum += floats.get(frame * channelCount + channel)
                if (timeUs >= startUs) resampler.push(sum / channelCount, sampleRate)
            }
        } else {
            val shorts = buffer.asShortBuffer()
            val frames = shorts.remaining() / channelCount
            for (frame in 0 until frames) {
                val timeUs = presentationTimeUs + frame * 1_000_000L / sampleRate
                if (timeUs >= endUs) return true
                var sum = 0f
                for (channel in 0 until channelCount) sum += shorts.get(frame * channelCount + channel) / 32768f
                if (timeUs >= startUs) resampler.push(sum / channelCount, sampleRate)
            }
        }
        return false
    }

    private fun writeWav(file: File, pcm: ByteArray, sampleRate: Int) {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + pcm.size)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1) // PCM
            putShort(1) // mono
            putInt(sampleRate)
            putInt(sampleRate * 2)
            putShort(2)
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcm.size)
        }
        file.outputStream().use { stream ->
            stream.write(header.array())
            stream.write(pcm)
        }
    }

    /** Averages input samples down (or repeats them up) to the target rate. Plenty for speech recognition. */
    private class MonoResampler(private val outputRate: Int) {
        private val output = ByteArrayOutputStream()
        private var sum = 0.0
        private var count = 0
        private var position = 0.0

        fun push(sample: Float, inputRate: Int) {
            sum += sample
            count++
            position += outputRate.toDouble() / inputRate
            if (position >= 1.0) {
                val value = (sum / count).coerceIn(-1.0, 1.0)
                val pcm = (value * Short.MAX_VALUE).toInt()
                while (position >= 1.0) {
                    output.write(pcm and 0xFF)
                    output.write((pcm shr 8) and 0xFF)
                    position -= 1.0
                }
                sum = 0.0
                count = 0
            }
        }

        fun toByteArray(): ByteArray = output.toByteArray()
    }
}
