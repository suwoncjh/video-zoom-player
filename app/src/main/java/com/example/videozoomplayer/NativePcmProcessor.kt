package com.example.videozoomplayer

import android.util.Log

class NativePcmProcessor {

    private var nativeHandle: Long = 0L
    private var nativeAvailable = false

    init {
        nativeAvailable = try {
            System.loadLibrary(LIB_NAME)
            nativeHandle = nativeCreate()
            nativeHandle != 0L
        } catch (error: UnsatisfiedLinkError) {
            Log.w(TAG, "Native library is unavailable: ${error.message}")
            false
        }
    }

    fun reset(sampleRateHz: Int, channelCount: Int) {
        if (!nativeAvailable || nativeHandle == 0L) return
        nativeReset(nativeHandle, sampleRateHz, channelCount)
    }

    fun processFrame20ms(inputInt32: IntArray, sampleRateHz: Int, channelCount: Int): IntArray? {
        if (!nativeAvailable || nativeHandle == 0L || inputInt32.isEmpty()) return null
        if (sampleRateHz != TARGET_SAMPLE_RATE_HZ || channelCount != TARGET_CHANNEL_COUNT) return null
        if (inputInt32.size != FRAME_SAMPLES_PER_CHANNEL * TARGET_CHANNEL_COUNT) return null
        return nativeProcess20ms(nativeHandle, inputInt32, sampleRateHz, channelCount)
    }

    fun release() {
        if (!nativeAvailable || nativeHandle == 0L) return
        nativeRelease(nativeHandle)
        nativeHandle = 0L
    }

    private external fun nativeCreate(): Long
    private external fun nativeReset(handle: Long, sampleRateHz: Int, channelCount: Int)
    private external fun nativeProcess20ms(
        handle: Long,
        inputInt32: IntArray,
        sampleRateHz: Int,
        channelCount: Int
    ): IntArray

    private external fun nativeRelease(handle: Long)

    companion object {
        private const val TAG = "NativePcmProcessor"
        private const val LIB_NAME = "pcmprocessor_jni"
        private const val TARGET_SAMPLE_RATE_HZ = 48_000
        private const val TARGET_CHANNEL_COUNT = 3
        private const val FRAME_SAMPLES_PER_CHANNEL = 960
    }
}
