package com.example.videozoomplayer

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

class NativeOutputAudioProcessor(
    private val nativePcmProcessor: NativePcmProcessor
) : BaseAudioProcessor() {

    private var active = false
    private var sampleRateHz = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var frameValueCount = 0

    private var pendingValues = IntArray(0)
    private var pendingCount = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRateHz = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        frameValueCount = FRAME_SAMPLES_PER_CHANNEL * channelCount
        active = sampleRateHz == TARGET_SAMPLE_RATE_HZ &&
            channelCount == TARGET_CHANNEL_COUNT &&
            isSupportedPcmEncoding(encoding)

        return if (active) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!active) {
            val passthrough = replaceOutputBuffer(inputBuffer.remaining())
            passthrough.put(inputBuffer)
            passthrough.flip()
            return
        }
        val inputValues = decodeToInt32Values(inputBuffer, encoding)
        if (inputValues.isEmpty()) return

        appendPending(inputValues)
        val processedValues = processFullFrames()
        if (processedValues.isEmpty()) return

        val outputBuffer = replaceOutputBuffer(processedValues.size * bytesPerSample(encoding))
        encodeFromInt32Values(processedValues, encoding, outputBuffer)
        outputBuffer.flip()
    }

    override fun onQueueEndOfStream() {
        if (!active || pendingCount == 0) return

        val tail = IntArray(pendingCount)
        System.arraycopy(pendingValues, 0, tail, 0, pendingCount)
        pendingCount = 0

        val outputBuffer = replaceOutputBuffer(tail.size * bytesPerSample(encoding))
        encodeFromInt32Values(tail, encoding, outputBuffer)
        outputBuffer.flip()
    }

    override fun onFlush() {
        pendingCount = 0
        nativePcmProcessor.reset(sampleRateHz, channelCount)
    }

    override fun onReset() {
        pendingValues = IntArray(0)
        pendingCount = 0
        active = false
        sampleRateHz = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
        frameValueCount = 0
    }

    fun release() {
        nativePcmProcessor.release()
    }

    private fun processFullFrames(): IntArray {
        val frameCount = if (frameValueCount > 0) pendingCount / frameValueCount else 0
        if (frameCount <= 0) return IntArray(0)

        val output = IntArray(frameCount * frameValueCount)
        var outputOffset = 0
        repeat(frameCount) {
            val frame = IntArray(frameValueCount)
            System.arraycopy(pendingValues, 0, frame, 0, frameValueCount)

            val processed = nativePcmProcessor.processFrame20ms(frame, sampleRateHz, channelCount)
            val outputFrame = if (processed == null || processed.size != frameValueCount) frame else processed
            System.arraycopy(outputFrame, 0, output, outputOffset, frameValueCount)
            outputOffset += frameValueCount

            val remaining = pendingCount - frameValueCount
            if (remaining > 0) {
                System.arraycopy(pendingValues, frameValueCount, pendingValues, 0, remaining)
            }
            pendingCount = remaining
        }
        return output
    }

    private fun appendPending(values: IntArray) {
        val required = pendingCount + values.size
        if (pendingValues.size < required) {
            pendingValues = pendingValues.copyOf(maxOf(required, pendingValues.size * 2 + 1))
        }
        System.arraycopy(values, 0, pendingValues, pendingCount, values.size)
        pendingCount += values.size
    }

    private fun decodeToInt32Values(buffer: ByteBuffer, encoding: Int): IntArray {
        val src = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val sampleCount = src.remaining() / bytesPerSample(encoding)
        if (sampleCount <= 0) return IntArray(0)

        val values = IntArray(sampleCount)
        var index = 0
        when (encoding) {
            C.ENCODING_PCM_8BIT -> {
                while (src.hasRemaining()) {
                    values[index++] = ((src.get().toInt() and 0xFF) - 128) shl 24
                }
            }

            C.ENCODING_PCM_16BIT -> {
                while (src.remaining() >= 2) {
                    values[index++] = src.short.toInt() shl 16
                }
            }

            C.ENCODING_PCM_24BIT -> {
                while (src.remaining() >= 3) {
                    val b0 = src.get().toInt() and 0xFF
                    val b1 = src.get().toInt() and 0xFF
                    val b2 = src.get().toInt()
                    val sample24 = b0 or (b1 shl 8) or (b2 shl 16)
                    values[index++] = sample24 shl 8
                }
            }

            C.ENCODING_PCM_32BIT -> {
                while (src.remaining() >= 4) {
                    values[index++] = src.int
                }
            }

            C.ENCODING_PCM_FLOAT -> {
                while (src.remaining() >= 4) {
                    val normalized = src.float.coerceIn(-1f, 1f)
                    values[index++] = (normalized * Int.MAX_VALUE).roundToInt()
                }
            }
        }
        return if (index == values.size) values else values.copyOf(index)
    }

    private fun encodeFromInt32Values(values: IntArray, encoding: Int, output: ByteBuffer) {
        output.order(ByteOrder.LITTLE_ENDIAN)
        when (encoding) {
            C.ENCODING_PCM_8BIT -> {
                for (sample in values) {
                    val byteValue = ((sample shr 24) + 128).coerceIn(0, 255)
                    output.put(byteValue.toByte())
                }
            }

            C.ENCODING_PCM_16BIT -> {
                for (sample in values) {
                    val shortValue = (sample shr 16).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    output.putShort(shortValue.toShort())
                }
            }

            C.ENCODING_PCM_24BIT -> {
                for (sample in values) {
                    val value24 = sample shr 8
                    output.put((value24 and 0xFF).toByte())
                    output.put(((value24 shr 8) and 0xFF).toByte())
                    output.put(((value24 shr 16) and 0xFF).toByte())
                }
            }

            C.ENCODING_PCM_32BIT -> {
                for (sample in values) {
                    output.putInt(sample)
                }
            }

            C.ENCODING_PCM_FLOAT -> {
                for (sample in values) {
                    val normalized = (sample.toFloat() / Int.MAX_VALUE).coerceIn(-1f, 1f)
                    output.putFloat(normalized)
                }
            }
        }
    }

    private fun bytesPerSample(encoding: Int): Int {
        return when (encoding) {
            C.ENCODING_PCM_8BIT -> 1
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT -> 4
            C.ENCODING_PCM_FLOAT -> 4
            else -> 0
        }
    }

    private fun isSupportedPcmEncoding(encoding: Int): Boolean {
        return encoding == C.ENCODING_PCM_8BIT ||
            encoding == C.ENCODING_PCM_16BIT ||
            encoding == C.ENCODING_PCM_24BIT ||
            encoding == C.ENCODING_PCM_32BIT ||
            encoding == C.ENCODING_PCM_FLOAT
    }

    companion object {
        private const val TARGET_SAMPLE_RATE_HZ = 48_000
        private const val TARGET_CHANNEL_COUNT = 3
        private const val FRAME_SAMPLES_PER_CHANNEL = 960
    }
}
