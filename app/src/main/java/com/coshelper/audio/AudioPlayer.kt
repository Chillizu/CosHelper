package com.coshelper.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.coshelper.utils.AppLogger

class AudioPlayer(context: Context) {
    private val appContext = context.applicationContext
    private var audioTrack: AudioTrack? = null
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private var isCommunicationMode = true
    private var preferredOutputDeviceId: Int? = null
    private val lock = Any()

    init {
        rebuildTrack()
    }

    fun setCommunicationMode(isCommunication: Boolean) {
        if (isCommunicationMode == isCommunication) return
        isCommunicationMode = isCommunication
        rebuildTrack()
    }

    fun setPreferredOutputDevice(deviceId: Int?) {
        synchronized(lock) {
            if (preferredOutputDeviceId == deviceId) return
            preferredOutputDeviceId = deviceId
            rebuildTrack()
        }
    }


    private fun rebuildTrack() {
        synchronized(lock) {
            val old = audioTrack
            old?.apply {
                try { stop() } catch (_: IllegalStateException) {}
                release()
            }
            audioTrack = null
            _isPlaying.value = false

            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val usage = if (isCommunicationMode) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA
            val contentType = if (isCommunicationMode) AudioAttributes.CONTENT_TYPE_SPEECH else AudioAttributes.CONTENT_TYPE_MUSIC

            val attributes = AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(contentType)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize.coerceAtLeast(FRAME_SIZE_IN_SAMPLES * 2 * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val deviceId = preferredOutputDeviceId
                    ?: AudioRouter.preferredOutputDeviceId
                deviceId?.let { id ->
                    val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val device = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                        .find { it.id == id }
                    if (device != null) {
                        track.setPreferredDevice(device)
                        AppLogger.d("AudioPlayer", "rebuildTrack resolved output device: [id=${device.id}, type=${AppLogger.deviceTypeName(device.type)}, name=${device.productName}]")
                    } else {
                        AppLogger.d("AudioPlayer", "rebuildTrack preferred output device not found for id=$id")
                    }
                } ?: AppLogger.d("AudioPlayer", "rebuildTrack no preferred output device")
            }

            try {
                track.play()
            } catch (e: IllegalStateException) {
                AppLogger.e("AudioPlayer", "AudioTrack.play() failed", e)
                track.release()
                return
            }

            audioTrack = track
            _isPlaying.value = true
        }
    }

    fun playPcm(buffer: ShortArray) {
        synchronized(lock) {
            val track = audioTrack ?: return
            try {
                track.write(buffer, 0, buffer.size)
            } catch (e: IllegalStateException) {
                AppLogger.e("AudioPlayer", "playPcm failed", e)
            }
        }
    }

    fun stop() {
        AppLogger.d("AudioPlayer", "stop()")
        synchronized(lock) {
            audioTrack?.apply {
                try { stop() } catch (_: IllegalStateException) {}
                release()
            }
            audioTrack = null
            _isPlaying.value = false
        }
    }

    fun cleanup() {
        stop()
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val FRAME_SIZE_IN_SAMPLES = 320 // 20 ms at 16 kHz
    }
}
