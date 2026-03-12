package com.example.videozoomplayer

import android.util.Log
import androidx.media3.common.C
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

class PcmToNativeAudioBufferSink(
    private val nativePcmProcessor: NativePcmProcessor
) : TeeAudioProcessor.AudioBufferSink {

    private var supportedFormat = false
    private var sampleRateHz = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var frameValueCount = 0

    private var pendingValues = IntArray(0)
    private var pendingCount = 0

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.sampleRateHz = sampleRateHz
        this.channelCount = channelCount
        this.encoding = encoding
        supportedFormat = sampleRateHz == TARGET_SAMPLE_RATE_HZ && channelCount == TARGET_CHANNEL_COUNT
        frameValueCount = SAMPLES_PER_20MS * channelCount
        pendingCount = 0
        nativePcmProcessor.reset(sampleRateHz, channelCount)
        if (!supportedFormat) {
            Log.w(
                TAG,
                "Unsupported audio format for native process: ${sampleRateHz}Hz, ${channelCount}ch"
            )
        }
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (!supportedFormat) return
        if (sampleRateHz <= 0 || channelCount <= 0 || frameValueCount <= 0) return

        val inputValues = decodeToInt32Values(buffer, encoding)
        if (inputValues.isEmpty()) return

        appendValues(inputValues)
        processPendingFrames()
    }

    fun release() {
        nativePcmProcessor.release()
    }

    private fun decodeToInt32Values(buffer: ByteBuffer, encoding: Int): IntArray {
        val src = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_8BIT -> 1
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT -> 4
            C.ENCODING_PCM_FLOAT -> 4
            else -> return IntArray(0)
        }

        val sampleCount = src.remaining() / bytesPerSample
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

    private fun appendValues(values: IntArray) {
        val required = pendingCount + values.size
        if (pendingValues.size < required) {
            pendingValues = pendingValues.copyOf(maxOf(required, pendingValues.size * 2 + 1))
        }
        System.arraycopy(values, 0, pendingValues, pendingCount, values.size)
        pendingCount += values.size
    }

    private fun processPendingFrames() {
        while (pendingCount >= frameValueCount) {
            val frame = IntArray(frameValueCount)
            System.arraycopy(pendingValues, 0, frame, 0, frameValueCount)

            nativePcmProcessor.processFrame20ms(frame, sampleRateHz, channelCount)

            val remaining = pendingCount - frameValueCount
            if (remaining > 0) {
                System.arraycopy(pendingValues, frameValueCount, pendingValues, 0, remaining)
            }
            pendingCount = remaining
        }
    }

    companion object {
        private const val TAG = "PcmToNativeSink"
        private const val TARGET_SAMPLE_RATE_HZ = 48_000
        private const val TARGET_CHANNEL_COUNT = 3
        private const val SAMPLES_PER_20MS = 960
    }
}
