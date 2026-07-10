package com.coshelper.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

class OpusCodec private constructor() {
    private val sequence = java.util.concurrent.atomic.AtomicInteger(0)

    external fun nativeEncode(samples: ShortArray, sampleCount: Int, output: ByteArray, maxBytes: Int): Int
    external fun nativeDecode(data: ByteArray, dataLen: Int, output: ShortArray, sampleCount: Int): Int

    /**
     * Encodes a PCM frame into a transport packet.
     * Transport frame layout:
     *   0-1: frameType (short, always 2)
     *   2-3: sequence (short)
     *   4-7: timestamp (int, ms since some epoch)
     *   8-9: dataLen (short, opus payload length)
     *   10+: opus payload
     */
    fun encode(pcm: ShortArray): ByteArray? {
        if (pcm.isEmpty()) return null
        val maxBytes = (pcm.size * 2).coerceAtLeast(160)
        val opusBuffer = ByteArray(maxBytes)
        val encodedLen = nativeEncode(pcm, pcm.size, opusBuffer, maxBytes)
        if (encodedLen <= 0) return null

        val frame = ByteArray(10 + encodedLen)
        val buf = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(FRAME_TYPE.toShort())
        buf.putShort((sequence.incrementAndGet() and 0xFFFF).toShort())
        buf.putInt(System.currentTimeMillis().toInt())
        buf.putShort(encodedLen.toShort())
        System.arraycopy(opusBuffer, 0, frame, 10, encodedLen)
        return frame
    }

    /**
     * Decodes a transport packet back into PCM.
     */
    fun decode(frame: ByteArray): ShortArray? {
        if (frame.size < 10) return null
        val buf = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN)
        val frameType = buf.short.toInt() and 0xFFFF
        if (frameType != FRAME_TYPE) return null
        buf.short // sequence: reserved for protocol, not used for playback
        buf.int   // timestamp: reserved for protocol, not used for playback
        val dataLen = buf.short.toInt() and 0xFFFF
        if (dataLen < 0 || 10 + dataLen > frame.size) return null

        val pcm = ShortArray(FRAME_SIZE_IN_SAMPLES)
        val decoded = nativeDecode(frame, 10 + dataLen, pcm, pcm.size)
        if (decoded <= 0) return null
        return if (decoded == pcm.size) pcm else pcm.copyOfRange(0, decoded)
    }

    companion object {
        const val FRAME_TYPE = 2
        const val FRAME_SIZE_IN_SAMPLES = 320 // 20 ms at 16 kHz

        init {
            System.loadLibrary("opus")
            System.loadLibrary("opus_jni")
        }

        private var instance: OpusCodec? = null
        fun getInstance(): OpusCodec {
            return instance ?: synchronized(this) {
                instance ?: OpusCodec().also { instance = it }
            }
        }
    }
}
